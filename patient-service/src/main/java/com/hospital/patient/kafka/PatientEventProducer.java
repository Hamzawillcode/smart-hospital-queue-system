package com.hospital.patient.kafka;

import com.hospital.common.events.PatientCheckedInEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;

@Component
public class PatientEventProducer {

    private static final Logger log =
            LogManager.getLogger(PatientEventProducer.class);

    @Autowired
    private KafkaTemplate<String, PatientCheckedInEvent>
            kafkaTemplate;

    @Value("${kafka.topics.patient-checked-in}")
    private String patientCheckedInTopic;

    public void publishPatientCheckedIn(
            PatientCheckedInEvent event) {

        log.info("Publishing patient.checked-in event: " +
                        "patient={} dept={}",
                event.getPatientId(),
                event.getDepartmentId());

        // Use patientId as message key
        // All events for same patient go to same partition
        // Ensures ordering for a single patient's events
        CompletableFuture<SendResult<String,
                PatientCheckedInEvent>> future =
                kafkaTemplate.send(
                        patientCheckedInTopic,
                        event.getPatientId(),  // key
                        event                  // value
                );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Event published successfully. " +
                                "Topic: {} Partition: {} Offset: {}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish event: {}",
                        ex.getMessage());
                // TODO: Add to retry queue or DLT
                // This will be handled on Day 21
            }
        });
    }
}