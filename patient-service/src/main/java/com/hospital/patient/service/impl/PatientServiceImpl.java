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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
    @Transactional
    public PatientResponse checkIn(CheckInRequest request) {
        log.info("Check-in request received for patient: {} " +
                        "in department: {}",
                request.getName(), request.getDepartment());

        // Validate department
        Department department = departmentRepository
                .findById(request.getDepartment())
                .orElseThrow(() -> {
                    log.warn("Department not found: {}",
                            request.getDepartment());
                    return new DepartmentNotFoundException(
                            request.getDepartment());
                });

        log.debug("Department validated: {}",
                department.getName());

        // Build patient
        Patient patient = new Patient();
        patient.setName(request.getName());
        patient.setPhone(request.getPhone());
        patient.setDepartment(request.getDepartment());
        patient.setStatus(PatientStatus.CHECKED_IN);
        patient.setCheckinTime(LocalDateTime.now());

        // TODO: Race condition here — fix Day 12
        // TODO: Token resets daily — fix Day 12
        // TODO: Token generation moves to queue-service Day 20
        long count = patientRepository.countByDepartmentAndStatus(
                request.getDepartment(), PatientStatus.CHECKED_IN);
        String token = request.getDepartment() + "-" + (count + 1);
        patient.setTokenNumber(token);

        log.debug("Generated token: {} for patient: {}",
                token, request.getName());

        // Save patient
        Patient saved = patientRepository.save(patient);

        // Update department queue size
        department.setCurrentQueueSize(
                department.getCurrentQueueSize() + 1);
        departmentRepository.save(department);

        log.info("Patient checked in successfully. " +
                        "ID: {}, Token: {}, Department: {}",
                saved.getId(),
                saved.getTokenNumber(),
                saved.getDepartment());

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