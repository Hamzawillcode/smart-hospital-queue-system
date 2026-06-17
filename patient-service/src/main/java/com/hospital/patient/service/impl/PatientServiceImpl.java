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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PatientServiceImpl implements PatientService {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    // ─────────────────────────────────────────
    // CHECK IN
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public PatientResponse checkIn(CheckInRequest request) {

        // 1. Validate department exists
        Department department = departmentRepository
                .findById(request.getDepartment())
                .orElseThrow(() -> new DepartmentNotFoundException(
                        request.getDepartment()));

        // 2. Build Patient object
        Patient patient = new Patient();
        patient.setName(request.getName());
        patient.setPhone(request.getPhone());
        patient.setDepartment(request.getDepartment());
        patient.setStatus(PatientStatus.CHECKED_IN);
        patient.setCheckinTime(LocalDateTime.now());

        // TODO: Race condition here — fix on Day 12
        // Token generation moves to queue-service on Day 20
        long count = patientRepository.countByDepartmentAndStatus(
                request.getDepartment(), PatientStatus.CHECKED_IN);
        patient.setTokenNumber(request.getDepartment()
                + "-" + (count + 1));

        // 3. Save patient
        Patient saved = patientRepository.save(patient);

        // 4. Update department queue size
        department.setCurrentQueueSize(
                department.getCurrentQueueSize() + 1);
        departmentRepository.save(department);

        // 5. Return DTO
        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────
    // GET BY ID
    // ─────────────────────────────────────────
    @Override
    public PatientResponse getById(String patientId) {
        Patient patient = patientRepository
                .findById(patientId)
                .orElseThrow(() ->
                        new PatientNotFoundException(patientId));
        return mapToResponse(patient);
    }

    // ─────────────────────────────────────────
    // GET ALL
    // ─────────────────────────────────────────
    @Override
    public List<PatientResponse> getAll() {
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

        // Validate department exists first
        departmentRepository
                .findById(departmentId)
                .orElseThrow(() ->
                        new DepartmentNotFoundException(departmentId));

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
                                        PatientStatus newStatus) {
        Patient patient = patientRepository
                .findById(patientId)
                .orElseThrow(() ->
                        new PatientNotFoundException(patientId));

        patient.setStatus(newStatus);
        Patient updated = patientRepository.save(patient);
        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // PRIVATE MAPPER
    // ─────────────────────────────────────────
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
