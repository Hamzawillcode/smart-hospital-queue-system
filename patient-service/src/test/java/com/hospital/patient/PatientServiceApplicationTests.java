package com.hospital.patient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.events.DoctorAssignedEvent;
import com.hospital.common.events.DoctorReleasedEvent;
import com.hospital.common.events.DoctorReleasedEvent.ReleaseReason;
import com.hospital.common.events.KafkaTopics;
import com.hospital.common.events.Priority;
import com.hospital.patient.dto.CheckInRequest;
import com.hospital.patient.dto.PatientResponse;
import com.hospital.patient.exception.DepartmentNotFoundException;
import com.hospital.patient.model.Patient;
import com.hospital.patient.model.PatientStatus;
import com.hospital.patient.outbox.OutboxEvent;
import com.hospital.patient.outbox.OutboxEventRepository;
import com.hospital.patient.repository.PatientRepository;
import com.hospital.patient.service.PatientService;
import com.hospital.patient.service.PatientTransactions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "PATIENT_DB_PASSWORD=unused",
        "security.jwt.secret=integration-test-secret-key-0123456789abcdef",
        "ADMIN_PASSWORD=admin-pass",
        "STAFF_PASSWORD=staff-pass",
        "DOCTOR_PASSWORD=doctor-pass",
        "eureka.client.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class PatientServiceApplicationTests {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @Autowired PatientService patientService;
    @Autowired PatientRepository patients;
    @Autowired OutboxEventRepository outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MockMvc mvc;
    @SpyBean PatientTransactions transactions;

    /** The resume claim: no duplicate tokens under concurrent check-ins. */
    @Test
    void concurrentCheckInsGetUniqueGapFreeTokensInLockOrder() throws Exception {
        int count = 40;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<PatientResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                int n = i;
                futures.add(pool.submit(() -> patientService.checkIn(
                        new CheckInRequest("Patient " + n, "9876543210", "CARDIOLOGY", Priority.NORMAL))));
            }
            List<String> tokens = new ArrayList<>();
            for (Future<PatientResponse> f : futures) {
                tokens.add(f.get(60, TimeUnit.SECONDS).tokenNumber());
            }
            assertThat(tokens).doesNotHaveDuplicates()
                    .containsExactlyInAnyOrderElementsOf(
                            IntStream.rangeClosed(1, count).mapToObj(i -> "CARDIOLOGY-" + i).toList());
        } finally {
            pool.shutdownNow();
        }

        // Outbox order (id) matches token order, because both are written under the same row lock.
        List<Integer> sequences = outbox.findByTopicOrderByIdAsc(KafkaTopics.PATIENT_CHECKED_IN).stream()
                .filter(o -> o.getMessageKey().equals("CARDIOLOGY"))
                .map(o -> parse(o).get("tokenSequence").asInt())
                .toList();
        assertThat(sequences).hasSize(count).isSorted();
    }

    @Test
    void tokenCounterRestartsEachDay() {
        jdbc.update("UPDATE departments SET token_date = DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY), "
                + "last_token_number = 57 WHERE dept_id = 'EMERGENCY'");

        PatientResponse first = patientService.checkIn(
                new CheckInRequest("Asha", "9123456780", "EMERGENCY", Priority.CRITICAL));

        assertThat(first.tokenNumber()).isEqualTo("EMERGENCY-1");
        assertThat(first.accessCode()).hasSizeGreaterThanOrEqualTo(20).doesNotContain("EMERGENCY");
        assertThat(first.trackingPath()).isEqualTo("/track/" + first.accessCode());
    }

    @Test
    void unknownDepartmentIsRejected() {
        assertThatThrownBy(() -> patientService.checkIn(new CheckInRequest("X", "9123456780", "NOPE", null)))
                .isInstanceOf(DepartmentNotFoundException.class);
    }

    @Test
    void statusFollowsEventsAndIgnoresOutOfOrderOnes() {
        PatientResponse p = patientService.checkIn(new CheckInRequest("Ravi", "9000000001", "OPD", null));

        // completed arrives before assigned (e.g. assigned went through a retry topic)
        patientService.onDoctorReleased(new DoctorReleasedEvent(UUID.randomUUID().toString(), "req", p.id(), "d1",
                "OPD", ReleaseReason.CONSULTATION_COMPLETED, Instant.now()));
        patientService.onDoctorAssigned(new DoctorAssignedEvent(UUID.randomUUID().toString(), "req", p.id(),
                p.tokenNumber(), "d1", "Dr. Late", "OPD-1", "OPD", Instant.now()));

        Patient stored = patients.findById(p.id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PatientStatus.DONE);
        assertThat(stored.getAssignedDoctorName()).isNull();
    }

    @Test
    void assignmentThenCompletionUpdatesThePatient() {
        PatientResponse p = patientService.checkIn(new CheckInRequest("Meera", "9000000002", "OPD", null));

        patientService.onDoctorAssigned(new DoctorAssignedEvent(UUID.randomUUID().toString(), "req", p.id(),
                p.tokenNumber(), "d1", "Dr. Sharma", "OPD-1", "OPD", Instant.now()));
        assertThat(patients.findById(p.id()).orElseThrow().getStatus()).isEqualTo(PatientStatus.IN_CONSULTATION);
        assertThat(patients.findById(p.id()).orElseThrow().getRoomNumber()).isEqualTo("OPD-1");

        patientService.onDoctorReleased(new DoctorReleasedEvent(UUID.randomUUID().toString(), "req", p.id(), "d1",
                "OPD", ReleaseReason.CONSULTATION_COMPLETED, Instant.now()));
        assertThat(patients.findById(p.id()).orElseThrow().getStatus()).isEqualTo(PatientStatus.DONE);
    }

    @Test
    void optimisticLockConflictOnStatusUpdateIsRetried() {
        PatientResponse p = patientService.checkIn(new CheckInRequest("Kiran", "9000000003", "OPD", null));
        doThrow(new ObjectOptimisticLockingFailureException(Patient.class, p.id()))
                .doCallRealMethod()
                .when(transactions).applyStatus(eq(p.id()), eq(PatientStatus.LEFT), any(), any(), anyBoolean());

        patientService.updateStatus(p.id(), PatientStatus.LEFT);

        assertThat(patients.findById(p.id()).orElseThrow().getStatus()).isEqualTo(PatientStatus.LEFT);
        verify(transactions, times(2)).applyStatus(eq(p.id()), eq(PatientStatus.LEFT), any(), any(), anyBoolean());
    }

    @Test
    void jwtLoginAndRoleBasedAccess() throws Exception {
        mvc.perform(get("/patients/departments")).andExpect(status().isUnauthorized());

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reception\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        String staffToken = login("reception", "staff-pass");
        String body = mvc.perform(post("/patients/checkin").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Web\",\"phone\":\"9000000004\",\"department\":\"OPD\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String patientId = objectMapper.readTree(body).get("id").asText();

        // STAFF may not correct statuses; ADMIN may.
        mvc.perform(put("/patients/" + patientId + "/status").param("status", "LEFT")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
        mvc.perform(put("/patients/" + patientId + "/status").param("status", "LEFT")
                        .header("Authorization", "Bearer " + login("admin", "admin-pass")))
                .andExpect(status().isOk());

        // Validation errors are reported as 400, not 500.
        mvc.perform(post("/patients/checkin").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"phone\":\"12\",\"department\":\"OPD\"}"))
                .andExpect(status().isBadRequest());

        // A tampered token is rejected.
        mvc.perform(get("/patients/departments").header("Authorization", "Bearer " + staffToken + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkInEventsAreRelayedToKafka() {
        patientService.checkIn(new CheckInRequest("Relay", "9000000005", "OPD", null));
        await().atMost(Duration.ofSeconds(30)).until(() -> outbox.countByPublishedAtIsNull() == 0);
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("tokenType").asText()).isEqualTo("Bearer");
        return json.get("accessToken").asText();
    }

    private JsonNode parse(OutboxEvent event) {
        try {
            return objectMapper.readTree(event.getPayload());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
