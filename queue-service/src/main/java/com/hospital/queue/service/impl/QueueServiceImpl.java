package com.hospital.queue.service.impl;

import com.hospital.common.events.DoctorAssignedEvent;
import com.hospital.common.events.DoctorReleasedEvent;
import com.hospital.common.events.DoctorReleasedEvent.ReleaseReason;
import com.hospital.common.events.DoctorRequestedEvent;
import com.hospital.common.events.KafkaTopics;
import com.hospital.common.events.PatientCheckedInEvent;
import com.hospital.common.events.Priority;
import com.hospital.common.events.QueueUpdatedEvent;
import com.hospital.queue.config.QueueProperties;
import com.hospital.queue.dto.QueueEntryResponse;
import com.hospital.queue.dto.QueueStatusResponse;
import com.hospital.queue.exception.ConflictException;
import com.hospital.queue.exception.QueueEntryNotFoundException;
import com.hospital.queue.factory.QueueStrategyFactory;
import com.hospital.queue.manager.DepartmentSnapshot;
import com.hospital.queue.manager.QueueItem;
import com.hospital.queue.manager.QueueManager;
import com.hospital.queue.model.QueueDepartment;
import com.hospital.queue.model.QueueEntry;
import com.hospital.queue.model.QueueStatus;
import com.hospital.queue.outbox.OutboxWriter;
import com.hospital.queue.repository.QueueDepartmentRepository;
import com.hospital.queue.repository.QueueEntryRepository;
import com.hospital.queue.service.QueueService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Queue state machine for one department:
 * <pre>
 *  WAITING ──dispatch──▶ AWAITING_DOCTOR ──doctor.assigned──▶ IN_CONSULTATION ──complete──▶ DONE
 *     │                        │
 *     └──────── leave ─────────┴──▶ LEFT
 * </pre>
 *
 * Every change runs through {@link #mutate}:
 * <ol>
 *   <li>take the department's in-JVM ReentrantLock (other departments proceed in parallel);</li>
 *   <li>start a transaction and lock the department row (SELECT ... FOR UPDATE), which
 *       serialises the department across all service instances;</li>
 *   <li>reload memory from MySQL if another instance changed the department meanwhile;</li>
 *   <li>apply the change, call the next patient if no doctor request is outstanding,
 *       and write the resulting events to the outbox in the same transaction;</li>
 *   <li>commit, then install the new immutable snapshot in memory, then release the lock.</li>
 * </ol>
 * If anything fails before the commit, the transaction rolls back and memory is untouched.
 */
@Service
public class QueueServiceImpl implements QueueService {

    private static final Logger log = LogManager.getLogger(QueueServiceImpl.class);
    private static final List<QueueStatus> ACTIVE = List.of(QueueStatus.AWAITING_DOCTOR, QueueStatus.IN_CONSULTATION);

    private final QueueEntryRepository entries;
    private final QueueDepartmentRepository departments;
    private final QueueManager queueManager;
    private final QueueStrategyFactory strategyFactory;
    private final OutboxWriter outbox;
    private final TransactionTemplate transactionTemplate;
    private final QueueProperties properties;
    private final Clock clock;

    public QueueServiceImpl(QueueEntryRepository entries,
                            QueueDepartmentRepository departments,
                            QueueManager queueManager,
                            QueueStrategyFactory strategyFactory,
                            OutboxWriter outbox,
                            TransactionTemplate queueTransactionTemplate,
                            QueueProperties properties,
                            Clock clock) {
        this.entries = entries;
        this.departments = departments;
        this.queueManager = queueManager;
        this.strategyFactory = strategyFactory;
        this.outbox = outbox;
        this.transactionTemplate = queueTransactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    // ───────────────────────────── commands ─────────────────────────────

    @Override
    public void enqueue(PatientCheckedInEvent event) {
        requireText(event.patientId(), "patientId");
        requireText(event.departmentId(), "departmentId");
        requireText(event.tokenNumber(), "tokenNumber");
        requireText(event.accessCode(), "accessCode");

        mutate(event.departmentId(), m -> {
            // Redelivery (outbox retry, consumer rebalance, retry topic) must not queue a patient twice.
            // The check runs under the department row lock; the unique key on patient_id backs it up.
            if (entries.existsByPatientId(event.patientId())) {
                log.info("Ignoring duplicate patient.checked-in for patient {}", event.patientId());
                return false;
            }
            QueueEntry entry = new QueueEntry();
            entry.setId(UUID.randomUUID().toString());
            entry.setPatientId(event.patientId());
            entry.setPatientName(event.patientName() == null ? "" : event.patientName());
            entry.setDepartmentId(event.departmentId());
            entry.setTokenNumber(event.tokenNumber());
            entry.setTokenSequence(event.tokenSequence());
            entry.setCheckinDate(event.checkinDate() != null ? event.checkinDate() : LocalDate.now(clock));
            entry.setPriority(event.priority() != null ? event.priority() : Priority.NORMAL);
            entry.setAccessCode(event.accessCode());
            entry.setStatus(QueueStatus.WAITING);
            entry.setJoinedAt(event.checkedInAt() != null ? event.checkedInAt() : now());
            entries.save(entry);
            m.waiting.add(toItem(entry));
            log.info("Patient {} ({}) joined {} queue", entry.getPatientId(), entry.getTokenNumber(),
                    entry.getDepartmentId());
            return true;
        });
    }

    @Override
    public void onDoctorAssigned(DoctorAssignedEvent event) {
        requireText(event.requestId(), "requestId");
        requireText(event.departmentId(), "departmentId");
        requireText(event.doctorId(), "doctorId");

        mutate(event.departmentId(), m -> {
            boolean wasPending = event.requestId().equals(m.department.getPendingRequestId());
            Optional<QueueEntry> found = entries.findById(event.requestId());

            if (found.isPresent() && found.get().getStatus() == QueueStatus.AWAITING_DOCTOR) {
                QueueEntry entry = found.get();
                entry.setStatus(QueueStatus.IN_CONSULTATION);
                entry.setDoctorId(event.doctorId());
                entry.setDoctorName(event.doctorName());
                entry.setRoomNumber(event.roomNumber());
                entry.setAssignedAt(event.assignedAt() != null ? event.assignedAt() : now());
                if (wasPending) {
                    m.department.setPendingRequestId(null);
                }
                log.info("Doctor {} assigned to patient {} in {}", event.doctorId(), entry.getPatientId(),
                        entry.getDepartmentId());
                return true;
            }

            if (found.isPresent()
                    && found.get().getStatus() == QueueStatus.IN_CONSULTATION
                    && Objects.equals(found.get().getDoctorId(), event.doctorId())) {
                log.info("Ignoring duplicate doctor.assigned for request {}", event.requestId());
                return false;
            }

            // The patient left, finished, or the request is unknown: give the doctor back.
            log.warn("Rejecting doctor.assigned for request {} (entry status {}); releasing doctor {}",
                    event.requestId(), found.map(QueueEntry::getStatus).orElse(null), event.doctorId());
            releaseDoctor(event.requestId(), event.patientId(), event.doctorId(), event.departmentId(),
                    ReleaseReason.ASSIGNMENT_REJECTED);
            if (wasPending) {
                m.department.setPendingRequestId(null);
                return true;
            }
            return false;
        });
    }

    @Override
    public QueueEntryResponse completeConsultation(String patientId) {
        String departmentId = departmentOfPatient(patientId);
        mutate(departmentId, m -> {
            QueueEntry entry = entries.findByPatientId(patientId)
                    .orElseThrow(() -> new QueueEntryNotFoundException("No queue entry for patient " + patientId));
            if (entry.getStatus() != QueueStatus.IN_CONSULTATION) {
                throw new ConflictException("Patient " + patientId + " is not in consultation (status "
                        + entry.getStatus() + ")");
            }
            entry.setStatus(QueueStatus.DONE);
            entry.setCompletedAt(now());
            releaseDoctor(entry.getId(), patientId, entry.getDoctorId(), departmentId,
                    ReleaseReason.CONSULTATION_COMPLETED);
            return true;
        });
        return getPatientQueueInfo(patientId);
    }

    @Override
    public QueueEntryResponse leaveQueue(String patientId) {
        String departmentId = departmentOfPatient(patientId);
        mutate(departmentId, m -> {
            QueueEntry entry = entries.findByPatientId(patientId)
                    .orElseThrow(() -> new QueueEntryNotFoundException("No queue entry for patient " + patientId));
            switch (entry.getStatus()) {
                case WAITING -> m.waiting.removeIf(item -> item.patientId().equals(patientId));
                case AWAITING_DOCTOR -> {
                    if (entry.getId().equals(m.department.getPendingRequestId())) {
                        m.department.setPendingRequestId(null);
                    }
                }
                default -> throw new ConflictException("Patient " + patientId + " cannot leave in status "
                        + entry.getStatus());
            }
            // Cancels a pending request, frees a doctor assigned in the meantime, and tells
            // patient-service the visit ended. doctor-service ignores unknown request ids.
            releaseDoctor(entry.getId(), patientId, null, departmentId, ReleaseReason.PATIENT_LEFT);
            entry.setStatus(QueueStatus.LEFT);
            entry.setCompletedAt(now());
            return true;
        });
        return getPatientQueueInfo(patientId);
    }

    @Override
    public QueueStatusResponse dispatchNext(String departmentId) {
        if (!departments.existsById(departmentId)) {
            throw new QueueEntryNotFoundException("Unknown department " + departmentId);
        }
        mutate(departmentId, m -> {
            String pending = m.department.getPendingRequestId();
            if (pending != null) {
                // Recovery path, e.g. after a doctor.requested landed in a DLT.
                // doctor-service treats the request id as an idempotency key.
                entries.findById(pending).ifPresent(entry -> {
                    log.info("Re-sending doctor.requested for request {}", pending);
                    requestDoctor(entry);
                });
            }
            return false; // if nothing is pending, mutate() calls the next patient itself
        });
        return getQueueStatus(departmentId);
    }

    // ───────────────────────────── queries ──────────────────────────────

    @Override
    public QueueStatusResponse getQueueStatus(String departmentId) {
        DepartmentSnapshot snapshot = currentSnapshot(departmentId);
        List<QueueEntryResponse> waiting = new ArrayList<>();
        for (QueueItem item : snapshot.waiting()) {
            int position = waiting.size() + 1;
            waiting.add(new QueueEntryResponse(item.queueEntryId(), item.patientId(), item.patientName(),
                    departmentId, item.tokenNumber(), item.priority(), QueueStatus.WAITING, position,
                    estimatedWait(position), null, null, item.joinedAt()));
        }
        List<QueueEntryResponse> active = entries
                .findByDepartmentIdAndStatusInOrderByCalledAtAsc(departmentId, ACTIVE)
                .stream()
                .map(entry -> toResponse(entry, null))
                .toList();
        return new QueueStatusResponse(departmentId, snapshot.version(),
                strategyFactory.forDepartment(departmentId).name(), waiting.size(), active.size(), waiting, active);
    }

    @Override
    public QueueEntryResponse getPatientQueueInfo(String patientId) {
        QueueEntry entry = entries.findByPatientId(patientId)
                .orElseThrow(() -> new QueueEntryNotFoundException("No queue entry for patient " + patientId));
        Integer position = null;
        if (entry.getStatus() == QueueStatus.WAITING) {
            position = currentSnapshot(entry.getDepartmentId()).positionOf(patientId).orElse(null);
        }
        return toResponse(entry, position);
    }

    @Override
    public List<QueueStatusResponse> getAllQueues() {
        return departments.findAll().stream()
                .map(QueueDepartment::getDepartmentId)
                .sorted()
                .map(this::getQueueStatus)
                .toList();
    }

    // ───────────────────────────── engine ───────────────────────────────

    /** Working state for one mutation; only used inside {@link #mutate}. */
    private static final class Mutation {
        private final QueueDepartment department;
        private final List<QueueItem> waiting;

        private Mutation(QueueDepartment department, List<QueueItem> waiting) {
            this.department = department;
            this.waiting = waiting;
        }
    }

    /**
     * @param change applies the domain change; returns true if queue state changed
     */
    private void mutate(String departmentId, Predicate<Mutation> change) {
        queueManager.withDepartmentLock(departmentId, () -> {
            DepartmentSnapshot committed = transactionTemplate.execute(status -> {
                departments.insertIfMissing(departmentId);
                QueueDepartment department = departments.findForUpdate(departmentId)
                        .orElseThrow(() -> new IllegalStateException("Department row missing: " + departmentId));

                DepartmentSnapshot current = queueManager.snapshot(departmentId);
                boolean memoryIsCurrent = current.version() == department.getStateVersion();
                List<QueueItem> waiting = memoryIsCurrent
                        ? new ArrayList<>(current.waiting())
                        : loadWaiting(departmentId);

                Mutation mutation = new Mutation(department, waiting);
                boolean changed = change.test(mutation);

                List<QueueItem> ordered = order(departmentId, mutation.waiting);
                boolean dispatched = dispatchIfIdle(department, ordered);

                if (!changed && !dispatched) {
                    return memoryIsCurrent ? null : DepartmentSnapshot.of(department.getStateVersion(), ordered);
                }
                department.setStateVersion(department.getStateVersion() + 1);
                department.setUpdatedAt(now());
                DepartmentSnapshot next = DepartmentSnapshot.of(department.getStateVersion(), ordered);
                publishQueueUpdated(departmentId, next);
                return next;
            });
            // Only reached after a successful commit.
            if (committed != null) {
                queueManager.install(departmentId, committed);
            }
            return null;
        });
    }

    /**
     * One outstanding doctor request per department: the head of the queue is called
     * (AWAITING_DOCTOR) and doctor-service is asked for a doctor. The next patient is
     * called when that request is answered or cancelled.
     */
    private boolean dispatchIfIdle(QueueDepartment department, List<QueueItem> ordered) {
        if (department.getPendingRequestId() != null || ordered.isEmpty()) {
            return false;
        }
        QueueItem head = ordered.remove(0);
        QueueEntry entry = entries.findById(head.queueEntryId())
                .orElseThrow(() -> new IllegalStateException("Queue entry missing: " + head.queueEntryId()));
        entry.setStatus(QueueStatus.AWAITING_DOCTOR);
        entry.setCalledAt(now());
        department.setPendingRequestId(entry.getId());
        requestDoctor(entry);
        log.info("Called patient {} ({}) in {}; doctor requested", entry.getPatientId(), entry.getTokenNumber(),
                entry.getDepartmentId());
        return true;
    }

    private void requestDoctor(QueueEntry entry) {
        String eventId = newEventId();
        outbox.enqueue(KafkaTopics.DOCTOR_REQUESTED, entry.getDepartmentId(), eventId, new DoctorRequestedEvent(
                eventId, entry.getId(), entry.getPatientId(), entry.getTokenNumber(), entry.getDepartmentId(),
                entry.getPriority(), now()));
    }

    private void publishQueueUpdated(String departmentId, DepartmentSnapshot snapshot) {
        List<QueueUpdatedEvent.WaitingEntry> waiting = new ArrayList<>();
        for (QueueItem item : snapshot.waiting()) {
            int position = waiting.size() + 1;
            waiting.add(new QueueUpdatedEvent.WaitingEntry(item.queueEntryId(), item.patientId(),
                    item.patientName(), item.tokenNumber(), item.accessCode(), item.priority(), position,
                    estimatedWait(position), item.joinedAt()));
        }
        // Auto-flush makes this query see the changes made earlier in this transaction.
        List<QueueUpdatedEvent.ActiveEntry> active = entries
                .findByDepartmentIdAndStatusInOrderByCalledAtAsc(departmentId, ACTIVE)
                .stream()
                .map(entry -> new QueueUpdatedEvent.ActiveEntry(entry.getId(), entry.getPatientId(),
                        entry.getPatientName(), entry.getTokenNumber(), entry.getAccessCode(),
                        entry.getStatus().name(), entry.getDoctorName(), entry.getRoomNumber(), entry.getCalledAt()))
                .toList();
        String eventId = newEventId();
        outbox.enqueue(KafkaTopics.QUEUE_UPDATED, departmentId, eventId, new QueueUpdatedEvent(
                eventId, departmentId, snapshot.version(), strategyFactory.forDepartment(departmentId).name(),
                waiting, active, now()));
    }

    /** Returns the committed snapshot, reloading from MySQL if another instance has moved the department on. */
    private DepartmentSnapshot currentSnapshot(String departmentId) {
        long databaseVersion = departments.findStateVersion(departmentId)
                .orElseThrow(() -> new QueueEntryNotFoundException("Unknown department " + departmentId));
        DepartmentSnapshot snapshot = queueManager.snapshot(departmentId);
        if (snapshot.version() == databaseVersion) {
            return snapshot;
        }
        return queueManager.withDepartmentLock(departmentId, () -> {
            DepartmentSnapshot reloaded = transactionTemplate.execute(status -> {
                QueueDepartment department = departments.findForUpdate(departmentId).orElseThrow();
                DepartmentSnapshot current = queueManager.snapshot(departmentId);
                if (current.version() == department.getStateVersion()) {
                    return current;
                }
                return DepartmentSnapshot.of(department.getStateVersion(),
                        order(departmentId, loadWaiting(departmentId)));
            });
            queueManager.install(departmentId, reloaded);
            return reloaded;
        });
    }

    private List<QueueItem> loadWaiting(String departmentId) {
        return entries.findByDepartmentIdAndStatus(departmentId, QueueStatus.WAITING).stream()
                .map(QueueServiceImpl::toItem)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<QueueItem> order(String departmentId, List<QueueItem> items) {
        List<QueueItem> ordered = new ArrayList<>(items);
        ordered.sort(strategyFactory.forDepartment(departmentId).comparator(now()));
        return ordered;
    }

    // ───────────────────────────── helpers ──────────────────────────────

    private String departmentOfPatient(String patientId) {
        return entries.findByPatientId(patientId)
                .map(QueueEntry::getDepartmentId)
                .orElseThrow(() -> new QueueEntryNotFoundException("No queue entry for patient " + patientId));
    }

    private int estimatedWait(int position) {
        return (position - 1) * properties.avgConsultationMinutes();
    }

    private static QueueItem toItem(QueueEntry entry) {
        return new QueueItem(entry.getId(), entry.getPatientId(), entry.getPatientName(), entry.getTokenNumber(),
                entry.getTokenSequence(), entry.getCheckinDate(), entry.getPriority(), entry.getAccessCode(),
                entry.getJoinedAt());
    }

    private QueueEntryResponse toResponse(QueueEntry entry, Integer position) {
        return new QueueEntryResponse(entry.getId(), entry.getPatientId(), entry.getPatientName(),
                entry.getDepartmentId(), entry.getTokenNumber(), entry.getPriority(), entry.getStatus(), position,
                position == null ? null : estimatedWait(position), entry.getDoctorName(), entry.getRoomNumber(),
                entry.getJoinedAt());
    }

    private void releaseDoctor(String requestId, String patientId, String doctorId,
                               String departmentId, ReleaseReason reason) {
        String eventId = newEventId();
        outbox.enqueue(KafkaTopics.DOCTOR_RELEASED, departmentId, eventId, new DoctorReleasedEvent(
                eventId, requestId, patientId, doctorId, departmentId, reason, now()));
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            // Non-retryable: the event goes straight to the dead-letter topic.
            throw new IllegalArgumentException("Event field '" + field + "' is required");
        }
    }
}
