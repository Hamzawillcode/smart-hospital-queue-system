package com.hospital.doctor.service;

import com.hospital.common.events.DoctorAssignedEvent;
import com.hospital.common.events.DoctorReleasedEvent;
import com.hospital.common.events.DoctorRequestedEvent;
import com.hospital.common.events.DoctorStatusChangedEvent;
import com.hospital.common.events.KafkaTopics;
import com.hospital.common.events.Priority;
import com.hospital.doctor.model.Doctor;
import com.hospital.doctor.model.DoctorRequest;
import com.hospital.doctor.model.DoctorStatus;
import com.hospital.doctor.outbox.OutboxWriter;
import com.hospital.doctor.repository.DoctorRepository;
import com.hospital.doctor.repository.DoctorRequestRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Doctor assignment, owned entirely by doctor-service.
 *
 * <p>Matching takes the first available doctor and the oldest pending request with
 * {@code SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1}, then flips both rows (doctor BUSY,
 * request ASSIGNED) and writes {@code doctor.assigned} to the outbox, all in one
 * transaction. That transaction is the "atomic find-and-assign": no other transaction
 * on any instance can reserve the same doctor, and the event exists only if the
 * reservation committed.</p>
 *
 * <p>Every handler is idempotent, because Kafka delivers at least once.</p>
 */
@Service
public class AssignmentService {

    private static final Logger log = LogManager.getLogger(AssignmentService.class);
    private static final PageRequest FIRST = PageRequest.of(0, 1);

    private final DoctorRepository doctors;
    private final DoctorRequestRepository requests;
    private final OutboxWriter outbox;
    private final Clock clock;

    public AssignmentService(DoctorRepository doctors, DoctorRequestRepository requests,
                             OutboxWriter outbox, Clock clock) {
        this.doctors = doctors;
        this.requests = requests;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Kafka: doctor.requested. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onDoctorRequested(DoctorRequestedEvent event) {
        requireText(event.requestId(), "requestId");
        requireText(event.departmentId(), "departmentId");
        requireText(event.patientId(), "patientId");

        Optional<DoctorRequest> existing = requests.findById(event.requestId());
        if (existing.isPresent()) {
            DoctorRequest request = existing.get();
            if (request.getStatus() == DoctorRequest.Status.ASSIGNED) {
                // queue-service re-sent the request (e.g. it never saw our answer): answer again.
                doctors.findById(request.getDoctorId()).ifPresent(doctor -> publishAssigned(request, doctor));
                log.info("Re-announced assignment for request {}", request.getRequestId());
            } else {
                log.info("Ignoring duplicate doctor.requested {} (status {})", request.getRequestId(),
                        request.getStatus());
            }
            return;
        }

        DoctorRequest request = new DoctorRequest();
        request.setRequestId(event.requestId());
        request.setPatientId(event.patientId());
        request.setTokenNumber(event.tokenNumber() == null ? "" : event.tokenNumber());
        request.setDepartmentId(event.departmentId());
        request.setPriority(event.priority() == null ? Priority.NORMAL : event.priority());
        request.setStatus(DoctorRequest.Status.PENDING);
        request.setRequestedAt(event.requestedAt() != null ? event.requestedAt() : now());
        requests.save(request);

        matchDepartment(event.departmentId());
    }

    /** Kafka: doctor.released. Frees the doctor only if it is still serving this request. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onDoctorReleased(DoctorReleasedEvent event) {
        requireText(event.requestId(), "requestId");

        Optional<DoctorRequest> request = requests.findForUpdate(event.requestId());
        String doctorId = event.doctorId() != null
                ? event.doctorId()
                : request.map(DoctorRequest::getDoctorId).orElse(null);

        String freedDepartment = null;
        if (doctorId != null) {
            Optional<Doctor> doctor = doctors.findForUpdate(doctorId);
            if (doctor.isPresent() && event.requestId().equals(doctor.get().getCurrentRequestId())) {
                Doctor d = doctor.get();
                d.setStatus(DoctorStatus.AVAILABLE);
                d.setCurrentRequestId(null);
                doctors.flush(); // the event carries the post-update @Version
                publishStatus(d);
                freedDepartment = d.getDepartmentId();
                log.info("Doctor {} released from request {} ({})", d.getDoctorId(), event.requestId(),
                        event.reason());
            } else {
                // Duplicate release, or the doctor already moved on to another patient.
                log.info("Release for request {} ignored: doctor {} not serving it", event.requestId(), doctorId);
            }
        }

        request.filter(DoctorRequest::isOpen).ifPresent(r -> {
            r.setStatus(event.reason() == DoctorReleasedEvent.ReleaseReason.CONSULTATION_COMPLETED
                    ? DoctorRequest.Status.COMPLETED
                    : DoctorRequest.Status.CANCELLED);
            r.setClosedAt(now());
        });

        if (freedDepartment != null) {
            matchDepartment(freedDepartment);
        }
    }

    /**
     * Pairs available doctors with pending requests until one side runs out.
     * Called whenever either side may have grown, and periodically by the reconciler.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int matchDepartment(String departmentId) {
        int assigned = 0;
        while (true) {
            List<Doctor> doctor = doctors.lockAvailable(departmentId, DoctorStatus.AVAILABLE, FIRST);
            if (doctor.isEmpty()) {
                return assigned;
            }
            List<DoctorRequest> request = requests.lockOldest(departmentId, DoctorRequest.Status.PENDING, FIRST);
            if (request.isEmpty()) {
                return assigned;
            }
            assign(doctor.get(0), request.get(0));
            assigned++;
        }
    }

    private void assign(Doctor doctor, DoctorRequest request) {
        Instant now = now();
        doctor.setStatus(DoctorStatus.BUSY);
        doctor.setCurrentRequestId(request.getRequestId());
        doctor.setLastAssignedAt(now);
        request.setStatus(DoctorRequest.Status.ASSIGNED);
        request.setDoctorId(doctor.getDoctorId());
        request.setAssignedAt(now);
        // Flush so the version bump is visible to the status event and the next locking query.
        doctors.flush();
        publishAssigned(request, doctor);
        publishStatus(doctor);
        log.info("Assigned doctor {} to request {} in {}", doctor.getDoctorId(), request.getRequestId(),
                request.getDepartmentId());
    }

    /** Must be called inside a transaction (the outbox write is MANDATORY). Public so the Spring proxy delegates it. */
    public void publishStatus(Doctor doctor) {
        String eventId = UUID.randomUUID().toString();
        outbox.enqueue(KafkaTopics.DOCTOR_STATUS_CHANGED, doctor.getDepartmentId(), eventId,
                new DoctorStatusChangedEvent(eventId, doctor.getDoctorId(), doctor.getName(),
                        doctor.getDepartmentId(), doctor.getStatus().name(), doctor.getCurrentRequestId(),
                        doctor.getVersion(), now()));
    }

    private void publishAssigned(DoctorRequest request, Doctor doctor) {
        String eventId = UUID.randomUUID().toString();
        outbox.enqueue(KafkaTopics.DOCTOR_ASSIGNED, request.getDepartmentId(), eventId,
                new DoctorAssignedEvent(eventId, request.getRequestId(), request.getPatientId(),
                        request.getTokenNumber(), doctor.getDoctorId(), doctor.getName(), doctor.getRoomNumber(),
                        request.getDepartmentId(),
                        request.getAssignedAt() != null ? request.getAssignedAt() : now()));
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Event field '" + field + "' is required");
        }
    }
}
