package com.hospital.patient.repository;

import com.hospital.patient.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PatientRepository extends JpaRepository<Patient, String> {

    List<Patient> findByDepartmentAndCheckinDateOrderByTokenSequenceAsc(String department, LocalDate checkinDate);
}
