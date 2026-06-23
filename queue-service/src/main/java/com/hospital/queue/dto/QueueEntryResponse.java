package com.hospital.queue.dto;

import com.hospital.queue.model.QueueStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class QueueEntryResponse {
    private String queueId;
    private String patientId;
    private String patientName;
    private String departmentId;
    private String tokenNumber;
    private Integer position;
    private QueueStatus status;
    private String assignedDoctorName;
    private String roomNumber;
    private LocalDateTime joinedAt;
    private int estimatedWaitMinutes;
}
