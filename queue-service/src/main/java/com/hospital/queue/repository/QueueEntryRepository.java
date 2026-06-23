package com.hospital.queue.repository;

import com.hospital.queue.model.QueueEntry;
import com.hospital.queue.model.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface QueueEntryRepository
        extends JpaRepository<QueueEntry, String> {

    // All entries for a department ordered by position
    List<QueueEntry> findByDepartmentIdOrderByPositionAsc(
            String departmentId);

    // All waiting entries in a department
    List<QueueEntry> findByDepartmentIdAndStatusOrderByPositionAsc(
            String departmentId, QueueStatus status);

    // Check if patient already in queue
    Optional<QueueEntry> findByPatientIdAndStatus(
            String patientId, QueueStatus status);

    // Count waiting in department
    long countByDepartmentIdAndStatus(
            String departmentId, QueueStatus status);

    // Find by patient ID
    List<QueueEntry> findByPatientId(String patientId);

    // Find first in queue (position 1)
    @Query("SELECT q FROM QueueEntry q WHERE " +
            "q.departmentId = :deptId AND " +
            "q.status = 'WAITING' AND " +
            "q.position = 1")
    Optional<QueueEntry> findFirstInQueue(
            @Param("deptId") String deptId);

    // Max position in department
    @Query("SELECT COALESCE(MAX(q.position), 0) " +
            "FROM QueueEntry q WHERE " +
            "q.departmentId = :deptId AND " +
            "q.status = 'WAITING'")
    int findMaxPosition(@Param("deptId") String deptId);
}
