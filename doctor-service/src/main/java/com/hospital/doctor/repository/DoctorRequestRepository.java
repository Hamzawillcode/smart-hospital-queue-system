package com.hospital.doctor.repository;

import com.hospital.doctor.model.DoctorRequest;
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

public interface DoctorRequestRepository extends JpaRepository<DoctorRequest, String> {

    /** Oldest request in the given status, FOR UPDATE SKIP LOCKED. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select r from DoctorRequest r where r.departmentId = :departmentId and r.status = :status "
            + "order by r.requestedAt asc")
    List<DoctorRequest> lockOldest(@Param("departmentId") String departmentId,
                                   @Param("status") DoctorRequest.Status status,
                                   Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select r from DoctorRequest r where r.requestId = :requestId")
    Optional<DoctorRequest> findForUpdate(@Param("requestId") String requestId);

    @Query("select distinct r.departmentId from DoctorRequest r where r.status = :status")
    List<String> findDepartmentsWithStatus(@Param("status") DoctorRequest.Status status);

    List<DoctorRequest> findByDepartmentIdAndStatusOrderByRequestedAtAsc(String departmentId,
                                                                        DoctorRequest.Status status);
}
