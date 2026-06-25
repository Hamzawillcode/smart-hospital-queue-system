package com.hospital.monitor.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "event_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventLog {

    @Id
    private String id;

    private String eventType;

    // Which service generated this event
    private String sourceService;

    // The actual event data as a string
    private String payload;

    private String departmentId;

    private String patientId;

    private LocalDateTime recordedAt;
}
