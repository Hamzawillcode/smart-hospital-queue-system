package com.hospital.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class QueueStatusResponse {
    private String departmentId;
    private int totalWaiting;
    private int totalAssigned;
    private List<QueueEntryResponse> queue;
}
