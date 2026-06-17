package com.hospital.patient.repository;

import com.hospital.patient.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department,String> {
    
}
