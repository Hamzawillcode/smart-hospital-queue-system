package com.hospital.queue.kafka;

import com.hospital.common.events.KafkaTopics;
import com.hospital.common.events.PatientCheckedInEvent;
import com.hospital.queue.service.QueueService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Failed records move through retry topics with exponential backoff (1s, 2s, 4s)
 * and then to {@code patient.checked-in-queue-dlt}. The suffixes include this
 * service's name so another service's failures on the same topic never land in our
 * retry topics. monitor-service collects every *-dlt topic and can replay records.
 */
@Component
public class PatientCheckedInConsumer {

    private static final Logger log = LogManager.getLogger(PatientCheckedInConsumer.class);

    private final QueueService queueService;

    public PatientCheckedInConsumer(QueueService queueService) {
        this.queueService = queueService;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            retryTopicSuffix = "-queue-retry",
            dltTopicSuffix = "-queue-dlt",
            numPartitions = "${kafka.topics.partitions}",
            replicationFactor = "${kafka.topics.replicas}",
            exclude = IllegalArgumentException.class,
            traversingCauses = "true",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            kafkaTemplate = "kafkaTemplate")
    @KafkaListener(topics = KafkaTopics.PATIENT_CHECKED_IN)
    public void onPatientCheckedIn(PatientCheckedInEvent event) {
        queueService.enqueue(event);
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        Header error = record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        log.error("patient.checked-in moved to {}: {} payload={}", record.topic(),
                error == null ? "unknown error" : new String(error.value(), StandardCharsets.UTF_8), record.value());
    }
}
