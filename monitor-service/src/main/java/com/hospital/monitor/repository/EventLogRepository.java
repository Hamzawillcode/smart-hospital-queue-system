package com.hospital.monitor.repository;

import com.hospital.monitor.document.EventLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface EventLogRepository
        extends MongoRepository<EventLog, String> {

    // Find recent events ordered by time
    List<EventLog> findTop10ByOrderByRecordedAtDesc();

    // Find events by type
    List<EventLog> findByEventTypeOrderByRecordedAtDesc(
            String eventType);

    // Find events for a department
    List<EventLog> findByDepartmentIdOrderByRecordedAtDesc(
            String departmentId);

    // Find events for a patient
    List<EventLog> findByPatientIdOrderByRecordedAtDesc(
            String patientId);

    // Count events after a time (for "today" stats)
    long countByRecordedAtAfter(LocalDateTime after);

    // Find events after a time
    List<EventLog> findByRecordedAtAfterOrderByRecordedAtDesc(
            LocalDateTime after);
}
