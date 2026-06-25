package com.hospital.monitor.repository;

import com.hospital.monitor.document.QueueSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface QueueSnapshotRepository
        extends MongoRepository<QueueSnapshot, String> {

    Optional<QueueSnapshot> findTopByDepartmentIdOrderByRecordedAtDesc(
            String departmentId);

    List<QueueSnapshot> findByDepartmentIdOrderByRecordedAtDesc(
            String departmentId);
}
