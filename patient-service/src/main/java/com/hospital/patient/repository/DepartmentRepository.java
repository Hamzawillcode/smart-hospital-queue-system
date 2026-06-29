package com.hospital.patient.repository;

import com.hospital.patient.model.Department;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository
        extends JpaRepository<Department, String> {

    // PESSIMISTIC WRITE LOCK on department row
    // Only ONE thread can hold this at a time
    // Other threads WAIT until lock released
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Department d " +
            "WHERE d.deptId = :deptId")
    java.util.Optional<Department> findByIdWithLock(
            @Param("deptId") String deptId);

    // Atomic increment — single SQL UPDATE
    @Modifying
    @Query("UPDATE Department d SET " +
            "d.currentQueueSize = d.currentQueueSize + 1 " +
            "WHERE d.deptId = :deptId")
    void incrementQueueSize(@Param("deptId") String deptId);
}
