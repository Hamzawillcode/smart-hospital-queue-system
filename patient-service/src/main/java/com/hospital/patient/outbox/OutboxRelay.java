package com.hospital.patient.outbox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polls the outbox and publishes pending events to Kafka in id order.
 *
 * Delivery is at-least-once: if the process dies after Kafka acknowledged a record
 * but before the row is marked published, the record is sent again. Every consumer
 * is idempotent for that reason.
 *
 * On the first failure the batch stops, so later events for the same key are never
 * published ahead of an earlier one.
 */
@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LogManager.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int retentionDays;

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       TransactionTemplate transactionTemplate,
                       Clock clock,
                       @Value("${outbox.retention-days:7}") int retentionDays) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:200}")
    public void publishPending() {
        Integer published;
        do {
            published = transactionTemplate.execute(status -> publishBatch());
        } while (published != null && published == BATCH_SIZE);
    }

    private int publishBatch() {
        List<OutboxEvent> batch = repository.lockNextBatch(PageRequest.of(0, BATCH_SIZE));
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                event.markPublished(Instant.now(clock));
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.markFailed("interrupted");
                break;
            } catch (ExecutionException | TimeoutException | RuntimeException e) {
                event.markFailed(e.toString());
                log.warn("Outbox publish failed for event {} to {} (attempt {}): {}",
                        event.getEventId(), event.getTopic(), event.getAttempts(), e.toString());
                break;
            }
        }
        return published;
    }

    @Scheduled(cron = "${outbox.cleanup-cron:0 0 3 * * *}")
    public void deleteOldPublishedEvents() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(retentionDays));
        Integer deleted = transactionTemplate.execute(status -> repository.deletePublishedBefore(cutoff));
        log.info("Outbox cleanup removed {} published events older than {}", deleted, cutoff);
    }
}
