package com.hospital.doctor.repository;

import com.hospital.doctor.model.Doctor;
import com.hospital.doctor.model.DoctorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface DoctorRepository
        extends JpaRepository<Doctor, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Doctor d WHERE " +
            "d.departmentId = :deptId AND " +
            "d.status = 'AVAILABLE' ORDER BY d.name ASC")
    Optional<Doctor> findFirstAvailableWithLock(
            @Param("deptId") String deptId);

    // All doctors in a department
    List<Doctor> findByDepartmentId(String departmentId);

    // Available doctors in a department
    List<Doctor> findByDepartmentIdAndStatus(
            String departmentId, DoctorStatus status);

    // First available doctor in a department
    Optional<Doctor> findFirstByDepartmentIdAndStatus(
            String departmentId, DoctorStatus status);

    // All doctors with a specific status
    List<Doctor> findByStatus(DoctorStatus status);
}
