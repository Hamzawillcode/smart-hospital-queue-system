package com.hospital.patient.service.impl;

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

    // Logger instance — one per class
    private static final Logger log =
            LogManager.getLogger(PatientServiceImpl.class);

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

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

        log.info("Check-in request received for: {} dept: {}",
                request.getName(), request.getDepartment());

        // PESSIMISTIC LOCK on department row
        // Thread 1 locks OPD → Thread 2 waits
        // Thread 1 commits → Thread 2 proceeds with updated count
        Department department = departmentRepository
                .findByIdWithLock(request.getDepartment())
                .orElseThrow(() -> {
                    log.warn("Department not found: {}",
                            request.getDepartment());
                    return new DepartmentNotFoundException(
                            request.getDepartment());
                });

        log.debug("Department locked: {}", department.getName());

        // Build patient
        Patient patient = new Patient();
        patient.setName(request.getName());
        patient.setPhone(request.getPhone());
        patient.setDepartment(request.getDepartment());
        patient.setStatus(PatientStatus.CHECKED_IN);
        patient.setCheckinTime(LocalDateTime.now());

        // Token generation is now ATOMIC:
        // currentQueueSize is incremented under the lock
        // No two threads can get the same count
        int newSize = department.getCurrentQueueSize() + 1;
        String token = request.getDepartment() + "-" + newSize;
        patient.setTokenNumber(token);
        department.setCurrentQueueSize(newSize);

        log.debug("Token generated: {} under pessimistic lock",
                token);

        // Save patient and department in same transaction
        Patient saved = patientRepository.save(patient);
        departmentRepository.save(department);

        // Lock released here when @Transactional commits
        log.info("Check-in complete. Token: {} Patient: {}",
                token, saved.getId());

        return mapToResponse(saved);
    }

    @Override
    public PatientResponse getById(String patientId) {
        log.debug("Fetching patient by ID: {}", patientId);

        Patient patient = patientRepository
                .findById(patientId)
                .orElseThrow(() -> {
                    log.warn("Patient not found with ID: {}",
                            patientId);
                    return new PatientNotFoundException(patientId);
                });

        log.debug("Patient found: {}", patient.getName());
        return mapToResponse(patient);
    }

    @Override
    public List<PatientResponse> getAll() {
        log.debug("Fetching all patients");
        List<Patient> patients = patientRepository.findAll();
        log.info("Total patients fetched: {}", patients.size());
        return patients.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PatientResponse> getByDepartment(
            String departmentId) {
        log.debug("Fetching patients for department: {}",
                departmentId);

        departmentRepository
                .findById(departmentId)
                .orElseThrow(() -> {
                    log.warn("Department not found: {}",
                            departmentId);
                    return new DepartmentNotFoundException(
                            departmentId);
                });

        List<Patient> patients = patientRepository
                .findByDepartment(departmentId);

        log.info("Found {} patients in department: {}",
                patients.size(), departmentId);

        return patients.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PatientResponse updateStatus(String patientId,
                                        PatientStatus newStatus) {
        log.info("Updating status for patient: {} to: {}",
                patientId, newStatus);

        Patient patient = patientRepository
                .findById(patientId)
                .orElseThrow(() -> {
                    log.warn("Patient not found: {}", patientId);
                    return new PatientNotFoundException(patientId);
                });

        PatientStatus oldStatus = patient.getStatus();
        patient.setStatus(newStatus);
        Patient updated = patientRepository.save(patient);

        log.info("Patient status updated: {} → {} for ID: {}",
                oldStatus, newStatus, patientId);

        return mapToResponse(updated);
    }

    private PatientResponse mapToResponse(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getName(),
                patient.getDepartment(),
                patient.getStatus(),
                patient.getTokenNumber(),
                patient.getCheckinTime()
        );
    }
}