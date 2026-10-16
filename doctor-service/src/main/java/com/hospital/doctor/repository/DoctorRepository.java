package com.hospital.doctor.repository;

import com.hospital.doctor.model.Doctor;
import com.hospital.doctor.model.DoctorStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, String> {

    /**
     * Atomic "find first available doctor":
     * {@code SELECT ... WHERE status='AVAILABLE' ORDER BY last_assigned_at LIMIT 1 FOR UPDATE SKIP LOCKED}.
     *
     * The LIMIT (via Pageable) is what fixes the old {@code Optional} query that threw
     * when more than one doctor was available. SKIP LOCKED lets concurrent assigners
     * take different doctors instead of queueing behind the same row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select d from Doctor d where d.departmentId = :departmentId and d.status = :status "
            + "order by d.lastAssignedAt asc nulls first, d.name asc")
    List<Doctor> lockAvailable(@Param("departmentId") String departmentId,
                               @Param("status") DoctorStatus status,
                               Pageable pageable);

    /** Plain blocking row lock for releasing a specific doctor. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select d from Doctor d where d.doctorId = :doctorId")
    Optional<Doctor> findForUpdate(@Param("doctorId") String doctorId);

    List<Doctor> findByDepartmentIdOrderByNameAsc(String departmentId);

    List<Doctor> findByDepartmentIdAndStatusOrderByNameAsc(String departmentId, DoctorStatus status);
}
