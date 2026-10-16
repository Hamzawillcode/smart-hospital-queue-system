package com.hospital.doctor.kafka;

import com.hospital.common.events.DoctorRequestedEvent;
import com.hospital.common.events.KafkaTopics;
import com.hospital.doctor.service.AssignmentService;
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

@Component
public class DoctorRequestedConsumer {

    private static final Logger log = LogManager.getLogger(DoctorRequestedConsumer.class);

    private final AssignmentService assignmentService;

    public DoctorRequestedConsumer(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            retryTopicSuffix = "-doctor-retry",
            dltTopicSuffix = "-doctor-dlt",
            numPartitions = "${kafka.topics.partitions}",
            replicationFactor = "${kafka.topics.replicas}",
            exclude = IllegalArgumentException.class,
            traversingCauses = "true",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            kafkaTemplate = "kafkaTemplate")
    @KafkaListener(topics = KafkaTopics.DOCTOR_REQUESTED)
    public void onDoctorRequested(DoctorRequestedEvent event) {
        assignmentService.onDoctorRequested(event);
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        Header error = record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        log.error("doctor.requested moved to {}: {} payload={}", record.topic(),
                error == null ? "unknown error" : new String(error.value(), StandardCharsets.UTF_8), record.value());
    }
}
