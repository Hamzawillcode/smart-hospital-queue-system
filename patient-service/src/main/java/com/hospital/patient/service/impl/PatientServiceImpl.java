package com.hospital.patient.service.impl;

import com.hospital.patient.client.MonitorClient;
import com.hospital.patient.client.QueueServiceClient;
import com.hospital.patient.dto.CheckInRequest;
import com.hospital.patient.dto.PatientResponse;
import com.hospital.patient.exception.DepartmentNotFoundException;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.model.Department;
import com.hospital.patient.model.Patient;
import com.hospital.patient.model.PatientStatus;
import com.hospital.patient.repository.DepartmentRepository;
import com.hospital.patient.repository.PatientRepository;
import com.hospital.patient.service.PatientService;
import jakarta.persistence.OptimisticLockException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PatientServiceImpl implements PatientService {

    private static final Logger log =
            LogManager.getLogger(PatientServiceImpl.class);

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private QueueServiceClient queueClient;

    @Autowired
    private MonitorClient monitorClient;

    // ─────────────────────────────────────────
    // CHECK IN — THE MAIN FLOW
    // ─────────────────────────────────────────
    @Override
    @Retryable(
            retryFor = {
                    OptimisticLockException.class,
                    ObjectOptimisticLockingFailureException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PatientResponse checkIn(CheckInRequest request) {

        log.info("Check-in request: {} dept: {}",
                request.getName(), request.getDepartment());

        // 1. Validate + lock department
        Department department = departmentRepository
                .findByIdWithLock(request.getDepartment())
                .orElseThrow(() -> {
                    log.warn("Department not found: {}",
                            request.getDepartment());
                    return new DepartmentNotFoundException(
                            request.getDepartment());
                });

        // 2. Build patient
        Patient patient = new Patient();
        patient.setName(request.getName());
        patient.setPhone(request.getPhone());
        patient.setDepartment(request.getDepartment());
        patient.setStatus(PatientStatus.CHECKED_IN);
        patient.setCheckinTime(LocalDateTime.now());

        // 3. Generate token atomically under lock
        int newSize = department.getCurrentQueueSize() + 1;
        String token = request.getDepartment() + "-" + newSize;
        patient.setTokenNumber(token);
        department.setCurrentQueueSize(newSize);

        // 4. Save to DB
        Patient saved = patientRepository.save(patient);
        departmentRepository.save(department);

        log.info("Patient saved. ID: {} Token: {}",
                saved.getId(), token);

        // 5. Call queue-service (after transaction commits)
        // This runs OUTSIDE the transaction — correct
        queueClient.addToQueue(
                saved.getId(),
                saved.getName(),
                saved.getDepartment(),
                saved.getTokenNumber()
        );

        // 6. Log to monitor-service
        monitorClient.logEvent(
                "PATIENT_CHECKED_IN",
                saved.getId(),
                saved.getDepartment(),
                saved.getName() + " checked into "
                        + saved.getDepartment()
                        + " with token " + token
        );

        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────
    // GET BY ID
    // ─────────────────────────────────────────
    @Override
    public PatientResponse getById(String patientId) {
        log.debug("Fetching patient: {}", patientId);
        Patient patient = patientRepository
                .findById(patientId)
                .orElseThrow(() -> {
                    log.warn("Patient not found: {}",
                            patientId);
                    return new PatientNotFoundException(
                            patientId);
                });
        return mapToResponse(patient);
    }

    // ─────────────────────────────────────────
    // GET ALL
    // ─────────────────────────────────────────
    @Override
    public List<PatientResponse> getAll() {
        log.debug("Fetching all patients");
        return patientRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────
    // GET BY DEPARTMENT
    // ─────────────────────────────────────────
    @Override
    public List<PatientResponse> getByDepartment(
            String departmentId) {
        log.debug("Fetching patients for dept: {}",
                departmentId);

        departmentRepository.findById(departmentId)
                .orElseThrow(() ->
                        new DepartmentNotFoundException(
                                departmentId));

        return patientRepository
                .findByDepartment(departmentId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────
    // UPDATE STATUS
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public PatientResponse updateStatus(String patientId,
                                        PatientStatus status) {
        log.info("Updating status: {} → {}",
                patientId, status);

        Patient patient = patientRepository
                .findById(patientId)
                .orElseThrow(() ->
                        new PatientNotFoundException(
                                patientId));

        PatientStatus old = patient.getStatus();
        patient.setStatus(status);
        Patient updated = patientRepository.save(patient);

        log.info("Status updated: {} → {} for: {}",
                old, status, patientId);

        // Log status change to monitor
        monitorClient.logEvent(
                "PATIENT_STATUS_UPDATED",
                patientId,
                patient.getDepartment(),
                "Status changed from " + old + " to " + status
        );

        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // PRIVATE MAPPER
    // ─────────────────────────────────────────
    private PatientResponse mapToResponse(Patient p) {
        return new PatientResponse(
                p.getId(),
                p.getName(),
                p.getDepartment(),
                p.getStatus(),
                p.getTokenNumber(),
                p.getCheckinTime()
        );
    }
}