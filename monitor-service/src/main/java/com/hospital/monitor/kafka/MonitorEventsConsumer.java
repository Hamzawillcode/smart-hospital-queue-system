package com.hospital.monitor.kafka;

import com.hospital.common.events.KafkaTopics;
import com.hospital.monitor.service.EventIngestionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consumes every domain topic. If MongoDB is unavailable, records go through
 * -monitor-retry topics (1s, 2s, 4s) and then to -monitor-dlt, where the
 * DeadLetterCollector picks them up for replay.
 */
@Component
public class MonitorEventsConsumer {

    private static final Logger log = LogManager.getLogger(MonitorEventsConsumer.class);
    private static final Pattern DERIVED_TOPIC = Pattern.compile("^(.*)-monitor-(retry(-\\d+)?|dlt)$");

    private final EventIngestionService ingestion;

    public MonitorEventsConsumer(EventIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            retryTopicSuffix = "-monitor-retry",
            dltTopicSuffix = "-monitor-dlt",
            numPartitions = "${kafka.topics.partitions}",
            replicationFactor = "${kafka.topics.replicas}",
            exclude = IllegalArgumentException.class,
            traversingCauses = "true",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            kafkaTemplate = "kafkaTemplate")
    @KafkaListener(topics = {
            KafkaTopics.PATIENT_CHECKED_IN,
            KafkaTopics.QUEUE_UPDATED,
            KafkaTopics.DOCTOR_REQUESTED,
            KafkaTopics.DOCTOR_ASSIGNED,
            KafkaTopics.DOCTOR_RELEASED,
            KafkaTopics.DOCTOR_STATUS_CHANGED})
    public void onEvent(ConsumerRecord<String, String> record) {
        ingestion.ingest(originalTopic(record.topic()), record.value());
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        log.error("Monitor could not store a record from {}; it is in {}", originalTopic(record.topic()),
                record.topic());
    }

    /** Retry and DLT records keep the original topic name as a prefix. */
    static String originalTopic(String topic) {
        Matcher matcher = DERIVED_TOPIC.matcher(topic);
        return matcher.matches() ? matcher.group(1) : topic;
    }
}
