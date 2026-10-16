package com.hospital.monitor.repository;

import com.hospital.monitor.document.DoctorActivityLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DoctorActivityLogRepository extends MongoRepository<DoctorActivityLog, String> {

    List<DoctorActivityLog> findByDoctorIdOrderByOccurredAtDesc(String doctorId);
}
