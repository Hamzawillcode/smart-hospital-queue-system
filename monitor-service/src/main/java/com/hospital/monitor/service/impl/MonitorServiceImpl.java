package com.hospital.monitor.service.impl;

import com.hospital.common.events.DoctorReleasedEvent.ReleaseReason;
import com.hospital.common.events.KafkaTopics;
import com.hospital.monitor.document.DeadLetterRecord;
import com.hospital.monitor.document.DoctorStatusView;
import com.hospital.monitor.document.QueueSnapshot;
import com.hospital.monitor.dto.DashboardResponse;
import com.hospital.monitor.dto.DepartmentStatsResponse;
import com.hospital.monitor.dto.EventLogResponse;
import com.hospital.monitor.exception.ConflictException;
import com.hospital.monitor.exception.NotFoundException;
import com.hospital.monitor.repository.DeadLetterRepository;
import com.hospital.monitor.repository.DoctorStatusViewRepository;
import com.hospital.monitor.repository.EventLogRepository;
import com.hospital.monitor.repository.QueueSnapshotRepository;
import com.hospital.monitor.service.MonitorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/** Builds the operations dashboard purely from data in MongoDB: no synchronous calls to other services. */
@Service
public class MonitorServiceImpl implements MonitorService {

    private static final Logger log = LogManager.getLogger(MonitorServiceImpl.class);
    private static final String COMPLETED = ReleaseReason.CONSULTATION_COMPLETED.name();

    private final EventLogRepository events;
    private final QueueSnapshotRepository snapshots;
    private final DoctorStatusViewRepository doctorStatus;
    private final DeadLetterRepository deadLetters;
    private final MongoTemplate mongo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final ZoneId hospitalZone;

    public MonitorServiceImpl(EventLogRepository events, QueueSnapshotRepository snapshots,
                              DoctorStatusViewRepository doctorStatus, DeadLetterRepository deadLetters,
                              MongoTemplate mongo, KafkaTemplate<String, String> kafkaTemplate, Clock clock,
                              @Value("${hospital.time-zone}") ZoneId hospitalZone) {
        this.events = events;
        this.snapshots = snapshots;
        this.doctorStatus = doctorStatus;
        this.deadLetters = deadLetters;
        this.mongo = mongo;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.hospitalZone = hospitalZone;
    }

    @Override
    public DashboardResponse getDashboard() {
        Instant startOfDay = startOfToday();
        TreeSet<String> departments = new TreeSet<>(
                mongo.findDistinct(new Query(), "departmentId", QueueSnapshot.class, String.class));
        departments.addAll(mongo.findDistinct(new Query(), "departmentId", DoctorStatusView.class, String.class));

        return new DashboardResponse(
                Instant.now(clock),
                events.countByEventTypeAndOccurredAtAfter(KafkaTopics.PATIENT_CHECKED_IN, startOfDay),
                events.countByEventTypeAndOutcomeAndOccurredAtAfter(KafkaTopics.DOCTOR_RELEASED, COMPLETED, startOfDay),
                deadLetters.countByStatus(DeadLetterRecord.Status.NEW),
                departments.stream().map(this::getDepartmentStats).toList(),
                getRecentEvents(10));
    }

    @Override
    public DepartmentStatsResponse getDepartmentStats(String departmentId) {
        Instant startOfDay = startOfToday();
        Optional<QueueSnapshot> latest = snapshots.findTopByDepartmentIdOrderByVersionDesc(departmentId);
        Map<String, Long> doctors = doctorStatus.findByDepartmentIdOrderByDoctorNameAsc(departmentId).stream()
                .collect(Collectors.groupingBy(DoctorStatusView::status, TreeMap::new, Collectors.counting()));
        return new DepartmentStatsResponse(
                departmentId,
                latest.map(QueueSnapshot::strategy).orElse(null),
                latest.map(QueueSnapshot::version).orElse(0L),
                latest.map(QueueSnapshot::waitingCount).orElse(0),
                latest.map(QueueSnapshot::activeCount).orElse(0),
                latest.map(QueueSnapshot::avgWaitMinutes).orElse(0.0),
                latest.map(QueueSnapshot::longestWaitMinutes).orElse(0.0),
                doctors,
                events.countByEventTypeAndDepartmentIdAndOccurredAtAfter(KafkaTopics.PATIENT_CHECKED_IN,
                        departmentId, startOfDay),
                events.countByEventTypeAndDepartmentIdAndOutcomeAndOccurredAtAfter(KafkaTopics.DOCTOR_RELEASED,
                        departmentId, COMPLETED, startOfDay),
                latest.map(QueueSnapshot::occurredAt).orElse(null));
    }

    @Override
    public List<QueueSnapshot> getSnapshotHistory(String departmentId, int limit) {
        return snapshots.findByDepartmentIdOrderByVersionDesc(departmentId, PageRequest.of(0, clamp(limit, 500)));
    }

    @Override
    public List<EventLogResponse> getRecentEvents(int limit) {
        return events.findByOrderByOccurredAtDesc(PageRequest.of(0, clamp(limit, 100))).stream()
                .map(EventLogResponse::from).toList();
    }

    @Override
    public List<EventLogResponse> getPatientHistory(String patientId) {
        return events.findByPatientIdOrderByOccurredAtAsc(patientId).stream().map(EventLogResponse::from).toList();
    }

    @Override
    public List<DeadLetterRecord> getDeadLetters(DeadLetterRecord.Status status) {
        return deadLetters.findByStatusOrderByReceivedAtDesc(status);
    }

    @Override
    public DeadLetterRecord replayDeadLetter(String id) {
        DeadLetterRecord record = deadLetters.findById(id)
                .orElseThrow(() -> new NotFoundException("Dead letter not found: " + id));
        if (record.status() == DeadLetterRecord.Status.REPLAYED) {
            throw new ConflictException("Dead letter " + id + " was already replayed");
        }
        try {
            kafkaTemplate.send(record.originalTopic(), record.messageKey(), record.payload()).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while replaying " + id, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka unavailable, dead letter " + id + " not replayed", e);
        }
        log.info("Replayed dead letter {} to {}", id, record.originalTopic());
        return deadLetters.save(record.replayed(Instant.now(clock)));
    }

    private Instant startOfToday() {
        return LocalDate.now(clock.withZone(hospitalZone)).atStartOfDay(hospitalZone).toInstant();
    }

    private static int clamp(int value, int max) {
        return Math.max(1, Math.min(value, max));
    }
}
