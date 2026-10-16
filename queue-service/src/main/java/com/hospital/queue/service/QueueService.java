package com.hospital.queue.service;

import com.hospital.common.events.DoctorAssignedEvent;
import com.hospital.common.events.PatientCheckedInEvent;
import com.hospital.queue.dto.QueueEntryResponse;
import com.hospital.queue.dto.QueueStatusResponse;

import java.util.List;

public interface QueueService {

    /** Kafka: patient.checked-in. Idempotent. */
    void enqueue(PatientCheckedInEvent event);

    /** Kafka: doctor.assigned. Idempotent. */
    void onDoctorAssigned(DoctorAssignedEvent event);

    QueueEntryResponse completeConsultation(String patientId);

    QueueEntryResponse leaveQueue(String patientId);

    /** Calls the next patient if no doctor request is outstanding, or re-sends the outstanding one. */
    QueueStatusResponse dispatchNext(String departmentId);

    QueueStatusResponse getQueueStatus(String departmentId);

    QueueEntryResponse getPatientQueueInfo(String patientId);

    List<QueueStatusResponse> getAllQueues();
}
