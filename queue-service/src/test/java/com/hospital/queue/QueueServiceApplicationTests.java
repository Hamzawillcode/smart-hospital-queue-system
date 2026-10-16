package com.hospital.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.events.DoctorAssignedEvent;
import com.hospital.common.events.KafkaTopics;
import com.hospital.common.events.PatientCheckedInEvent;
import com.hospital.common.events.Priority;
import com.hospital.queue.dto.QueueEntryResponse;
import com.hospital.queue.dto.QueueStatusResponse;
import com.hospital.queue.exception.ConflictException;
import com.hospital.queue.manager.DepartmentSnapshot;
import com.hospital.queue.manager.QueueManager;
import com.hospital.queue.model.QueueEntry;
import com.hospital.queue.model.QueueStatus;
import com.hospital.queue.outbox.OutboxEvent;
import com.hospital.queue.outbox.OutboxEventRepository;
import com.hospital.queue.repository.QueueEntryRepository;
import com.hospital.queue.service.QueueService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end tests against real MySQL 8 and Kafka (Testcontainers).
 * Each test uses its own department id, so tests are independent.
 */
@SpringBootTest(properties = {
        "QUEUE_DB_PASSWORD=unused",
        "security.jwt.secret=integration-test-secret-key-0123456789abcdef",
        "eureka.client.enabled=false",
        "queue.strategy.departments.ER-T=PRIORITY",
        "outbox.relay.interval-ms=100"
})
@Testcontainers
class QueueServiceApplicationTests {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @Autowired QueueService queueService;
    @Autowired QueueEntryRepository entries;
    @Autowired OutboxEventRepository outbox;
    @Autowired QueueManager queueManager;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void firstPatientIsCalledOthersWaitInOrderAndDuplicatesAreIgnored() {
        String dept = "OPD-T1";
        PatientCheckedInEvent p1 = checkIn(dept, 1, Priority.NORMAL);
        PatientCheckedInEvent p2 = checkIn(dept, 2, Priority.NORMAL);
        PatientCheckedInEvent p3 = checkIn(dept, 3, Priority.NORMAL);

        queueService.enqueue(p1);
        queueService.enqueue(p2);
        queueService.enqueue(p3);
        queueService.enqueue(p2); // redelivered

        QueueStatusResponse status = queueService.getQueueStatus(dept);
        assertThat(status.active()).singleElement().satisfies(e -> {
            assertThat(e.patientId()).isEqualTo(p1.patientId());
            assertThat(e.status()).isEqualTo(QueueStatus.AWAITING_DOCTOR);
        });
        assertThat(status.waiting()).extracting(QueueEntryResponse::patientId, QueueEntryResponse::position)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(p2.patientId(), 1),
                        org.assertj.core.groups.Tuple.tuple(p3.patientId(), 2));
        assertThat(entries.findAll().stream().filter(e -> e.getDepartmentId().equals(dept))).hasSize(3);
        assertThat(doctorRequestsFor(dept, entryId(p1))).isEqualTo(1);
    }

    /** Regression for audit finding C1: an assigned patient must never be called again. */
    @Test
    void assignedPatientLeavesTheQueueAndTheNextPatientIsCalled() {
        String dept = "OPD-T2";
        PatientCheckedInEvent p1 = checkIn(dept, 1, Priority.NORMAL);
        PatientCheckedInEvent p2 = checkIn(dept, 2, Priority.NORMAL);
        queueService.enqueue(p1);
        queueService.enqueue(p2);

        DoctorAssignedEvent assigned = assigned(dept, entryId(p1), p1.patientId(), "doc-1");
        queueService.onDoctorAssigned(assigned);
        long versionAfterAssignment = queueService.getQueueStatus(dept).version();
        queueService.onDoctorAssigned(assigned); // duplicate delivery

        assertThat(entry(p1).getStatus()).isEqualTo(QueueStatus.IN_CONSULTATION);
        assertThat(entry(p1).getDoctorId()).isEqualTo("doc-1");
        assertThat(entry(p2).getStatus()).isEqualTo(QueueStatus.AWAITING_DOCTOR);
        QueueStatusResponse status = queueService.getQueueStatus(dept);
        assertThat(status.version()).isEqualTo(versionAfterAssignment);
        assertThat(status.waiting()).isEmpty();

        queueService.completeConsultation(p1.patientId());
        assertThat(entry(p1).getStatus()).isEqualTo(QueueStatus.DONE);
        assertThat(releases(dept)).anySatisfy(r -> {
            assertThat(r.get("requestId").asText()).isEqualTo(entryId(p1));
            assertThat(r.get("doctorId").asText()).isEqualTo("doc-1");
            assertThat(r.get("reason").asText()).isEqualTo("CONSULTATION_COMPLETED");
        });

        // "Call next" while p2's request is outstanding re-sends p2's request; p1 is never touched again.
        queueService.dispatchNext(dept);
        assertThat(entry(p1).getStatus()).isEqualTo(QueueStatus.DONE);
        assertThat(doctorRequestsFor(dept, entryId(p1))).isEqualTo(1);
        assertThat(doctorRequestsFor(dept, entryId(p2))).isEqualTo(2);

        assertThatThrownBy(() -> queueService.completeConsultation(p1.patientId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void lateAssignmentForAPatientWhoLeftIsRejectedAndTheDoctorReleased() {
        String dept = "OPD-T3";
        PatientCheckedInEvent p1 = checkIn(dept, 1, Priority.NORMAL);
        queueService.enqueue(p1);

        queueService.leaveQueue(p1.patientId());
        queueService.onDoctorAssigned(assigned(dept, entryId(p1), p1.patientId(), "doc-9"));

        assertThat(entry(p1).getStatus()).isEqualTo(QueueStatus.LEFT);
        assertThat(releases(dept)).extracting(r -> r.get("reason").asText())
                .containsExactly("PATIENT_LEFT", "ASSIGNMENT_REJECTED");
        assertThat(releases(dept).get(1).get("doctorId").asText()).isEqualTo("doc-9");
    }

    @Test
    void priorityDepartmentPutsCriticalPatientsFirst() {
        String dept = "ER-T";
        PatientCheckedInEvent n1 = checkIn(dept, 1, Priority.NORMAL);
        PatientCheckedInEvent n2 = checkIn(dept, 2, Priority.NORMAL);
        PatientCheckedInEvent critical = checkIn(dept, 3, Priority.CRITICAL);
        queueService.enqueue(n1); // department idle: called immediately
        queueService.enqueue(n2);
        queueService.enqueue(critical);

        QueueStatusResponse status = queueService.getQueueStatus(dept);
        assertThat(status.strategy()).isEqualTo("PRIORITY");
        assertThat(status.waiting()).extracting(QueueEntryResponse::patientId)
                .containsExactly(critical.patientId(), n2.patientId());
    }

    @Test
    void concurrentCheckInsAcrossDepartmentsKeepMemoryAndDatabaseConsistent() throws Exception {
        int perDepartment = 25;
        List<PatientCheckedInEvent> events = new ArrayList<>();
        for (String dept : List.of("CONC-A", "CONC-B")) {
            for (int seq = 1; seq <= perDepartment; seq++) {
                PatientCheckedInEvent e = checkIn(dept, seq, Priority.NORMAL);
                events.add(e);
                events.add(e); // every event delivered twice
            }
        }
        Collections.shuffle(events);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = events.stream()
                    .map(e -> pool.submit(() -> queueService.enqueue(e)))
                    .collect(Collectors.toList());
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        for (String dept : List.of("CONC-A", "CONC-B")) {
            List<QueueEntry> rows = entries.findAll().stream()
                    .filter(e -> e.getDepartmentId().equals(dept)).toList();
            assertThat(rows).hasSize(perDepartment);
            assertThat(rows.stream().filter(e -> e.getStatus() == QueueStatus.AWAITING_DOCTOR)).hasSize(1);

            QueueStatusResponse status = queueService.getQueueStatus(dept);
            assertThat(status.waiting()).hasSize(perDepartment - 1);
            assertThat(status.waiting()).extracting(QueueEntryResponse::position)
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, perDepartment - 1).boxed().toList());
            // FIFO: positions follow the token sequence
            List<Integer> sequences = status.waiting().stream()
                    .map(w -> entry(w.patientId()).getTokenSequence()).toList();
            assertThat(sequences).isSorted();

            // memory == database
            DepartmentSnapshot memory = queueManager.snapshot(dept);
            Set<String> dbWaiting = rows.stream().filter(e -> e.getStatus() == QueueStatus.WAITING)
                    .map(QueueEntry::getId).collect(Collectors.toSet());
            assertThat(memory.waiting()).extracting(i -> i.queueEntryId()).containsExactlyInAnyOrderElementsOf(dbWaiting);

            // one queue.updated per committed change, versions 1..n with no gaps or repeats
            List<Long> versions = outboxPayloads(KafkaTopics.QUEUE_UPDATED, dept).stream()
                    .map(n -> n.get("version").asLong()).toList();
            assertThat(versions).containsExactlyElementsOf(
                    IntStream.rangeClosed(1, versions.size()).mapToObj(Long::valueOf).toList());
            assertThat(versions.get(versions.size() - 1)).isEqualTo(memory.version());
        }
    }

    @Test
    void staleMemoryIsReloadedWhenAnotherInstanceChangedTheDepartment() {
        String dept = "STALE-T";
        PatientCheckedInEvent p1 = checkIn(dept, 1, Priority.NORMAL);
        PatientCheckedInEvent p2 = checkIn(dept, 2, Priority.NORMAL);
        queueService.enqueue(p1);
        queueService.enqueue(p2);

        // Simulate another queue-service instance adding a patient and bumping the version.
        String otherPatient = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO queue_entries (id, patient_id, patient_name, department_id, token_number, token_sequence,
                  checkin_date, priority, access_code, status, joined_at, version)
                VALUES (?, ?, 'Other', ?, 'STALE-T-3', 3, CURRENT_DATE, 'NORMAL', 'other-code', 'WAITING', NOW(6), 0)
                """, UUID.randomUUID().toString(), otherPatient, dept);
        jdbc.update("UPDATE queue_departments SET state_version = state_version + 1 WHERE department_id = ?", dept);

        assertThat(queueService.getQueueStatus(dept).waiting()).extracting(QueueEntryResponse::patientId)
                .containsExactly(p2.patientId(), otherPatient);
    }

    @Test
    void checkInEventsAreConsumedFromKafkaAndPoisonPillsGoToTheDeadLetterTopic() throws Exception {
        String dept = "KAFKA-T";
        PatientCheckedInEvent event = checkIn(dept, 1, Priority.NORMAL);
        kafkaTemplate.send(KafkaTopics.PATIENT_CHECKED_IN, dept, objectMapper.writeValueAsString(event)).get();

        await().atMost(Duration.ofSeconds(30)).until(() -> entries.existsByPatientId(event.patientId()));
        // The relay drains the outbox to Kafka.
        await().atMost(Duration.ofSeconds(30)).until(() -> outbox.countByPublishedAtIsNull() == 0);

        kafkaTemplate.send(KafkaTopics.PATIENT_CHECKED_IN, dept, "{not json").get();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(KafkaTopics.PATIENT_CHECKED_IN + "-queue-dlt"));
            List<ConsumerRecord<String, String>> seen = new ArrayList<>();
            await().atMost(Duration.ofSeconds(60)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(seen::add);
                return seen.stream().anyMatch(r -> "{not json".equals(r.value()));
            });
        }
    }

    // ───────────────────────── helpers ─────────────────────────

    private static PatientCheckedInEvent checkIn(String dept, int sequence, Priority priority) {
        return new PatientCheckedInEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "Patient " + sequence, dept, dept + "-" + sequence, sequence, LocalDate.now(), priority,
                UUID.randomUUID().toString().replace("-", ""), Instant.now());
    }

    private static DoctorAssignedEvent assigned(String dept, String requestId, String patientId, String doctorId) {
        return new DoctorAssignedEvent(UUID.randomUUID().toString(), requestId, patientId, "T", doctorId,
                "Dr. Test", "R-1", dept, Instant.now());
    }

    private QueueEntry entry(PatientCheckedInEvent event) {
        return entry(event.patientId());
    }

    private QueueEntry entry(String patientId) {
        return entries.findByPatientId(patientId).orElseThrow();
    }

    private String entryId(PatientCheckedInEvent event) {
        return entry(event).getId();
    }

    private long doctorRequestsFor(String dept, String requestId) {
        return outboxPayloads(KafkaTopics.DOCTOR_REQUESTED, dept).stream()
                .filter(n -> n.get("requestId").asText().equals(requestId))
                .count();
    }

    private List<JsonNode> releases(String dept) {
        return outboxPayloads(KafkaTopics.DOCTOR_RELEASED, dept);
    }

    private List<JsonNode> outboxPayloads(String topic, String dept) {
        return outbox.findByTopicOrderByIdAsc(topic).stream()
                .filter(e -> e.getMessageKey().equals(dept))
                .map(OutboxEvent::getPayload)
                .map(this::parse)
                .toList();
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
