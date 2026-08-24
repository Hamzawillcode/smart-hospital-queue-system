package com.hospital.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorAssignedEvent {

    private String eventId;
    private String patientId;
    private String patientToken;
    private String doctorId;
    private String doctorName;
    private String departmentId;
    private String roomNumber;
    private LocalDateTime assignedAt;
    private LocalDateTime eventTimestamp;

    public static DoctorAssignedEvent of(
            String patientId,
            String patientToken,
            String doctorId,
            String doctorName,
            String departmentId,
            String roomNumber) {

        DoctorAssignedEvent event = new DoctorAssignedEvent();
        event.setEventId(java.util.UUID
                .randomUUID().toString());
        event.setPatientId(patientId);
        event.setPatientToken(patientToken);
        event.setDoctorId(doctorId);
        event.setDoctorName(doctorName);
        event.setDepartmentId(departmentId);
        event.setRoomNumber(roomNumber);
        event.setAssignedAt(LocalDateTime.now());
        event.setEventTimestamp(LocalDateTime.now());
        return event;
    }
}
