package com.hospital.doctor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.events.DoctorReleasedEvent;
import com.hospital.common.events.DoctorReleasedEvent.ReleaseReason;
import com.hospital.common.events.DoctorRequestedEvent;
import com.hospital.common.events.KafkaTopics;
import com.hospital.common.events.Priority;
import com.hospital.doctor.dto.DoctorRegisterRequest;
import com.hospital.doctor.dto.DoctorResponse;
import com.hospital.doctor.exception.ConflictException;
import com.hospital.doctor.model.Doctor;
import com.hospital.doctor.model.DoctorRequest;
import com.hospital.doctor.model.DoctorStatus;
import com.hospital.doctor.outbox.OutboxEvent;
import com.hospital.doctor.outbox.OutboxEventRepository;
import com.hospital.doctor.repository.DoctorRepository;
import com.hospital.doctor.repository.DoctorRequestRepository;
import com.hospital.doctor.service.AssignmentService;
import com.hospital.doctor.service.DoctorService;
import com.hospital.doctor.service.DoctorStatusUpdater;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "DOCTOR_DB_PASSWORD=unused",
        "security.jwt.secret=integration-test-secret-key-0123456789abcdef",
        "eureka.client.enabled=false",
        "assignment.reconcile-interval-ms=500"
})
@Testcontainers
class DoctorServiceApplicationTests {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @Autowired AssignmentService assignmentService;
    @Autowired DoctorService doctorService;
    @Autowired DoctorRepository doctors;
    @Autowired DoctorRequestRepository requests;
    @Autowired OutboxEventRepository outbox;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ObjectMapper objectMapper;
    @SpyBean DoctorStatusUpdater statusUpdater;

    @Test
    void seedDataIsLoadedByFlyway() {
        assertThat(doctors.findByDepartmentIdOrderByNameAsc("OPD")).hasSize(2);
    }

    /** Regression for audit finding C2 (Optional query + more than one available doctor) and the core race. */
    @Test
    void concurrentRequestsNeverDoubleBookADoctor() throws Exception {
        String dept = "CONC";
        for (int i = 1; i <= 3; i++) {
            register(dept, "Dr. C" + i);
        }
        List<DoctorRequestedEvent> events = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            events.add(request(dept));
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = events.stream()
                    .map(e -> pool.submit(() -> assignmentService.onDoctorRequested(e)))
                    .collect(Collectors.toList());
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        await().atMost(Duration.ofSeconds(10)).until(() -> countRequests(dept, DoctorRequest.Status.ASSIGNED) == 3);
        assertThat(countRequests(dept, DoctorRequest.Status.PENDING)).isEqualTo(17);

        List<Doctor> busy = doctors.findByDepartmentIdOrderByNameAsc(dept);
        assertThat(busy).allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(DoctorStatus.BUSY));
        Set<String> servedRequests = busy.stream().map(Doctor::getCurrentRequestId).collect(Collectors.toSet());
        assertThat(servedRequests).hasSize(3); // three distinct requests, one per doctor
        assertThat(assignedEvents(dept)).hasSize(3);

        // Releasing one doctor immediately hands it the oldest pending request.
        Doctor first = busy.get(0);
        String finished = first.getCurrentRequestId();
        assignmentService.onDoctorReleased(release(dept, finished, first.getDoctorId(),
                ReleaseReason.CONSULTATION_COMPLETED));

        Doctor reassigned = doctors.findById(first.getDoctorId()).orElseThrow();
        assertThat(reassigned.getStatus()).isEqualTo(DoctorStatus.BUSY);
        assertThat(reassigned.getCurrentRequestId()).isNotEqualTo(finished);
        assertThat(requests.findById(finished).orElseThrow().getStatus()).isEqualTo(DoctorRequest.Status.COMPLETED);
        assertThat(countRequests(dept, DoctorRequest.Status.PENDING)).isEqualTo(16);
    }

    @Test
    void duplicateRequestIsIdempotentAndReAnnouncesTheAssignment() {
        String dept = "IDEM";
        register(dept, "Dr. Idem");
        DoctorRequestedEvent event = request(dept);

        assignmentService.onDoctorRequested(event);
        assignmentService.onDoctorRequested(event);

        assertThat(requests.findAll().stream().filter(r -> r.getDepartmentId().equals(dept))).hasSize(1);
        assertThat(assignedEvents(dept)).hasSize(2)
                .allSatisfy(n -> assertThat(n.get("requestId").asText()).isEqualTo(event.requestId()));
    }

    @Test
    void duplicateReleaseNeverFreesADoctorServingSomeoneElse() {
        String dept = "REL";
        DoctorResponse doctor = register(dept, "Dr. Rel");
        DoctorRequestedEvent first = request(dept);
        DoctorRequestedEvent second = request(dept);
        assignmentService.onDoctorRequested(first);
        assignmentService.onDoctorRequested(second); // pending: only one doctor

        DoctorReleasedEvent done = release(dept, first.requestId(), doctor.doctorId(),
                ReleaseReason.CONSULTATION_COMPLETED);
        assignmentService.onDoctorReleased(done);   // doctor moves on to `second`
        assignmentService.onDoctorReleased(done);   // redelivered

        Doctor current = doctors.findById(doctor.doctorId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(DoctorStatus.BUSY);
        assertThat(current.getCurrentRequestId()).isEqualTo(second.requestId());
    }

    @Test
    void patientLeavingCancelsAPendingRequest() {
        String dept = "LEFT";
        DoctorResponse doctor = register(dept, "Dr. Left");
        doctorService.updateStatus(doctor.doctorId(), DoctorStatus.ON_BREAK);
        DoctorRequestedEvent event = request(dept);
        assignmentService.onDoctorRequested(event);

        assignmentService.onDoctorReleased(release(dept, event.requestId(), null, ReleaseReason.PATIENT_LEFT));
        doctorService.updateStatus(doctor.doctorId(), DoctorStatus.AVAILABLE);

        assertThat(requests.findById(event.requestId()).orElseThrow().getStatus())
                .isEqualTo(DoctorRequest.Status.CANCELLED);
        assertThat(doctors.findById(doctor.doctorId()).orElseThrow().getStatus()).isEqualTo(DoctorStatus.AVAILABLE);
    }

    @Test
    void comingBackFromBreakPicksUpTheWaitingRequest() {
        String dept = "BREAK";
        DoctorResponse doctor = register(dept, "Dr. Break");
        doctorService.updateStatus(doctor.doctorId(), DoctorStatus.ON_BREAK);
        DoctorRequestedEvent event = request(dept);
        assignmentService.onDoctorRequested(event);
        assertThat(requests.findById(event.requestId()).orElseThrow().getStatus())
                .isEqualTo(DoctorRequest.Status.PENDING);

        doctorService.updateStatus(doctor.doctorId(), DoctorStatus.AVAILABLE);

        assertThat(doctors.findById(doctor.doctorId()).orElseThrow().getCurrentRequestId())
                .isEqualTo(event.requestId());
    }

    @Test
    void busyDoctorCannotBeMovedManually() {
        String dept = "BUSY";
        DoctorResponse doctor = register(dept, "Dr. Busy");
        assignmentService.onDoctorRequested(request(dept));

        assertThatThrownBy(() -> doctorService.updateStatus(doctor.doctorId(), DoctorStatus.OFF_DUTY))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void optimisticLockConflictIsRetriedInAFreshTransaction() {
        DoctorResponse doctor = register("RETRY", "Dr. Retry");
        doThrow(new ObjectOptimisticLockingFailureException(Doctor.class, doctor.doctorId()))
                .doCallRealMethod()
                .when(statusUpdater).updateStatus(eq(doctor.doctorId()), any());

        DoctorResponse result = doctorService.updateStatus(doctor.doctorId(), DoctorStatus.ON_BREAK);

        assertThat(result.status()).isEqualTo(DoctorStatus.ON_BREAK);
        verify(statusUpdater, times(2)).updateStatus(eq(doctor.doctorId()), any());
    }

    @Test
    void staleManualUpdateIsRejectedByVersionCheck() {
        DoctorResponse registered = register("VER", "Dr. Version");
        Doctor stale = doctors.findById(registered.doctorId()).orElseThrow();
        doctorService.updateStatus(registered.doctorId(), DoctorStatus.ON_BREAK); // bumps the version

        stale.setStatus(DoctorStatus.OFF_DUTY);
        assertThatThrownBy(() -> doctors.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void requestsArriveOverKafkaAndAssignmentsAreRelayed() throws Exception {
        String dept = "KAFKA";
        register(dept, "Dr. Kafka");
        DoctorRequestedEvent event = request(dept);
        kafkaTemplate.send(KafkaTopics.DOCTOR_REQUESTED, dept, objectMapper.writeValueAsString(event)).get();

        await().atMost(Duration.ofSeconds(30)).until(() -> requests.findById(event.requestId())
                .map(r -> r.getStatus() == DoctorRequest.Status.ASSIGNED).orElse(false));
        await().atMost(Duration.ofSeconds(30)).until(() -> outbox.findAll().stream()
                .allMatch(o -> o.getPublishedAt() != null));
    }

    // ───────────────────────── helpers ─────────────────────────

    private DoctorResponse register(String dept, String name) {
        return doctorService.register(new DoctorRegisterRequest(name, dept, "General", "R-" + name.hashCode()));
    }

    private static DoctorRequestedEvent request(String dept) {
        String id = UUID.randomUUID().toString();
        return new DoctorRequestedEvent(UUID.randomUUID().toString(), id, UUID.randomUUID().toString(),
                dept + "-1", dept, Priority.NORMAL, Instant.now());
    }

    private static DoctorReleasedEvent release(String dept, String requestId, String doctorId, ReleaseReason reason) {
        return new DoctorReleasedEvent(UUID.randomUUID().toString(), requestId, "p", doctorId, dept, reason,
                Instant.now());
    }

    private long countRequests(String dept, DoctorRequest.Status status) {
        return requests.findByDepartmentIdAndStatusOrderByRequestedAtAsc(dept, status).size();
    }

    private List<JsonNode> assignedEvents(String dept) {
        return outbox.findByTopicOrderByIdAsc(KafkaTopics.DOCTOR_ASSIGNED).stream()
                .filter(o -> o.getMessageKey().equals(dept))
                .map(OutboxEvent::getPayload)
                .map(json -> {
                    try {
                        return objectMapper.readTree(json);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }
}
