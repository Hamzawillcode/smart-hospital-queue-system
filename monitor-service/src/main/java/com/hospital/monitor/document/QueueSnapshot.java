package com.hospital.monitor.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "queue_snapshots")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueueSnapshot {

    @Id
    private String id;

    private String departmentId;

    private int totalWaiting;

    private int totalAssigned;

    private double avgWaitMinutes;

    // List of patient names in queue
    // Try doing this in MySQL — nightmare
    // In MongoDB — trivial
    private List<String> waitingPatients;

    private LocalDateTime recordedAt;
}
