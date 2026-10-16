package com.hospital.monitor.repository;

import com.hospital.monitor.document.DeadLetterRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DeadLetterRepository extends MongoRepository<DeadLetterRecord, String> {

    List<DeadLetterRecord> findByStatusOrderByReceivedAtDesc(DeadLetterRecord.Status status);

    long countByStatus(DeadLetterRecord.Status status);
}
