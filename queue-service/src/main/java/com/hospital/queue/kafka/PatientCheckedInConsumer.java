package com.hospital.queue.kafka;

import com.hospital.common.events.PatientCheckedInEvent;
import com.hospital.queue.dto.AddToQueueRequest;
import com.hospital.queue.service.QueueService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class PatientCheckedInConsumer {

    private static final Logger log =
            LogManager.getLogger(
                    PatientCheckedInConsumer.class);

    @Autowired
    private QueueService queueService;

    @KafkaListener(
            topics = "patient.checked-in",
            groupId = "queue-service-group",
            containerFactory = "patientCheckedInListenerFactory"
    )
    // Retry 3 times before sending to DLT
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            autoCreateTopics = "true"
            // Creates patient.checked-in-dlt on failure
    )
    public void onPatientCheckedIn(
            PatientCheckedInEvent event) {

        log.info("Received patient.checked-in event: " +
                        "patient={} dept={} token={}",
                event.getPatientId(),
                event.getDepartmentId(),
                event.getTokenNumber());

        try {
            // Build request from Kafka event
            AddToQueueRequest request = new AddToQueueRequest();
            request.setPatientId(event.getPatientId());
            request.setPatientName(event.getPatientName());
            request.setDepartmentId(event.getDepartmentId());
            request.setTokenNumber(event.getTokenNumber());

            // Add to queue — same logic as before
            // but now triggered by Kafka instead of REST
            queueService.addToQueue(request);

            log.info("Patient {} added to queue via " +
                    "Kafka event", event.getPatientId());

        } catch (Exception e) {
            log.error("Error processing patient.checked-in " +
                            "event for patient {}: {}",
                    event.getPatientId(), e.getMessage());
            // Re-throw so @RetryableTopic can retry
            throw e;
        }
    }
}
