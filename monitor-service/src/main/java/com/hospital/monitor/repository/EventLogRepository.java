package com.hospital.monitor.repository;

import com.hospital.monitor.document.EventLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface EventLogRepository extends MongoRepository<EventLog, String> {

    List<EventLog> findByOrderByOccurredAtDesc(Pageable pageable);

    List<EventLog> findByPatientIdOrderByOccurredAtAsc(String patientId);

    long countByEventTypeAndOccurredAtAfter(String eventType, Instant after);

    long countByEventTypeAndDepartmentIdAndOccurredAtAfter(String eventType, String departmentId, Instant after);

    long countByEventTypeAndDepartmentIdAndOutcomeAndOccurredAtAfter(String eventType, String departmentId,
                                                                      String outcome, Instant after);

    long countByEventTypeAndOutcomeAndOccurredAtAfter(String eventType, String outcome, Instant after);
}
