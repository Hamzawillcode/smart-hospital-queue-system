package com.hospital.queue.service;

import com.hospital.queue.dto.*;
import java.util.List;

public interface QueueService {

    QueueEntryResponse addToQueue(AddToQueueRequest request);

    QueueStatusResponse getQueueStatus(String departmentId);

    QueueEntryResponse getPatientQueueInfo(String patientId,
                                           String departmentId);

    QueueEntryResponse callNextPatient(String departmentId);

    QueueEntryResponse assignDoctor(String patientId,
                                    AssignDoctorRequest request);

    QueueEntryResponse markDone(String patientId,
                                String departmentId);

    boolean removeFromQueue(String patientId,
                            String departmentId);

    List<QueueStatusResponse> getAllQueues();
}
