package com.hospital.monitor.repository;

import com.hospital.monitor.document.DoctorStatusView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DoctorStatusViewRepository extends MongoRepository<DoctorStatusView, String> {

    List<DoctorStatusView> findByDepartmentIdOrderByDoctorNameAsc(String departmentId);
}
