package com.hospital.queue.service.impl;

import com.hospital.common.events.QueueUpdatedEvent;
import com.hospital.queue.client.DoctorServiceClient;
import com.hospital.queue.client.MonitorClient;
import com.hospital.queue.dto.*;
import com.hospital.queue.exception.PatientAlreadyInQueueException;
import com.hospital.queue.exception.QueueEntryNotFoundException;
import com.hospital.queue.kafka.QueueEventProducer;
import com.hospital.queue.manager.QueueManager;
import com.hospital.queue.model.QueueEntry;
import com.hospital.queue.model.QueueStatus;
import com.hospital.queue.repository.QueueEntryRepository;
import com.hospital.queue.service.QueueService;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QueueServiceImpl implements QueueService {

    private static final Logger log = LogManager.getLogger(QueueServiceImpl.class);

    @Autowired
    private QueueEntryRepository queueRepository;

    @Autowired
    private QueueManager queueManager;

    @Autowired
    private DoctorServiceClient doctorClient;

    @Autowired
    private MonitorClient monitorClient;

    @Autowired
    private QueueEventProducer queueEventProducer;

    // ─────────────────────────────────────────
    // LOAD FROM DB ON STARTUP
    // ─────────────────────────────────────────
    @PostConstruct
    public void initializeQueues() {
        log.info("Initializing queues from DB...");
        List<QueueEntry> waiting = queueRepository.findAll()
                .stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING)
                .collect(Collectors.toList());

        Map<String, List<QueueEntry>> byDept = waiting
                .stream()
                .collect(Collectors.groupingBy(QueueEntry::getDepartmentId));

        byDept.forEach(queueManager::loadQueue);
        log.info("Queues initialized: {}", byDept.keySet());
    }

    // ─────────────────────────────────────────
    // ADD TO QUEUE
    // ─────────────────────────────────────────
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueEntryResponse addToQueue(AddToQueueRequest request) {

        log.info("Adding: {} to {} queue",
                request.getPatientName(),
                request.getDepartmentId());

        // 1. Check duplicate
        Optional<QueueEntry> existing = queueRepository
                .findByPatientIdAndStatus(
                        request.getPatientId(),
                        QueueStatus.WAITING);

        if (existing.isPresent()) {
            log.warn("Patient already in queue: {}", request.getPatientId());
            throw new PatientAlreadyInQueueException(request.getPatientId());
        }

        // 2. Build entry
        QueueEntry entry = new QueueEntry();
        entry.setPatientId(request.getPatientId());
        entry.setPatientName(request.getPatientName());
        entry.setDepartmentId(request.getDepartmentId());
        entry.setTokenNumber(request.getTokenNumber());
        entry.setStatus(QueueStatus.WAITING);
        entry.setJoinedAt(LocalDateTime.now());
        entry.setPosition(0);

        // 3. Add to in-memory queue (thread-safe)
        int position = queueManager.addToQueue(entry);
        entry.setPosition(position);

        // 4. Save to DB
        QueueEntry saved = queueRepository.save(entry);

        log.info("Patient {} at position {} in {}",
                request.getPatientName(),
                position,
                request.getDepartmentId());

        // 5. Build complete queue snapshot for Kafka event
        List<QueueUpdatedEvent.QueueEntrySnapshot> snapshots =
                queueManager.getQueueSnapshot(request.getDepartmentId())
                        .stream()
                        .map(e -> new QueueUpdatedEvent.QueueEntrySnapshot(
                                e.getPatientId(),
                                e.getPatientName(),
                                e.getTokenNumber(),
                                e.getPosition() != null ? e.getPosition() : 0,
                                e.getPosition() != null ? (e.getPosition() - 1) * 5 : 0
                        ))
                        .collect(Collectors.toList());

        // First patient in queue
        String firstId = snapshots.isEmpty() ? null : snapshots.get(0).getPatientId();
        String firstToken = snapshots.isEmpty() ? null : snapshots.get(0).getTokenNumber();

        // 6. Publish to Kafka — notification-service and monitor-service consume this
        queueEventProducer.publishQueueUpdated(
                request.getDepartmentId(),
                snapshots.size(),
                firstId,
                firstToken,
                snapshots
        );

        log.info("queue.updated published: dept={} patients={}",
                request.getDepartmentId(), snapshots.size());

        // 7. If this patient is at position 1 — publish event to assign doctor
        if (position == 1) {
            tryAssignDoctorViaKafka(saved);
        }

        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────
    // CALL NEXT PATIENT
    // ─────────────────────────────────────────
    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public QueueEntryResponse callNextPatient(String departmentId) {

        log.info("Calling next patient for dept: {}", departmentId);

        Optional<QueueEntry> nextOpt = queueManager.removeNext(departmentId);

        if (nextOpt.isEmpty()) {
            throw new QueueEntryNotFoundException("No patients waiting in: " + departmentId);
        }

        QueueEntry next = nextOpt.get();

        // Update status in DB
        QueueEntry dbEntry = queueRepository.findById(next.getQueueId())
                .orElseThrow(() -> new QueueEntryNotFoundException(next.getQueueId()));

        dbEntry.setStatus(QueueStatus.ASSIGNED);
        dbEntry.setAssignedAt(LocalDateTime.now());
        QueueEntry updated = queueRepository.save(dbEntry);

        // Sync positions to DB
        syncPositionsToDB(departmentId);

        // Rebuild snapshot after removal
        List<QueueUpdatedEvent.QueueEntrySnapshot> snapshots =
                queueManager.getQueueSnapshot(departmentId)
                        .stream()
                        .map(e -> new QueueUpdatedEvent.QueueEntrySnapshot(
                                e.getPatientId(),
                                e.getPatientName(),
                                e.getTokenNumber(),
                                e.getPosition() != null ? e.getPosition() : 0,
                                e.getPosition() != null ? (e.getPosition() - 1) * 5 : 0
                        ))
                        .collect(Collectors.toList());

        String firstId = snapshots.isEmpty() ? null : snapshots.get(0).getPatientId();
        String firstToken = snapshots.isEmpty() ? null : snapshots.get(0).getTokenNumber();

        // Publish updated queue state
        queueEventProducer.publishQueueUpdated(
                departmentId,
                snapshots.size(),
                firstId,
                firstToken,
                snapshots
        );

        log.info("queue.updated published after callNext: dept={} remaining={}",
                departmentId, snapshots.size());

        // Try to assign a doctor
        tryAssignDoctorViaKafka(updated);

        log.info("Next patient called: {} dept: {}", next.getPatientName(), departmentId);

        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // GET QUEUE STATUS
    // ─────────────────────────────────────────
    @Override
    public QueueStatusResponse getQueueStatus(String departmentId) {

        log.debug("Getting queue status for: {}", departmentId);

        List<QueueEntry> snapshot = queueManager.getQueueSnapshot(departmentId);

        long totalWaiting = snapshot.stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING)
                .count();

        long totalAssigned = queueRepository
                .countByDepartmentIdAndStatus(departmentId, QueueStatus.ASSIGNED);

        List<QueueEntryResponse> responses = snapshot.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return new QueueStatusResponse(
                departmentId,
                (int) totalWaiting,
                (int) totalAssigned,
                responses);
    }

    // ─────────────────────────────────────────
    // GET PATIENT QUEUE INFO
    // ─────────────────────────────────────────
    @Override
    public QueueEntryResponse getPatientQueueInfo(String patientId, String departmentId) {

        Optional<Integer> position = queueManager.getPatientPosition(patientId, departmentId);

        QueueEntry entry = queueRepository.findByPatientId(patientId)
                .stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING || e.getStatus() == QueueStatus.ASSIGNED)
                .findFirst()
                .orElseThrow(() -> new QueueEntryNotFoundException(patientId));

        position.ifPresent(entry::setPosition);
        return mapToResponse(entry);
    }

    // ─────────────────────────────────────────
    // ASSIGN DOCTOR
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public QueueEntryResponse assignDoctor(String patientId, AssignDoctorRequest request) {

        log.info("Assigning doctor: {} to patient: {}", request.getDoctorName(), patientId);

        QueueEntry entry = queueRepository.findByPatientId(patientId)
                .stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING || e.getStatus() == QueueStatus.ASSIGNED)
                .findFirst()
                .orElseThrow(() -> new QueueEntryNotFoundException(patientId));

        entry.setAssignedDoctorId(request.getDoctorId());
        entry.setAssignedDoctorName(request.getDoctorName());
        entry.setRoomNumber(request.getRoomNumber());
        entry.setStatus(QueueStatus.IN_CONSULTATION);

        QueueEntry updated = queueRepository.save(entry);

        // Publish doctor.assigned event to Kafka
        queueEventProducer.publishDoctorAssigned(
                updated.getPatientId(),
                updated.getTokenNumber(),
                request.getDoctorId(),
                request.getDoctorName(),
                updated.getDepartmentId(),
                request.getRoomNumber()
        );

        log.info("doctor.assigned event published for patient: {} with Dr. {}",
                patientId, request.getDoctorName());

        return mapToResponse(updated);
    }

    // Private helper for automated assignment
    private void tryAssignDoctorViaKafka(QueueEntry entry) {
        try {
            log.info("Finding doctor for patient: {}", entry.getPatientId());

            Map doctor = doctorClient.findAndAssignDoctor(entry.getDepartmentId());

            if (doctor != null) {
                String doctorId = doctor.get("doctorId") != null
                        ? doctor.get("doctorId").toString() : null;
                String doctorName = doctor.get("name") != null
                        ? doctor.get("name").toString() : "Unknown";
                String roomNumber = doctor.get("roomNumber") != null
                        ? doctor.get("roomNumber").toString() : "TBD";

                if (doctorId == null) return;

                AssignDoctorRequest req = new AssignDoctorRequest();
                req.setDoctorId(doctorId);
                req.setDoctorName(doctorName);
                req.setRoomNumber(roomNumber);

                assignDoctor(entry.getPatientId(), req);

                log.info("Doctor {} auto-assigned via Kafka event to: {}",
                        doctorName, entry.getPatientId());
            } else {
                log.warn("No doctor available for dept: {}", entry.getDepartmentId());
            }
        } catch (Exception e) {
            log.warn("Auto doctor assignment failed: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // MARK DONE
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public QueueEntryResponse markDone(String patientId, String departmentId) {

        log.info("Marking done: patient: {} dept: {}", patientId, departmentId);

        QueueEntry entry = queueRepository.findByPatientId(patientId)
                .stream()
                .filter(e -> e.getStatus() == QueueStatus.IN_CONSULTATION)
                .findFirst()
                .orElseThrow(() -> new QueueEntryNotFoundException(patientId));

        if (entry.getAssignedDoctorId() != null) {
            doctorClient.freeDoctor(entry.getAssignedDoctorId());
        }

        entry.setStatus(QueueStatus.DONE);
        QueueEntry updated = queueRepository.save(entry);

        monitorClient.logEvent(
                "CONSULTATION_DONE",
                patientId,
                departmentId,
                "Consultation completed for patient: " + patientId
        );

        log.info("Consultation done for: {}", patientId);
        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // REMOVE FROM QUEUE
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public boolean removeFromQueue(String patientId, String departmentId) {

        boolean removed = queueManager.removePatient(patientId, departmentId);

        if (removed) {
            queueRepository.findByPatientId(patientId)
                    .stream()
                    .filter(e -> e.getStatus() == QueueStatus.WAITING)
                    .findFirst()
                    .ifPresent(e -> {
                        e.setStatus(QueueStatus.DONE);
                        queueRepository.save(e);
                    });

            syncPositionsToDB(departmentId);

            // Rebuild snapshot and publish to Kafka after removal
            List<QueueUpdatedEvent.QueueEntrySnapshot> snapshots =
                    queueManager.getQueueSnapshot(departmentId)
                            .stream()
                            .map(e -> new QueueUpdatedEvent.QueueEntrySnapshot(
                                    e.getPatientId(),
                                    e.getPatientName(),
                                    e.getTokenNumber(),
                                    e.getPosition() != null ? e.getPosition() : 0,
                                    e.getPosition() != null ? (e.getPosition() - 1) * 5 : 0
                            ))
                            .collect(Collectors.toList());

            String firstId = snapshots.isEmpty() ? null : snapshots.get(0).getPatientId();
            String firstToken = snapshots.isEmpty() ? null : snapshots.get(0).getTokenNumber();

            queueEventProducer.publishQueueUpdated(
                    departmentId,
                    snapshots.size(),
                    firstId,
                    firstToken,
                    snapshots
            );

            log.info("queue.updated published after remove: dept={} remaining={}",
                    departmentId, snapshots.size());
        }

        return removed;
    }

    // ─────────────────────────────────────────
    // GET ALL QUEUES
    // ─────────────────────────────────────────
    @Override
    public List<QueueStatusResponse> getAllQueues() {
        return queueRepository.findAll()
                .stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING)
                .map(QueueEntry::getDepartmentId)
                .distinct()
                .map(this::getQueueStatus)
                .collect(Collectors.toList());
    }

    // Sync in-memory positions back to DB
    private void syncPositionsToDB(String departmentId) {
        List<QueueEntry> snapshot = queueManager.getQueueSnapshot(departmentId);

        snapshot.forEach(entry ->
                queueRepository.findById(entry.getQueueId())
                        .ifPresent(db -> {
                            db.setPosition(entry.getPosition());
                            queueRepository.save(db);
                        }));
    }

    private int calculateEstimatedWait(int position) {
        return (position - 1) * 5;
    }

    private QueueEntryResponse mapToResponse(QueueEntry entry) {
        return new QueueEntryResponse(
                entry.getQueueId(),
                entry.getPatientId(),
                entry.getPatientName(),
                entry.getDepartmentId(),
                entry.getTokenNumber(),
                entry.getPosition(),
                entry.getStatus(),
                entry.getAssignedDoctorName(),
                entry.getRoomNumber(),
                entry.getJoinedAt(),
                calculateEstimatedWait(entry.getPosition() != null ? entry.getPosition() : 0)
        );
    }
}