package com.hospital.monitor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.events.DoctorStatusChangedEvent;
import com.hospital.common.events.KafkaTopics;
import com.hospital.common.events.QueueUpdatedEvent;
import com.hospital.monitor.document.DoctorActivityLog;
import com.hospital.monitor.document.DoctorStatusView;
import com.hospital.monitor.document.EventLog;
import com.hospital.monitor.document.QueueSnapshot;
import com.hospital.monitor.repository.DoctorActivityLogRepository;
import com.hospital.monitor.repository.EventLogRepository;
import com.hospital.monitor.repository.QueueSnapshotRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Stores every domain event and derives the dashboard read models from them.
 * All writes are idempotent (keyed by eventId, department+version, or guarded by
 * version), because Kafka delivers at least once.
 */
@Service
public class EventIngestionService {

    private static final Map<String, String> SOURCE = Map.of(
            KafkaTopics.PATIENT_CHECKED_IN, "patient-service",
            KafkaTopics.QUEUE_UPDATED, "queue-service",
            KafkaTopics.DOCTOR_REQUESTED, "queue-service",
            KafkaTopics.DOCTOR_RELEASED, "queue-service",
            KafkaTopics.DOCTOR_ASSIGNED, "doctor-service",
            KafkaTopics.DOCTOR_STATUS_CHANGED, "doctor-service");

    private static final List<String> TIME_FIELDS = List.of("occurredAt", "checkedInAt", "assignedAt", "requestedAt");

    private final EventLogRepository events;
    private final QueueSnapshotRepository snapshots;
    private final DoctorActivityLogRepository doctorActivity;
    private final MongoTemplate mongo;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EventIngestionService(EventLogRepository events, QueueSnapshotRepository snapshots,
                                 DoctorActivityLogRepository doctorActivity, MongoTemplate mongo,
                                 ObjectMapper objectMapper, Clock clock) {
        this.events = events;
        this.snapshots = snapshots;
        this.doctorActivity = doctorActivity;
        this.mongo = mongo;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** @throws IllegalArgumentException for malformed events (not retried; sent to the DLT) */
    public void ingest(String topic, String payload) {
        if (!SOURCE.containsKey(topic)) {
            throw new IllegalArgumentException("Unexpected topic " + topic);
        }
        JsonNode node = parse(payload);
        String eventId = text(node, "eventId");
        if (eventId == null) {
            throw new IllegalArgumentException("Event without eventId on " + topic);
        }
        Instant now = Instant.now(clock);
        events.save(new EventLog(eventId, topic, SOURCE.get(topic), text(node, "departmentId"),
                text(node, "patientId"), outcome(topic, node), payload, occurredAt(node, now), now));

        try {
            switch (topic) {
                case KafkaTopics.QUEUE_UPDATED -> storeSnapshot(objectMapper.treeToValue(node, QueueUpdatedEvent.class));
                case KafkaTopics.DOCTOR_STATUS_CHANGED ->
                        storeDoctorStatus(objectMapper.treeToValue(node, DoctorStatusChangedEvent.class), now);
                default -> {
                }
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed " + topic + " event " + eventId, e);
        }
    }

    private void storeSnapshot(QueueUpdatedEvent event) {
        Instant at = event.occurredAt() != null ? event.occurredAt() : Instant.now(clock);
        List<QueueUpdatedEvent.WaitingEntry> waiting = event.waiting() == null ? List.of() : event.waiting();
        double avg = waiting.stream().mapToDouble(w -> minutesBetween(w.joinedAt(), at)).average().orElse(0);
        double longest = waiting.stream().mapToDouble(w -> minutesBetween(w.joinedAt(), at)).max().orElse(0);
        snapshots.save(new QueueSnapshot(event.departmentId() + ":" + event.version(), event.departmentId(),
                event.version(), event.strategy(), waiting.size(), event.active() == null ? 0 : event.active().size(),
                round(avg), round(longest), waiting.stream().map(QueueUpdatedEvent.WaitingEntry::tokenNumber).toList(),
                at));
    }

    private void storeDoctorStatus(DoctorStatusChangedEvent event, Instant now) {
        doctorActivity.save(new DoctorActivityLog(event.eventId(), event.doctorId(), event.doctorName(),
                event.departmentId(), event.status(), event.currentRequestId(), event.version(),
                event.occurredAt() != null ? event.occurredAt() : now));
        // Replace the current view only if this event is newer (higher entity version).
        Query newerOnly = Query.query(Criteria.where("_id").is(event.doctorId()).and("version").lt(event.version()));
        Update update = new Update()
                .set("doctorName", event.doctorName())
                .set("departmentId", event.departmentId())
                .set("status", event.status())
                .set("version", event.version())
                .set("updatedAt", now);
        try {
            mongo.upsert(newerOnly, update, DoctorStatusView.class);
        } catch (DuplicateKeyException e) {
            // A document with a newer (or equal) version already exists: keep it.
        }
    }

    private JsonNode parse(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("Event payload is not a JSON object");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Event payload is not valid JSON", e);
        }
    }

    private static String outcome(String topic, JsonNode node) {
        return switch (topic) {
            case KafkaTopics.DOCTOR_RELEASED -> text(node, "reason");
            case KafkaTopics.DOCTOR_STATUS_CHANGED -> text(node, "status");
            default -> null;
        };
    }

    private static Instant occurredAt(JsonNode node, Instant fallback) {
        for (String field : TIME_FIELDS) {
            String value = text(node, field);
            if (value != null) {
                try {
                    return Instant.parse(value);
                } catch (RuntimeException ignored) {
                    // try the next field
                }
            }
        }
        return fallback;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static double minutesBetween(Instant from, Instant to) {
        return from == null ? 0 : Math.max(0, Duration.between(from, to).toSeconds() / 60.0);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
