package com.hospital.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueueUpdatedEvent {

    private String eventId;
    private String departmentId;
    private int totalWaiting;
    private String firstPatientId;     // patient at position 1
    private String firstPatientToken;  // their token number
    private List<QueueEntrySnapshot> queueSnapshot;
    private LocalDateTime eventTimestamp;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueEntrySnapshot {
        private String patientId;
        private String patientName;
        private String tokenNumber;
        private int position;
        private int estimatedWaitMinutes;
    }

    public static QueueUpdatedEvent of(
            String departmentId,
            int totalWaiting,
            String firstPatientId,
            String firstPatientToken,
            List<QueueEntrySnapshot> snapshot) {

        QueueUpdatedEvent event = new QueueUpdatedEvent();
        event.setEventId(java.util.UUID
                .randomUUID().toString());
        event.setDepartmentId(departmentId);
        event.setTotalWaiting(totalWaiting);
        event.setFirstPatientId(firstPatientId);
        event.setFirstPatientToken(firstPatientToken);
        event.setQueueSnapshot(snapshot);
        event.setEventTimestamp(LocalDateTime.now());
        return event;
    }
}
