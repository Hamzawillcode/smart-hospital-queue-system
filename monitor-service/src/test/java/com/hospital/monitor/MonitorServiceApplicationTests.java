package com.hospital.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.events.DoctorReleasedEvent;
import com.hospital.common.events.DoctorStatusChangedEvent;
import com.hospital.common.events.KafkaTopics;
import com.hospital.common.events.PatientCheckedInEvent;
import com.hospital.common.events.Priority;
import com.hospital.common.events.QueueUpdatedEvent;
import com.hospital.monitor.document.DeadLetterRecord;
import com.hospital.monitor.document.QueueSnapshot;
import com.hospital.monitor.dto.DashboardResponse;
import com.hospital.monitor.dto.DepartmentStatsResponse;
import com.hospital.monitor.repository.DeadLetterRepository;
import com.hospital.monitor.repository.DoctorStatusViewRepository;
import com.hospital.monitor.repository.EventLogRepository;
import com.hospital.monitor.repository.QueueSnapshotRepository;
import com.hospital.monitor.service.EventIngestionService;
import com.hospital.monitor.service.MonitorService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.jwt.secret=" + MonitorServiceApplicationTests.SECRET,
        "eureka.client.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class MonitorServiceApplicationTests {

    static final String SECRET = "integration-test-secret-key-0123456789abcdef";

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0.12");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @Autowired EventIngestionService ingestion;
    @Autowired MonitorService monitorService;
    @Autowired EventLogRepository events;
    @Autowired QueueSnapshotRepository snapshots;
    @Autowired DoctorStatusViewRepository doctorStatus;
    @Autowired DeadLetterRepository deadLetters;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired MockMvc mvc;

    @Test
    void ingestionIsIdempotentAndSnapshotsCarryWaitTimes() throws Exception {
        String json = json(queueUpdated("IDEM", 1, 3));

        ingestion.ingest(KafkaTopics.QUEUE_UPDATED, json);
        ingestion.ingest(KafkaTopics.QUEUE_UPDATED, json); // redelivered

        assertThat(snapshots.findByDepartmentIdOrderByVersionDesc("IDEM", org.springframework.data.domain.Pageable.unpaged()))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.waitingCount()).isEqualTo(3);
                    assertThat(s.longestWaitMinutes()).isGreaterThanOrEqualTo(29.0);
                    assertThat(s.waitingTokens()).containsExactly("IDEM-1", "IDEM-2", "IDEM-3");
                });
        assertThat(events.findAll().stream().filter(e -> "IDEM".equals(e.departmentId()))).hasSize(1);
    }

    @Test
    void doctorStatusIsNeverRolledBackByAnOlderEvent() throws Exception {
        ingestion.ingest(KafkaTopics.DOCTOR_STATUS_CHANGED, json(doctorStatus("doc-v", "BUSY", 3)));
        ingestion.ingest(KafkaTopics.DOCTOR_STATUS_CHANGED, json(doctorStatus("doc-v", "AVAILABLE", 2)));

        assertThat(doctorStatus.findById("doc-v").orElseThrow().status()).isEqualTo("BUSY");
    }

    @Test
    void dashboardAggregatesTodaysActivity() throws Exception {
        String dept = "DASH";
        ingestion.ingest(KafkaTopics.PATIENT_CHECKED_IN, json(checkIn(dept)));
        ingestion.ingest(KafkaTopics.PATIENT_CHECKED_IN, json(checkIn(dept)));
        ingestion.ingest(KafkaTopics.DOCTOR_RELEASED, json(new DoctorReleasedEvent(UUID.randomUUID().toString(),
                "r1", "p1", "d1", dept, DoctorReleasedEvent.ReleaseReason.CONSULTATION_COMPLETED, Instant.now())));
        ingestion.ingest(KafkaTopics.QUEUE_UPDATED, json(queueUpdated(dept, 4, 1)));
        ingestion.ingest(KafkaTopics.DOCTOR_STATUS_CHANGED, json(doctorStatus("doc-dash", "AVAILABLE", 1, dept)));

        DepartmentStatsResponse stats = monitorService.getDepartmentStats(dept);
        assertThat(stats.checkInsToday()).isEqualTo(2);
        assertThat(stats.completedToday()).isEqualTo(1);
        assertThat(stats.snapshotVersion()).isEqualTo(4);
        assertThat(stats.doctorsByStatus()).containsEntry("AVAILABLE", 1L);

        DashboardResponse dashboard = monitorService.getDashboard();
        assertThat(dashboard.departments()).extracting(DepartmentStatsResponse::departmentId).contains(dept);
    }

    @Test
    void eventsArriveFromKafka() throws Exception {
        kafkaTemplate.send(KafkaTopics.QUEUE_UPDATED, "KAFKA", json(queueUpdated("KAFKA", 7, 2))).get();
        await().atMost(Duration.ofSeconds(30)).until(() -> snapshots.findById("KAFKA:7").isPresent());
    }

    /** Poison pill → monitor's own DLT → collected → replayed by an admin → consumed. */
    @Test
    void deadLettersAreCollectedAndCanBeReplayed() throws Exception {
        kafkaTemplate.send(KafkaTopics.DOCTOR_ASSIGNED, "DLT", "{broken").get();
        await().atMost(Duration.ofSeconds(60)).until(() -> deadLetters.findByStatusOrderByReceivedAtDesc(
                DeadLetterRecord.Status.NEW).stream().anyMatch(d -> "{broken".equals(d.payload())));
        DeadLetterRecord poison = deadLetters.findByStatusOrderByReceivedAtDesc(DeadLetterRecord.Status.NEW).stream()
                .filter(d -> "{broken".equals(d.payload())).findFirst().orElseThrow();
        assertThat(poison.originalTopic()).isEqualTo(KafkaTopics.DOCTOR_ASSIGNED);
        assertThat(poison.dltTopic()).isEqualTo(KafkaTopics.DOCTOR_ASSIGNED + "-monitor-dlt");

        // A record dead-lettered by another service (here: simulated queue-service DLT) with a valid payload.
        PatientCheckedInEvent valid = checkIn("REPLAY");
        kafkaTemplate.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                KafkaTopics.PATIENT_CHECKED_IN + "-queue-dlt", null, "REPLAY", json(valid),
                List.of(new org.apache.kafka.common.header.internals.RecordHeader(
                        "kafka_dlt-original-topic", KafkaTopics.PATIENT_CHECKED_IN.getBytes(StandardCharsets.UTF_8)))))
                .get();
        await().atMost(Duration.ofSeconds(60)).until(() -> deadLetters.findAll().stream()
                .anyMatch(d -> d.payload().contains(valid.eventId())));
        DeadLetterRecord letter = deadLetters.findAll().stream()
                .filter(d -> d.payload().contains(valid.eventId())).findFirst().orElseThrow();

        // STAFF can list but not replay; ADMIN can replay.
        mvc.perform(get("/monitor/dead-letters").header("Authorization", "Bearer " + token("STAFF")))
                .andExpect(status().isOk());
        mvc.perform(post("/monitor/dead-letters/" + letter.id() + "/replay")
                        .header("Authorization", "Bearer " + token("STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/monitor/dead-letters/" + letter.id() + "/replay")
                        .header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isOk());

        assertThat(deadLetters.findById(letter.id()).orElseThrow().status()).isEqualTo(DeadLetterRecord.Status.REPLAYED);
        // Replayed to patient.checked-in, which monitor itself consumes.
        await().atMost(Duration.ofSeconds(30)).until(() -> events.findById(valid.eventId()).isPresent());
    }

    @Test
    void dashboardRequiresAStaffToken() throws Exception {
        mvc.perform(get("/monitor/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/monitor/dashboard").header("Authorization", "Bearer " + token("DOCTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/monitor/dashboard").header("Authorization", "Bearer " + token("STAFF")))
                .andExpect(status().isOk());
    }

    // ───────────────────────── helpers ─────────────────────────

    private static String token(String role) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("hospital-auth").subject("tester").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600)).claim("roles", List.of(role)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String json(Object event) throws Exception {
        return objectMapper.writeValueAsString(event);
    }

    private static QueueUpdatedEvent queueUpdated(String dept, long version, int waiting) {
        List<QueueUpdatedEvent.WaitingEntry> entries = java.util.stream.IntStream.rangeClosed(1, waiting)
                .mapToObj(i -> new QueueUpdatedEvent.WaitingEntry("q" + i, "p" + i, "Name " + i, dept + "-" + i,
                        "code" + i, Priority.NORMAL, i, (i - 1) * 10, Instant.now().minusSeconds(60L * 10 * i)))
                .toList();
        return new QueueUpdatedEvent(UUID.randomUUID().toString(), dept, version, "FIFO", entries, List.of(),
                Instant.now());
    }

    private static DoctorStatusChangedEvent doctorStatus(String doctorId, String status, long version) {
        return doctorStatus(doctorId, status, version, "OPD");
    }

    private static DoctorStatusChangedEvent doctorStatus(String doctorId, String status, long version, String dept) {
        return new DoctorStatusChangedEvent(UUID.randomUUID().toString(), doctorId, "Dr. " + doctorId, dept, status,
                null, version, Instant.now());
    }

    private static PatientCheckedInEvent checkIn(String dept) {
        return new PatientCheckedInEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "Name", dept,
                dept + "-1", 1, LocalDate.now(), Priority.NORMAL, "code", Instant.now());
    }
}
