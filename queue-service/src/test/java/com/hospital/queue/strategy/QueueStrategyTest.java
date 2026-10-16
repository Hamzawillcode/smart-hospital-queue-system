package com.hospital.queue.strategy;

import com.hospital.common.events.Priority;
import com.hospital.queue.config.QueueProperties;
import com.hospital.queue.factory.QueueStrategyFactory;
import com.hospital.queue.manager.QueueItem;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueStrategyTest {

    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    private final QueueProperties properties = new QueueProperties(10, 15,
            new QueueProperties.Strategy("FIFO", Map.of("EMERGENCY", "PRIORITY", "cardiology", "AGING_PRIORITY")));

    private final List<QueuePriorityStrategy> strategies = List.of(
            new FifoStrategy(), new EmergencyPriorityStrategy(), new AgingPriorityStrategy(properties));

    @Test
    void fifoOrdersByTokenSequenceNotByWallClock() {
        // seq 2 has an earlier timestamp (clock skew) but must still come after seq 1
        QueueItem first = item("a", 1, Priority.NORMAL, NOW.minusSeconds(10));
        QueueItem second = item("b", 2, Priority.CRITICAL, NOW.minusSeconds(60));

        assertThat(sort(new FifoStrategy(), second, first)).containsExactly(first, second);
    }

    @Test
    void priorityServesCriticalFirstAndKeepsArrivalOrderWithinPriority() {
        QueueItem normal1 = item("n1", 1, Priority.NORMAL, NOW);
        QueueItem urgent = item("u", 2, Priority.URGENT, NOW);
        QueueItem normal2 = item("n2", 3, Priority.NORMAL, NOW);
        QueueItem critical = item("c", 4, Priority.CRITICAL, NOW);

        assertThat(sort(new EmergencyPriorityStrategy(), normal1, urgent, normal2, critical))
                .containsExactly(critical, urgent, normal1, normal2);
    }

    @Test
    void agingLetsALongWaitingNormalPatientOvertakeAFreshUrgentOne() {
        AgingPriorityStrategy aging = new AgingPriorityStrategy(properties);
        QueueItem oldNormal = item("old", 1, Priority.NORMAL, NOW.minus(Duration.ofMinutes(20))); // 0 + 20/15 = 1.33
        QueueItem freshUrgent = item("new", 2, Priority.URGENT, NOW);                              // 1 + 0     = 1.0
        QueueItem freshCritical = item("crit", 3, Priority.CRITICAL, NOW);                         // 2 + 0     = 2.0

        assertThat(sort(aging, freshUrgent, oldNormal, freshCritical))
                .containsExactly(freshCritical, oldNormal, freshUrgent);
    }

    @Test
    void factoryResolvesPerDepartmentCaseInsensitivelyWithDefaultFallback() {
        QueueStrategyFactory factory = new QueueStrategyFactory(strategies, properties);

        assertThat(factory.forDepartment("EMERGENCY").name()).isEqualTo("PRIORITY");
        assertThat(factory.forDepartment("Cardiology").name()).isEqualTo("AGING_PRIORITY");
        assertThat(factory.forDepartment("OPD").name()).isEqualTo("FIFO");
    }

    @Test
    void factoryFailsFastOnUnknownStrategyName() {
        QueueProperties bad = new QueueProperties(10, 15,
                new QueueProperties.Strategy("FIFO", Map.of("OPD", "LOTTERY")));

        assertThatThrownBy(() -> new QueueStrategyFactory(strategies, bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LOTTERY");
    }

    private static List<QueueItem> sort(QueuePriorityStrategy strategy, QueueItem... items) {
        List<QueueItem> list = new ArrayList<>(List.of(items));
        list.sort(strategy.comparator(NOW));
        return list;
    }

    private static QueueItem item(String id, int sequence, Priority priority, Instant joinedAt) {
        return new QueueItem(id, "patient-" + id, "Name " + id, "OPD-" + sequence, sequence, TODAY,
                priority, "code-" + id, joinedAt);
    }
}
