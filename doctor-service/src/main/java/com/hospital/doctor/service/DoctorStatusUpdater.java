package com.hospital.doctor.service;

import com.hospital.doctor.dto.DoctorResponse;
import com.hospital.doctor.exception.ConflictException;
import com.hospital.doctor.exception.DoctorNotFoundException;
import com.hospital.doctor.model.Doctor;
import com.hospital.doctor.model.DoctorStatus;
import com.hospital.doctor.repository.DoctorRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of a manual status change. Kept in its own bean so the
 * caller's {@code @Retryable} proxy wraps this {@code @Transactional} proxy:
 * every retry runs in a brand-new transaction that re-reads the doctor.
 */
@Component
public class DoctorStatusUpdater {

    private final DoctorRepository doctors;
    private final AssignmentService assignmentService;

    public DoctorStatusUpdater(DoctorRepository doctors, AssignmentService assignmentService) {
        this.doctors = doctors;
        this.assignmentService = assignmentService;
    }

    @Transactional
    public DoctorResponse updateStatus(String doctorId, DoctorStatus newStatus) {
        if (newStatus == DoctorStatus.BUSY) {
            throw new ConflictException("BUSY is set only by doctor assignment");
        }
        // Optimistic read: no row lock. If an assignment commits before we do, our
        // UPDATE ... WHERE version = ? matches no row and the transaction fails.
        Doctor doctor = doctors.findById(doctorId).orElseThrow(() -> new DoctorNotFoundException(doctorId));
        if (doctor.getStatus() == DoctorStatus.BUSY) {
            throw new ConflictException("Doctor " + doctorId + " is with a patient; complete the consultation first");
        }
        if (doctor.getStatus() != newStatus) {
            doctor.setStatus(newStatus);
            doctors.saveAndFlush(doctor);
            assignmentService.publishStatus(doctor);
            if (newStatus == DoctorStatus.AVAILABLE) {
                assignmentService.matchDepartment(doctor.getDepartmentId());
            }
        }
        return DoctorResponse.from(doctors.findById(doctorId).orElseThrow());
    }
}
