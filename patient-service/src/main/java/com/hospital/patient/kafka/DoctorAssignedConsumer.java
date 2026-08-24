package com.hospital.patient.kafka;

import com.hospital.common.events.DoctorAssignedEvent;
import com.hospital.patient.model.PatientStatus;
import com.hospital.patient.repository.PatientRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DoctorAssignedConsumer {

    private static final Logger log =
            LogManager.getLogger(DoctorAssignedConsumer.class);

    @Autowired
    private PatientRepository patientRepository;

    @KafkaListener(
            topics = "doctor.assigned",
            groupId = "patient-service-group",
            containerFactory = "doctorAssignedListenerFactory"
    )
    @Transactional
    public void onDoctorAssigned(DoctorAssignedEvent event) {

        log.info("Received doctor.assigned event: " +
                        "patient={} doctor={} room={}",
                event.getPatientId(),
                event.getDoctorName(),
                event.getRoomNumber());

        patientRepository.findById(event.getPatientId())
                .ifPresent(patient -> {
                    patient.setStatus(PatientStatus.ASSIGNED);
                    patient.setAssignedDoctorName(
                            event.getDoctorName());
                    patientRepository.save(patient);

                    log.info("Patient {} status updated to " +
                                    "ASSIGNED with Dr. {}",
                            event.getPatientId(),
                            event.getDoctorName());
                });
    }
}