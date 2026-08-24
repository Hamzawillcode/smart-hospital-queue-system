package com.hospital.queue.kafka;

import com.hospital.common.events.DoctorAssignedEvent;
import com.hospital.common.events.QueueUpdatedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class QueueEventProducer {

    private static final Logger log =
            LogManager.getLogger(QueueEventProducer.class);

    @Autowired
    private KafkaTemplate<String, QueueUpdatedEvent>
            queueUpdatedTemplate;

    @Autowired
    private KafkaTemplate<String, DoctorAssignedEvent>
            doctorAssignedTemplate;

    @Value("${kafka.topics.queue-updated}")
    private String queueUpdatedTopic;

    @Value("${kafka.topics.doctor-assigned}")
    private String doctorAssignedTopic;

    public void publishQueueUpdated(
            String departmentId,
            int totalWaiting,
            String firstPatientId,
            String firstPatientToken,
            List<QueueUpdatedEvent.QueueEntrySnapshot>
                    snapshots) {

        QueueUpdatedEvent event = QueueUpdatedEvent.of(
                departmentId,
                totalWaiting,
                firstPatientId,
                firstPatientToken,
                snapshots
        );

        log.info("Publishing queue.updated: dept={} " +
                        "waiting={} firstPatient={}",
                departmentId, totalWaiting, firstPatientId);

        queueUpdatedTemplate.send(
                queueUpdatedTopic,
                departmentId,  // key = department
                event
        ).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("queue.updated published. " +
                                "Partition: {} Offset: {}",
                        result.getRecordMetadata()
                                .partition(),
                        result.getRecordMetadata()
                                .offset());
            } else {
                log.error("Failed to publish queue.updated: {}",
                        ex.getMessage());
            }
        });
    }

    public void publishDoctorAssigned(
            String patientId,
            String patientToken,
            String doctorId,
            String doctorName,
            String departmentId,
            String roomNumber) {

        DoctorAssignedEvent event = DoctorAssignedEvent.of(
                patientId, patientToken,
                doctorId, doctorName,
                departmentId, roomNumber
        );

        log.info("Publishing doctor.assigned: " +
                        "patient={} doctor={} room={}",
                patientId, doctorName, roomNumber);

        doctorAssignedTemplate.send(
                doctorAssignedTopic,
                patientId,  // key = patient
                event
        );
    }
}