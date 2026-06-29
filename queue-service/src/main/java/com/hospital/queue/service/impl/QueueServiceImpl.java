package com.hospital.queue.service.impl;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

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

    // ─────────────────────────────────────────
    // LOAD QUEUE FROM DB ON STARTUP
    // ─────────────────────────────────────────
    @PostConstruct
    public void initializeQueues() {
        log.info("Initializing in-memory queues from DB...");

        // Load all WAITING entries from DB into memory
        List<QueueEntry> waitingEntries = queueRepository
                .findAll()
                .stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING)
                .collect(Collectors.toList());

        // Group by department and load into QueueManager
        Map<String, List<QueueEntry>> byDept = waitingEntries
                .stream()
                .collect(Collectors.groupingBy(
                        QueueEntry::getDepartmentId));

        byDept.forEach(queueManager::loadQueue);

        log.info("Queues initialized. Departments loaded: {}",
                byDept.keySet());
    }

    // ─────────────────────────────────────────
    // ADD TO QUEUE
    // ─────────────────────────────────────────
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueEntryResponse addToQueue(
            AddToQueueRequest request) {

        log.info("Adding patient: {} to dept: {} queue",
                request.getPatientName(),
                request.getDepartmentId());

        // Check if patient already in queue
        Optional<QueueEntry> existing = queueRepository
                .findByPatientIdAndStatus(
                        request.getPatientId(),
                        QueueStatus.WAITING);

        if (existing.isPresent()) {
            log.warn("Patient: {} already in queue",
                    request.getPatientId());
            throw new PatientAlreadyInQueueException(
                    request.getPatientId());
        }

        // Build queue entry
        QueueEntry entry = new QueueEntry();
        entry.setPatientId(request.getPatientId());
        entry.setPatientName(request.getPatientName());
        entry.setDepartmentId(request.getDepartmentId());
        entry.setTokenNumber(request.getTokenNumber());
        entry.setStatus(QueueStatus.WAITING);
        entry.setJoinedAt(LocalDateTime.now());
        entry.setPosition(0); // temp — set by QueueManager

        // Add to in-memory queue (thread-safe)
        int position = queueManager.addToQueue(entry);
        entry.setPosition(position);

        // Save to DB
        QueueEntry saved = queueRepository.save(entry);

        log.info("Patient: {} added at position: {}",
                request.getPatientName(), position);

        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────
    // GET QUEUE STATUS FOR A DEPARTMENT
    // ─────────────────────────────────────────
    @Override
    public QueueStatusResponse getQueueStatus(
            String departmentId) {

        log.debug("Getting queue status for dept: {}",
                departmentId);

        List<QueueEntry> snapshot = queueManager
                .getQueueSnapshot(departmentId);

        long totalWaiting = snapshot.stream()
                .filter(e -> e.getStatus()
                        == QueueStatus.WAITING)
                .count();

        long totalAssigned = queueRepository
                .countByDepartmentIdAndStatus(
                        departmentId, QueueStatus.ASSIGNED);

        List<QueueEntryResponse> queueResponses = snapshot
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return new QueueStatusResponse(
                departmentId,
                (int) totalWaiting,
                (int) totalAssigned,
                queueResponses);
    }

    // ─────────────────────────────────────────
    // GET PATIENT'S QUEUE INFO
    // ─────────────────────────────────────────
    @Override
    public QueueEntryResponse getPatientQueueInfo(
            String patientId, String departmentId) {

        log.debug("Getting queue info for patient: {}",
                patientId);

        // Check in-memory queue first
        Optional<Integer> position = queueManager
                .getPatientPosition(patientId, departmentId);

        // Find from DB
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

        // Update position from in-memory state
        position.ifPresent(entry::setPosition);

        return mapToResponse(entry);
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

        // Get next from in-memory queue
        Optional<QueueEntry> nextOpt = queueManager
                .removeNext(departmentId);

        if (nextOpt.isEmpty()) {
            throw new QueueEntryNotFoundException(
                    "No patients waiting in: " + departmentId);
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

        // Update positions in DB for remaining patients
        syncPositionsToDB(departmentId);

        log.info("Next patient called: {} for dept: {}",
                next.getPatientName(), departmentId);

        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // ASSIGN DOCTOR TO PATIENT
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

        log.info("Doctor: {} assigned to patient: {}. " +
                        "Room: {}",
                request.getDoctorName(), patientId,
                request.getRoomNumber());

        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // MARK CONSULTATION DONE
    // ─────────────────────────────────────────
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueEntryResponse markDone(String patientId,
                                       String departmentId) {

        log.info("Marking done for patient: {} in dept: {}",
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

        entry.setStatus(QueueStatus.DONE);
        QueueEntry updated = queueRepository.save(entry);

        log.info("Consultation done for patient: {}",
                patientId);

        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // REMOVE FROM QUEUE (patient leaves)
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public boolean removeFromQueue(String patientId,
                                   String departmentId) {

        log.info("Removing patient: {} from dept: {} queue",
                patientId, departmentId);

        // Remove from in-memory
        boolean removed = queueManager
                .removePatient(patientId, departmentId);

        if (removed) {
            // Update DB
            queueRepository.findByPatientId(patientId)
                    .stream()
                    .filter(e -> e.getStatus()
                            == QueueStatus.WAITING)
                    .findFirst()
                    .ifPresent(e -> {
                        e.setStatus(QueueStatus.DONE);
                        queueRepository.save(e);
                    });

            // Sync positions to DB
            syncPositionsToDB(departmentId);
        }

        return removed;
    }

    // ─────────────────────────────────────────
    // GET ALL QUEUES
    // ─────────────────────────────────────────
    @Override
    public List<QueueStatusResponse> getAllQueues() {
        log.debug("Getting all queue statuses");

        // Get distinct departments from DB
        List<String> departments = queueRepository
                .findAll()
                .stream()
                .filter(e -> e.getStatus()
                        == QueueStatus.WAITING)
                .map(QueueEntry::getDepartmentId)
                .distinct()
                .collect(Collectors.toList());

        return departments.stream()
                .map(this::getQueueStatus)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────

    // Sync in-memory positions back to DB
    private void syncPositionsToDB(String departmentId) {
        List<QueueEntry> snapshot = queueManager
                .getQueueSnapshot(departmentId);

        snapshot.forEach(entry -> {
            queueRepository.findById(entry.getQueueId())
                    .ifPresent(dbEntry -> {
                        dbEntry.setPosition(entry.getPosition());
                        queueRepository.save(dbEntry);
                    });
        });

        log.debug("Positions synced to DB for dept: {}",
                departmentId);
    }

    // Calculate estimated wait time (5 mins per patient)
    private int calculateEstimatedWait(int position) {
        return (position - 1) * 5;
    }

    // Map entity to response DTO
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
