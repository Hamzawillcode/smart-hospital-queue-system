package com.hospital.patient.service;

import com.hospital.common.events.KafkaTopics;
import com.hospital.common.events.PatientCheckedInEvent;
import com.hospital.common.events.Priority;
import com.hospital.patient.dto.CheckInRequest;
import com.hospital.patient.dto.PatientResponse;
import com.hospital.patient.exception.DepartmentNotFoundException;
import com.hospital.patient.exception.InvalidStatusTransitionException;
import com.hospital.patient.exception.PatientNotFoundException;
import com.hospital.patient.model.Department;
import com.hospital.patient.model.Patient;
import com.hospital.patient.model.PatientStatus;
import com.hospital.patient.outbox.OutboxWriter;
import com.hospital.patient.repository.DepartmentRepository;
import com.hospital.patient.repository.PatientRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

/**
 * Transactional units of work. Kept separate from PatientServiceImpl so the
 * {@code @Retryable} proxy there wraps the {@code @Transactional} proxy here,
 * and each retry gets a brand-new transaction.
 */
@Component
public class PatientTransactions {

    private static final Logger log = LogManager.getLogger(PatientTransactions.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DepartmentRepository departments;
    private final PatientRepository patients;
    private final OutboxWriter outbox;
    private final Clock clock;
    private final ZoneId hospitalZone;

    public PatientTransactions(DepartmentRepository departments, PatientRepository patients, OutboxWriter outbox,
                               Clock clock, @Value("${hospital.time-zone}") ZoneId hospitalZone) {
        this.departments = departments;
        this.patients = patients;
        this.outbox = outbox;
        this.clock = clock;
        this.hospitalZone = hospitalZone;
    }

    /**
     * Token generation. The department row is locked (SELECT ... FOR UPDATE) for the
     * whole transaction, so two concurrent check-ins can never read the same counter.
     * Only local database work happens under the lock; the Kafka event is written to
     * the outbox and published after commit, so a slow broker can't hold the lock.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public PatientResponse checkIn(CheckInRequest request) {
        Department department = departments.findByIdForUpdate(request.department())
                .orElseThrow(() -> new DepartmentNotFoundException(request.department()));

        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, hospitalZone);
        int sequence = department.nextToken(today);

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID().toString());
        patient.setName(request.name().trim());
        patient.setPhone(request.phone());
        patient.setDepartment(department.getDeptId());
        patient.setPriority(request.priority() == null ? Priority.NORMAL : request.priority());
        patient.setStatus(PatientStatus.CHECKED_IN);
        patient.setTokenSequence(sequence);
        patient.setTokenNumber(department.getDeptId() + "-" + sequence);
        patient.setCheckinDate(today);
        patient.setCheckinTime(now);
        patient.setAccessCode(newAccessCode());
        patients.save(patient);

        String eventId = UUID.randomUUID().toString();
        outbox.enqueue(KafkaTopics.PATIENT_CHECKED_IN, department.getDeptId(), eventId, new PatientCheckedInEvent(
                eventId, patient.getId(), patient.getName(), patient.getDepartment(), patient.getTokenNumber(),
                sequence, today, patient.getPriority(), patient.getAccessCode(), now));

        log.info("Checked in patient {} as {}", patient.getId(), patient.getTokenNumber());
        return PatientResponse.from(patient);
    }

    /**
     * Applies a status change if the state machine allows it.
     *
     * @param strict true for staff requests (illegal move → 409); false for events, where an
     *               illegal move means a stale or reordered event and is ignored
     */
    @Transactional
    public PatientResponse applyStatus(String patientId, PatientStatus target, String doctorName, String roomNumber,
                                       boolean strict) {
        Patient patient = patients.findById(patientId).orElseThrow(() -> new PatientNotFoundException(patientId));
        if (patient.getStatus() == target) {
            return PatientResponse.from(patient);
        }
        if (!patient.getStatus().canMoveTo(target)) {
            if (strict) {
                throw new InvalidStatusTransitionException(patient.getStatus(), target);
            }
            log.info("Ignoring stale status event for patient {}: {} -> {}", patientId, patient.getStatus(), target);
            return PatientResponse.from(patient);
        }
        patient.setStatus(target);
        if (doctorName != null) {
            patient.setAssignedDoctorName(doctorName);
        }
        if (roomNumber != null) {
            patient.setRoomNumber(roomNumber);
        }
        patients.saveAndFlush(patient); // version check happens here
        return PatientResponse.from(patient);
    }

    /** 128 random bits, URL-safe. Used as the patient's WebSocket topic instead of the guessable token. */
    private static String newAccessCode() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
