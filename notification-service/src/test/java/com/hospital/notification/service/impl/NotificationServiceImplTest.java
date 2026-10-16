package com.hospital.notification.service.impl;

import com.hospital.common.events.Priority;
import com.hospital.common.events.QueueUpdatedEvent;
import com.hospital.notification.dto.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotificationServiceImplTest {

    private static final String A = "aaaaaaaaaaaaaaaaaaaaaa";
    private static final String B = "bbbbbbbbbbbbbbbbbbbbbb";

    private final SimpMessageSendingOperations messaging = mock(SimpMessageSendingOperations.class);
    private final NotificationServiceImpl service = new NotificationServiceImpl(messaging, Clock.systemUTC());

    @Test
    void pushesPositionsAndDropsStaleSnapshots() {
        assertThat(service.onQueueUpdated(snapshot(2, List.of(waiting(B, 1)), List.of(active(A, "IN_CONSULTATION")))))
                .isTrue();
        clearInvocations(messaging);

        // v1 arrives late (e.g. redelivered): must not move B back to position 2
        assertThat(service.onQueueUpdated(snapshot(1, List.of(waiting(A, 1), waiting(B, 2)), List.of()))).isFalse();

        verify(messaging, never()).convertAndSend(any(String.class), any(Object.class));
        assertThat(service.currentView(B)).get().extracting(NotificationMessage::position).isEqualTo(1);
        assertThat(service.currentView(A)).get().extracting(NotificationMessage::status).isEqualTo("IN_CONSULTATION");
    }

    @Test
    void patientWhoLeavesTheSnapshotGetsACompletedMessage() {
        service.onQueueUpdated(snapshot(1, List.of(waiting(A, 1), waiting(B, 2)), List.of()));
        clearInvocations(messaging);

        service.onQueueUpdated(snapshot(2, List.of(waiting(B, 1)), List.of()));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq("/topic/patient/" + A), payload.capture());
        assertThat(((NotificationMessage) payload.getValue()).status()).isEqualTo("COMPLETED");
        assertThat(service.currentView(A)).isEmpty();
    }

    @Test
    void payloadsNeverContainPatientNames() {
        service.onQueueUpdated(snapshot(1, List.of(waiting(A, 1)), List.of()));
        assertThat(service.currentView(A).orElseThrow().toString()).doesNotContain("Secret Name");
    }

    private static QueueUpdatedEvent snapshot(long version, List<QueueUpdatedEvent.WaitingEntry> waiting,
                                              List<QueueUpdatedEvent.ActiveEntry> active) {
        return new QueueUpdatedEvent("e" + version, "OPD", version, "FIFO", waiting, active, Instant.now());
    }

    private static QueueUpdatedEvent.WaitingEntry waiting(String code, int position) {
        return new QueueUpdatedEvent.WaitingEntry("q-" + code, "p-" + code, "Secret Name", "OPD-" + position, code,
                Priority.NORMAL, position, (position - 1) * 10, Instant.now());
    }

    private static QueueUpdatedEvent.ActiveEntry active(String code, String status) {
        return new QueueUpdatedEvent.ActiveEntry("q-" + code, "p-" + code, "Secret Name", "OPD-9", code, status,
                "Dr. Sharma", "OPD-1", Instant.now());
    }
}
