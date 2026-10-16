package com.hospital.patient.service.impl;

import com.hospital.common.events.DoctorAssignedEvent;
import com.hospital.common.events.DoctorReleasedEvent;
import com.hospital.patient.dto.CheckInRequest;
import com.hospital.patient.dto.PatientResponse;
import com.hospital.patient.exception.DepartmentNotFoundException;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.model.Department;
import com.hospital.patient.model.PatientStatus;
import com.hospital.patient.repository.DepartmentRepository;
import com.hospital.patient.repository.PatientRepository;
import com.hospital.patient.service.PatientService;
import com.hospital.patient.service.PatientTransactions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Retry boundary. Each {@code @Retryable} method delegates to a {@code @Transactional}
 * method on {@link PatientTransactions}, so every attempt starts a fresh transaction:
 * <ul>
 *   <li>check-in retries a pessimistic lock timeout or deadlock on the department row;</li>
 *   <li>status updates retry an optimistic-lock (@Version) conflict on the patient row.</li>
 * </ul>
 */
@Service
public class PatientServiceImpl implements PatientService {

    private static final Logger log = LogManager.getLogger(PatientServiceImpl.class);

    private final PatientTransactions transactions;
    private final PatientRepository patients;
    private final DepartmentRepository departments;

    public PatientServiceImpl(PatientTransactions transactions, PatientRepository patients,
                              DepartmentRepository departments) {
        this.transactions = transactions;
        this.patients = patients;
        this.departments = departments;
    }

    @Override
    @Retryable(retryFor = PessimisticLockingFailureException.class,
            maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2.0))
    public PatientResponse checkIn(CheckInRequest request) {
        return transactions.checkIn(request);
    }

    @Override
    public PatientResponse getById(String patientId) {
        return patients.findById(patientId).map(PatientResponse::from)
                .orElseThrow(() -> new PatientNotFoundException(patientId));
    }

    @Override
    public List<PatientResponse> getByDepartment(String departmentId, LocalDate date) {
        if (!departments.existsById(departmentId)) {
            throw new DepartmentNotFoundException(departmentId);
        }
        return patients.findByDepartmentAndCheckinDateOrderByTokenSequenceAsc(departmentId, date).stream()
                .map(PatientResponse::from)
                .toList();
    }

    @Override
    public List<Department> getDepartments() {
        return departments.findAll();
    }

    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3, backoff = @Backoff(delay = 50, multiplier = 2.0))
    public PatientResponse updateStatus(String patientId, PatientStatus status) {
        return transactions.applyStatus(patientId, status, null, null, true);
    }

    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3, backoff = @Backoff(delay = 50, multiplier = 2.0))
    public void onDoctorAssigned(DoctorAssignedEvent event) {
        applyFromEvent(event.patientId(), PatientStatus.IN_CONSULTATION, event.doctorName(), event.roomNumber());
    }

    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3, backoff = @Backoff(delay = 50, multiplier = 2.0))
    public void onDoctorReleased(DoctorReleasedEvent event) {
        PatientStatus target = switch (event.reason()) {
            case CONSULTATION_COMPLETED -> PatientStatus.DONE;
            case PATIENT_LEFT -> PatientStatus.LEFT;
            case ASSIGNMENT_REJECTED -> null; // a doctor was handed back; the patient's visit is unaffected
        };
        if (target != null) {
            applyFromEvent(event.patientId(), target, null, null);
        }
    }

    private void applyFromEvent(String patientId, PatientStatus target, String doctorName, String room) {
        if (patientId == null || !patients.existsById(patientId)) {
            log.warn("Status event for unknown patient {} ignored", patientId);
            return;
        }
        transactions.applyStatus(patientId, target, doctorName, room, false);
    }
}
