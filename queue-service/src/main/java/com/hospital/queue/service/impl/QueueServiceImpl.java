package com.hospital.queue.service.impl;

import com.hospital.queue.client.DoctorServiceClient;
import com.hospital.queue.client.MonitorClient;
import com.hospital.queue.client.NotificationClient;
import com.hospital.queue.dto.*;
import com.hospital.queue.exception.PatientAlreadyInQueueException;
import com.hospital.queue.exception.QueueEntryNotFoundException;
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

    private static final Logger log =
            LogManager.getLogger(QueueServiceImpl.class);

    @Autowired
    private QueueEntryRepository queueRepository;

    @Autowired
    private QueueManager queueManager;

    @Autowired
    private DoctorServiceClient doctorClient;

    @Autowired
    private NotificationClient notificationClient;

    @Autowired
    private MonitorClient monitorClient;

    // ─────────────────────────────────────────
    // LOAD FROM DB ON STARTUP
    // ─────────────────────────────────────────
    @PostConstruct
    public void initializeQueues() {
        log.info("Initializing queues from DB...");
        List<QueueEntry> waiting = queueRepository.findAll()
                .stream()
                .filter(e -> e.getStatus()
                        == QueueStatus.WAITING)
                .collect(Collectors.toList());

        Map<String, List<QueueEntry>> byDept = waiting
                .stream()
                .collect(Collectors.groupingBy(
                        QueueEntry::getDepartmentId));

        byDept.forEach(queueManager::loadQueue);
        log.info("Queues initialized: {}",
                byDept.keySet());
    }

    // ─────────────────────────────────────────
    // ADD TO QUEUE
    // ─────────────────────────────────────────
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueEntryResponse addToQueue(
            AddToQueueRequest request) {

        log.info("Adding: {} to {} queue",
                request.getPatientName(),
                request.getDepartmentId());

        // Check duplicate
        Optional<QueueEntry> existing = queueRepository
                .findByPatientIdAndStatus(
                        request.getPatientId(),
                        QueueStatus.WAITING);

        if (existing.isPresent()) {
            log.warn("Patient already in queue: {}",
                    request.getPatientId());
            throw new PatientAlreadyInQueueException(
                    request.getPatientId());
        }

        // Build entry
        QueueEntry entry = new QueueEntry();
        entry.setPatientId(request.getPatientId());
        entry.setPatientName(request.getPatientName());
        entry.setDepartmentId(request.getDepartmentId());
        entry.setTokenNumber(request.getTokenNumber());
        entry.setStatus(QueueStatus.WAITING);
        entry.setJoinedAt(LocalDateTime.now());
        entry.setPosition(0);

        // Add to in-memory queue (thread-safe)
        int position = queueManager.addToQueue(entry);
        entry.setPosition(position);

        // Save to DB
        QueueEntry saved = queueRepository.save(entry);

        log.info("Patient {} at position {} in {}",
                request.getPatientName(),
                position,
                request.getDepartmentId());

        // Notify patient of their position
        notificationClient.sendPositionUpdate(
                request.getTokenNumber(),
                request.getPatientName(),
                request.getDepartmentId(),
                position,
                queueManager.getQueueSize(
                        request.getDepartmentId())
        );

        // Log to monitor
        monitorClient.logEvent(
                "QUEUE_UPDATED",
                request.getPatientId(),
                request.getDepartmentId(),
                request.getPatientName()
                        + " added to queue at position "
                        + position
        );

        // If position is 1 — try to assign doctor now
        if (position == 1) {
            tryAssignDoctor(saved);
        }

        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────
    // CALL NEXT PATIENT
    // ─────────────────────────────────────────
    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public QueueEntryResponse callNextPatient(
            String departmentId) {

        log.info("Calling next patient for dept: {}",
                departmentId);

        Optional<QueueEntry> nextOpt =
                queueManager.removeNext(departmentId);

        if (nextOpt.isEmpty()) {
            throw new QueueEntryNotFoundException(
                    "No patients waiting in: "
                            + departmentId);
        }

        QueueEntry next = nextOpt.get();

        // Update status in DB
        QueueEntry dbEntry = queueRepository
                .findById(next.getQueueId())
                .orElseThrow(() ->
                        new QueueEntryNotFoundException(
                                next.getQueueId()));

        dbEntry.setStatus(QueueStatus.ASSIGNED);
        dbEntry.setAssignedAt(LocalDateTime.now());
        QueueEntry updated = queueRepository.save(dbEntry);

        // Sync positions to DB
        syncPositionsToDB(departmentId);

        // Notify all remaining patients their position updated
        notifyAllPatientsInDept(departmentId);

        // Try to assign a doctor
        tryAssignDoctor(updated);

        log.info("Next patient called: {} dept: {}",
                next.getPatientName(), departmentId);

        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // GET QUEUE STATUS
    // ─────────────────────────────────────────
    @Override
    public QueueStatusResponse getQueueStatus(
            String departmentId) {

        log.debug("Getting queue status for: {}",
                departmentId);

        List<QueueEntry> snapshot =
                queueManager.getQueueSnapshot(departmentId);

        long totalWaiting = snapshot.stream()
                .filter(e -> e.getStatus()
                        == QueueStatus.WAITING)
                .count();

        long totalAssigned = queueRepository
                .countByDepartmentIdAndStatus(
                        departmentId, QueueStatus.ASSIGNED);

        List<QueueEntryResponse> responses = snapshot
                .stream()
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
    public QueueEntryResponse getPatientQueueInfo(
            String patientId, String departmentId) {

        Optional<Integer> position =
                queueManager.getPatientPosition(
                        patientId, departmentId);

        QueueEntry entry = queueRepository
                .findByPatientId(patientId)
                .stream()
                .filter(e -> e.getStatus()
                        == QueueStatus.WAITING
                        || e.getStatus()
                        == QueueStatus.ASSIGNED)
                .findFirst()
                .orElseThrow(() ->
                        new QueueEntryNotFoundException(
                                patientId));

        position.ifPresent(entry::setPosition);
        return mapToResponse(entry);
    }

    // ─────────────────────────────────────────
    // ASSIGN DOCTOR
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public QueueEntryResponse assignDoctor(
            String patientId,
            AssignDoctorRequest request) {

        log.info("Assigning doctor: {} to patient: {}",
                request.getDoctorName(), patientId);

        QueueEntry entry = queueRepository
                .findByPatientId(patientId)
                .stream()
                .filter(e -> e.getStatus()
                        == QueueStatus.ASSIGNED)
                .findFirst()
                .orElseThrow(() ->
                        new QueueEntryNotFoundException(
                                patientId));

        entry.setAssignedDoctorId(request.getDoctorId());
        entry.setAssignedDoctorName(request.getDoctorName());
        entry.setRoomNumber(request.getRoomNumber());
        entry.setStatus(QueueStatus.IN_CONSULTATION);

        QueueEntry updated = queueRepository.save(entry);

        // Notify patient with doctor and room info
        notificationClient.notifyPatientNext(
                entry.getTokenNumber(),
                request.getRoomNumber(),
                request.getDoctorName(),
                entry.getDepartmentId()
        );

        monitorClient.logEvent(
                "DOCTOR_ASSIGNED",
                patientId,
                entry.getDepartmentId(),
                "Dr. " + request.getDoctorName()
                        + " assigned to patient "
                        + patientId
                        + " in room " + request.getRoomNumber()
        );

        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // MARK DONE
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public QueueEntryResponse markDone(String patientId,
                                       String departmentId) {

        log.info("Marking done: patient: {} dept: {}",
                patientId, departmentId);

        QueueEntry entry = queueRepository
                .findByPatientId(patientId)
                .stream()
                .filter(e -> e.getStatus()
                        == QueueStatus.IN_CONSULTATION)
                .findFirst()
                .orElseThrow(() ->
                        new QueueEntryNotFoundException(
                                patientId));

        // Free the doctor
        if (entry.getAssignedDoctorId() != null) {
            doctorClient.freeDoctor(
                    entry.getAssignedDoctorId());
        }

        entry.setStatus(QueueStatus.DONE);
        QueueEntry updated = queueRepository.save(entry);

        monitorClient.logEvent(
                "CONSULTATION_DONE",
                patientId,
                departmentId,
                "Consultation completed for patient: "
                        + patientId
        );

        log.info("Consultation done for: {}", patientId);
        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // REMOVE FROM QUEUE
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public boolean removeFromQueue(String patientId,
                                   String departmentId) {

        boolean removed = queueManager
                .removePatient(patientId, departmentId);

        if (removed) {
            queueRepository.findByPatientId(patientId)
                    .stream()
                    .filter(e -> e.getStatus()
                            == QueueStatus.WAITING)
                    .findFirst()
                    .ifPresent(e -> {
                        e.setStatus(QueueStatus.DONE);
                        queueRepository.save(e);
                    });

            syncPositionsToDB(departmentId);
            notifyAllPatientsInDept(departmentId);
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
                .filter(e -> e.getStatus()
                        == QueueStatus.WAITING)
                .map(QueueEntry::getDepartmentId)
                .distinct()
                .map(this::getQueueStatus)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────

    // Try to find and assign doctor to patient
    private void tryAssignDoctor(QueueEntry entry) {
        try {
            log.info("Trying to assign doctor for: {}",
                    entry.getPatientId());

            Map doctor = doctorClient
                    .findAndAssignDoctor(
                            entry.getDepartmentId());

            if (doctor != null) {
                String doctorId =
                        (String) doctor.get("doctorId");
                String doctorName =
                        (String) doctor.get("name");
                String roomNumber =
                        (String) doctor.get("roomNumber");

                AssignDoctorRequest req =
                        new AssignDoctorRequest();
                req.setDoctorId(doctorId);
                req.setDoctorName(doctorName);
                req.setRoomNumber(roomNumber);

                assignDoctor(entry.getPatientId(), req);

                log.info("Doctor: {} auto-assigned to: {}",
                        doctorName, entry.getPatientId());
            } else {
                log.warn("No doctor available for dept: {}",
                        entry.getDepartmentId());
            }
        } catch (Exception e) {
            log.warn("Auto doctor assignment failed: {}",
                    e.getMessage());
        }
    }

    // Notify all patients in department of updated positions
    private void notifyAllPatientsInDept(String deptId) {
        List<QueueEntry> snapshot =
                queueManager.getQueueSnapshot(deptId);

        snapshot.forEach(entry -> {
            if (entry.getStatus() == QueueStatus.WAITING) {
                notificationClient.sendPositionUpdate(
                        entry.getTokenNumber(),
                        entry.getPatientName(),
                        deptId,
                        entry.getPosition(),
                        snapshot.size()
                );
            }
        });

        log.debug("All patients notified in dept: {}", deptId);
    }

    // Sync in-memory positions back to DB
    private void syncPositionsToDB(String departmentId) {
        List<QueueEntry> snapshot =
                queueManager.getQueueSnapshot(departmentId);

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

    private QueueEntryResponse mapToResponse(
            QueueEntry entry) {
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
                calculateEstimatedWait(
                        entry.getPosition() != null
                                ? entry.getPosition() : 0)
        );
    }
}