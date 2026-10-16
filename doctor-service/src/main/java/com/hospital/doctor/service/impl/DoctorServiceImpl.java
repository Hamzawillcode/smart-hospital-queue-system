package com.hospital.doctor.service.impl;

import com.hospital.doctor.dto.DoctorRegisterRequest;
import com.hospital.doctor.dto.DoctorRequestResponse;
import com.hospital.doctor.dto.DoctorResponse;
import com.hospital.doctor.exception.DoctorNotFoundException;
import com.hospital.doctor.model.Doctor;
import com.hospital.doctor.model.DoctorRequest;
import com.hospital.doctor.model.DoctorStatus;
import com.hospital.doctor.repository.DoctorRepository;
import com.hospital.doctor.repository.DoctorRequestRepository;
import com.hospital.doctor.service.AssignmentService;
import com.hospital.doctor.service.DoctorService;
import com.hospital.doctor.service.DoctorStatusUpdater;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DoctorServiceImpl implements DoctorService {

    private static final Logger log = LogManager.getLogger(DoctorServiceImpl.class);

    private final DoctorRepository doctors;
    private final DoctorRequestRepository requests;
    private final AssignmentService assignmentService;
    private final DoctorStatusUpdater statusUpdater;

    public DoctorServiceImpl(DoctorRepository doctors, DoctorRequestRepository requests,
                             AssignmentService assignmentService, DoctorStatusUpdater statusUpdater) {
        this.doctors = doctors;
        this.requests = requests;
        this.assignmentService = assignmentService;
        this.statusUpdater = statusUpdater;
    }

    @Override
    @Transactional
    public DoctorResponse register(DoctorRegisterRequest request) {
        Doctor doctor = new Doctor();
        doctor.setDoctorId(UUID.randomUUID().toString());
        doctor.setName(request.name());
        doctor.setDepartmentId(request.departmentId());
        doctor.setSpecialization(request.specialization());
        doctor.setRoomNumber(request.roomNumber());
        doctor.setStatus(DoctorStatus.AVAILABLE);
        doctors.saveAndFlush(doctor);
        assignmentService.publishStatus(doctor);
        // A new doctor may be able to take a request that was waiting.
        assignmentService.matchDepartment(doctor.getDepartmentId());
        log.info("Registered doctor {} in {}", doctor.getDoctorId(), doctor.getDepartmentId());
        return DoctorResponse.from(doctors.findById(doctor.getDoctorId()).orElseThrow());
    }

    @Override
    public DoctorResponse getById(String doctorId) {
        return doctors.findById(doctorId).map(DoctorResponse::from)
                .orElseThrow(() -> new DoctorNotFoundException(doctorId));
    }

    @Override
    public List<DoctorResponse> getAll() {
        return doctors.findAll().stream().map(DoctorResponse::from).toList();
    }

    @Override
    public List<DoctorResponse> getByDepartment(String departmentId) {
        return doctors.findByDepartmentIdOrderByNameAsc(departmentId).stream().map(DoctorResponse::from).toList();
    }

    @Override
    public List<DoctorResponse> getAvailableByDepartment(String departmentId) {
        return doctors.findByDepartmentIdAndStatusOrderByNameAsc(departmentId, DoctorStatus.AVAILABLE).stream()
                .map(DoctorResponse::from).toList();
    }

    /**
     * Not transactional itself: each attempt calls the transactional updater, so a
     * retry after {@link ObjectOptimisticLockingFailureException} re-reads fresh state.
     */
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3, backoff = @Backoff(delay = 50, multiplier = 2.0))
    public DoctorResponse updateStatus(String doctorId, DoctorStatus newStatus) {
        return statusUpdater.updateStatus(doctorId, newStatus);
    }

    @Override
    public List<DoctorRequestResponse> getRequests(String departmentId, DoctorRequest.Status status) {
        return requests.findByDepartmentIdAndStatusOrderByRequestedAtAsc(departmentId, status).stream()
                .map(DoctorRequestResponse::from).toList();
    }
}
