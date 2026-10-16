package com.hospital.monitor.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A record that exhausted its retries in some service and landed in a *-dlt topic.
 * id = "{dltTopic}:{partition}:{offset}" makes collection idempotent.
 */
@Document(collection = "dead_letters")
public record DeadLetterRecord(
        @Id String id,
        String dltTopic,
        String originalTopic,
        String messageKey,
        String payload,
        String exceptionMessage,
        @Indexed Status status,
        Instant receivedAt,
        Instant replayedAt) {

    public enum Status { NEW, REPLAYED }

    public DeadLetterRecord replayed(Instant at) {
        return new DeadLetterRecord(id, dltTopic, originalTopic, messageKey, payload, exceptionMessage,
                Status.REPLAYED, receivedAt, at);
    }
}
