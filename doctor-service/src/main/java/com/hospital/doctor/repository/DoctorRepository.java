package com.hospital.doctor.repository;

import com.hospital.doctor.model.Doctor;
import com.hospital.doctor.model.DoctorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DoctorRepository
        extends JpaRepository<Doctor, String> {

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
