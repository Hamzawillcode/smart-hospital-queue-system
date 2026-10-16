package com.hospital.patient.repository;

import com.hospital.patient.model.Department;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, String> {

    /**
     * SELECT ... FOR UPDATE on the department row. Concurrent check-ins for the same
     * department queue here; a waiter gives up after 3s with a lock-timeout exception,
     * which the service retries.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select d from Department d where d.deptId = :deptId")
    Optional<Department> findByIdForUpdate(@Param("deptId") String deptId);
}
