package com.hospital.doctor.service.impl;

import com.hospital.doctor.dto.DoctorRegisterRequest;
import com.hospital.doctor.dto.DoctorResponse;
import com.hospital.doctor.exception.DoctorNotFoundException;
import com.hospital.doctor.exception.NoDoctorAvailableException;
import com.hospital.doctor.model.Doctor;
import com.hospital.doctor.model.DoctorStatus;
import com.hospital.doctor.repository.DoctorRepository;
import com.hospital.doctor.service.DoctorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DoctorServiceImpl implements DoctorService {

    private static final Logger log =
            LogManager.getLogger(DoctorServiceImpl.class);

    @Autowired
    private DoctorRepository doctorRepository;

    // ─────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public DoctorResponse register(
            DoctorRegisterRequest request) {
        log.info("Registering new doctor: {} in dept: {}",
                request.getName(), request.getDepartmentId());

        Doctor doctor = new Doctor();
        doctor.setName(request.getName());
        doctor.setDepartmentId(request.getDepartmentId());
        doctor.setSpecialization(request.getSpecialization());
        doctor.setRoomNumber(request.getRoomNumber());
        doctor.setStatus(DoctorStatus.AVAILABLE);

        Doctor saved = doctorRepository.save(doctor);
        log.info("Doctor registered successfully: {}",
                saved.getDoctorId());
        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────
    // GET BY ID
    // ─────────────────────────────────────────
    @Override
    public DoctorResponse getById(String doctorId) {
        log.debug("Fetching doctor by ID: {}", doctorId);
        Doctor doctor = doctorRepository
                .findById(doctorId)
                .orElseThrow(() -> {
                    log.warn("Doctor not found: {}", doctorId);
                    return new DoctorNotFoundException(doctorId);
                });
        return mapToResponse(doctor);
    }

    // ─────────────────────────────────────────
    // GET ALL
    // ─────────────────────────────────────────
    @Override
    public List<DoctorResponse> getAll() {
        log.debug("Fetching all doctors");
        List<Doctor> doctors = doctorRepository.findAll();
        log.info("Total doctors fetched: {}", doctors.size());
        return doctors.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────
    // GET BY DEPARTMENT
    // ─────────────────────────────────────────
    @Override
    public List<DoctorResponse> getByDepartment(String deptId) {
        log.debug("Fetching doctors for dept: {}", deptId);
        List<Doctor> doctors = doctorRepository
                .findByDepartmentId(deptId);
        log.info("Found {} doctors in dept: {}",
                doctors.size(), deptId);
        return doctors.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────
    // GET AVAILABLE BY DEPARTMENT
    // ─────────────────────────────────────────
    @Override
    public List<DoctorResponse> getAvailableByDepartment(
            String deptId) {
        log.debug("Fetching available doctors in dept: {}",
                deptId);
        List<Doctor> doctors = doctorRepository
                .findByDepartmentIdAndStatus(
                        deptId, DoctorStatus.AVAILABLE);
        log.info("Found {} available doctors in dept: {}",
                doctors.size(), deptId);
        return doctors.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────
    // FIND FIRST AVAILABLE
    // ─────────────────────────────────────────
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DoctorResponse findFirstAvailable(String deptId) {

        log.info("Finding first available doctor in: {}",
                deptId);

        // Lock the available doctor row
        // No other thread can assign this same doctor
        // until this transaction commits
        Doctor doctor = doctorRepository
                .findFirstAvailableWithLock(deptId)
                .orElseThrow(() -> {
                    log.warn("No available doctor in: {}",
                            deptId);
                    return new NoDoctorAvailableException(deptId);
                });

        // Immediately mark as BUSY within same transaction
        // Lock held until commit — no race condition
        doctor.setStatus(DoctorStatus.BUSY);
        Doctor updated = doctorRepository.save(doctor);

        log.info("Doctor: {} assigned atomically in dept: {}",
                updated.getName(), deptId);

        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // UPDATE STATUS
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public DoctorResponse updateStatus(String doctorId,
                                       DoctorStatus newStatus) {
        log.info("Updating status for doctor: {} to: {}",
                doctorId, newStatus);
        Doctor doctor = doctorRepository
                .findById(doctorId)
                .orElseThrow(() -> {
                    log.warn("Doctor not found: {}", doctorId);
                    return new DoctorNotFoundException(doctorId);
                });

        DoctorStatus oldStatus = doctor.getStatus();
        doctor.setStatus(newStatus);
        Doctor updated = doctorRepository.save(doctor);
        log.info("Doctor status updated: {} → {} for ID: {}",
                oldStatus, newStatus, doctorId);
        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // ASSIGN TO PATIENT
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public DoctorResponse assignToPatient(String doctorId) {
        log.info("Assigning doctor: {} to patient", doctorId);
        Doctor doctor = doctorRepository
                .findById(doctorId)
                .orElseThrow(() ->
                        new DoctorNotFoundException(doctorId));
        doctor.setStatus(DoctorStatus.BUSY);
        Doctor updated = doctorRepository.save(doctor);
        log.info("Doctor: {} is now BUSY", doctor.getName());
        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // FREE DOCTOR (After consultation done)
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public DoctorResponse freeDoctor(String doctorId) {
        log.info("Freeing doctor: {}", doctorId);
        Doctor doctor = doctorRepository
                .findById(doctorId)
                .orElseThrow(() ->
                        new DoctorNotFoundException(doctorId));
        doctor.setStatus(DoctorStatus.AVAILABLE);
        Doctor updated = doctorRepository.save(doctor);
        log.info("Doctor: {} is now AVAILABLE",
                doctor.getName());
        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────
    // PRIVATE MAPPER
    // ─────────────────────────────────────────
    private DoctorResponse mapToResponse(Doctor doctor) {
        return new DoctorResponse(
                doctor.getDoctorId(),
                doctor.getName(),
                doctor.getDepartmentId(),
                doctor.getSpecialization(),
                doctor.getRoomNumber(),
                doctor.getStatus()
        );
    }
}
