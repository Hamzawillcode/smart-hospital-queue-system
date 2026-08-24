package com.hospital.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientCheckedInEvent {

    private String eventId;        // unique event ID
    private String patientId;
    private String patientName;
    private String phone;
    private String departmentId;
    private String tokenNumber;
    private LocalDateTime checkinTime;
    private LocalDateTime eventTimestamp;

    // Factory method for easy creation
    public static PatientCheckedInEvent of(
            String patientId,
            String patientName,
            String phone,
            String departmentId,
            String tokenNumber) {

        PatientCheckedInEvent event =
                new PatientCheckedInEvent();
        event.setEventId(java.util.UUID
                .randomUUID().toString());
        event.setPatientId(patientId);
        event.setPatientName(patientName);
        event.setPhone(phone);
        event.setDepartmentId(departmentId);
        event.setTokenNumber(tokenNumber);
        event.setCheckinTime(LocalDateTime.now());
        event.setEventTimestamp(LocalDateTime.now());
        return event;
    }
}