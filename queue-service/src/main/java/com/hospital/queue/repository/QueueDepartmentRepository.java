package com.hospital.queue.repository;

import com.hospital.queue.model.QueueDepartment;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QueueDepartmentRepository extends JpaRepository<QueueDepartment, String> {

    /** Creates the coordination row the first time a department is seen. Safe under concurrency. */
    @Modifying
    @Query(value = "INSERT IGNORE INTO queue_departments (department_id, state_version, updated_at) "
            + "VALUES (:departmentId, 0, CURRENT_TIMESTAMP(6))", nativeQuery = true)
    int insertIfMissing(@Param("departmentId") String departmentId);

    /** SELECT ... FOR UPDATE; waits up to 5s, then fails with a lock timeout (retried by Kafka). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select d from QueueDepartment d where d.departmentId = :departmentId")
    Optional<QueueDepartment> findForUpdate(@Param("departmentId") String departmentId);

    @Query("select d.stateVersion from QueueDepartment d where d.departmentId = :departmentId")
    Optional<Long> findStateVersion(@Param("departmentId") String departmentId);
}
