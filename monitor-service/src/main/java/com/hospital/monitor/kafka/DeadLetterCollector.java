package com.hospital.monitor.kafka;

import com.hospital.monitor.document.DeadLetterRecord;
import com.hospital.monitor.repository.DeadLetterRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

/**
 * Subscribes to every *-dlt topic of every service (topic pattern) and stores the
 * records in MongoDB, so operators can see and replay them from the dashboard API
 * instead of digging through Kafka. New DLT topics are discovered within ~10s.
 */
@Component
public class DeadLetterCollector {

    private static final Logger log = LogManager.getLogger(DeadLetterCollector.class);

    private final DeadLetterRepository deadLetters;
    private final Clock clock;

    public DeadLetterCollector(DeadLetterRepository deadLetters, Clock clock) {
        this.deadLetters = deadLetters;
        this.clock = clock;
    }

    @KafkaListener(
            topicPattern = ".*-dlt",
            groupId = "monitor-dead-letters",
            containerFactory = "deadLetterContainerFactory",
            properties = "metadata.max.age.ms=10000")
    public void collect(ConsumerRecord<String, String> record) {
        String id = record.topic() + ":" + record.partition() + ":" + record.offset();
        if (deadLetters.existsById(id)) {
            return;
        }
        String originalTopic = header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        if (originalTopic == null) {
            originalTopic = record.topic().replaceFirst("-[a-z]+-dlt$", "");
        }
        deadLetters.save(new DeadLetterRecord(id, record.topic(), originalTopic, record.key(), record.value(),
                header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE), DeadLetterRecord.Status.NEW,
                Instant.now(clock), null));
        log.warn("Dead letter stored: {} (original topic {})", id, originalTopic);
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
