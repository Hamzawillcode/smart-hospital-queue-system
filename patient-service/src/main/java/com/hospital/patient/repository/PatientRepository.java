package com.hospital.patient.repository;

import com.hospital.patient.model.Patient;
import com.hospital.patient.model.PatientStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientRepository extends JpaRepository<Patient,String> {
    // Spring Data JPA generates the SQL for these automatically
    // based on method names — no implementation needed!

    List<Patient> findByDepartment(String department);

    List<Patient> findByDepartmentAndStatus(String department, PatientStatus status);

    long countByDepartmentAndStatus(String department, PatientStatus status);
}
