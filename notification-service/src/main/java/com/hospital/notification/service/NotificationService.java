package com.hospital.notification.service;

import com.hospital.notification.dto.NotificationRequest;

public interface NotificationService {

    // Send position update to a specific patient
    void sendPositionUpdate(NotificationRequest request);

    // Notify patient they are next
    void notifyPatientNext(String tokenNumber,
                           String roomNumber,
                           String doctorName,
                           String departmentId);

    // Notify all patients in a department
    // when queue changes
    void notifyQueueUpdate(String departmentId,
                           int totalWaiting);

    // Send custom message to a patient
    void sendCustomMessage(String tokenNumber,
                           String message);
}
