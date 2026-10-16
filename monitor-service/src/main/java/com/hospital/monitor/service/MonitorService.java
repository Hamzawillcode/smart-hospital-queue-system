package com.hospital.monitor.service;

import com.hospital.monitor.document.DeadLetterRecord;
import com.hospital.monitor.document.QueueSnapshot;
import com.hospital.monitor.dto.DashboardResponse;
import com.hospital.monitor.dto.DepartmentStatsResponse;
import com.hospital.monitor.dto.EventLogResponse;

import java.util.List;

public interface MonitorService {

    DashboardResponse getDashboard();

    DepartmentStatsResponse getDepartmentStats(String departmentId);

    List<QueueSnapshot> getSnapshotHistory(String departmentId, int limit);

    List<EventLogResponse> getRecentEvents(int limit);

    List<EventLogResponse> getPatientHistory(String patientId);

    List<DeadLetterRecord> getDeadLetters(DeadLetterRecord.Status status);

    /** Re-publishes a dead letter to its original topic. All consumers are idempotent. */
    DeadLetterRecord replayDeadLetter(String id);
}
