package com.hospital.queue.repository;

import com.hospital.queue.model.QueueEntry;
import com.hospital.queue.model.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, String> {

    boolean existsByPatientId(String patientId);

    Optional<QueueEntry> findByPatientId(String patientId);

    List<QueueEntry> findByDepartmentIdAndStatus(String departmentId, QueueStatus status);

    List<QueueEntry> findByDepartmentIdAndStatusInOrderByCalledAtAsc(String departmentId,
                                                                    Collection<QueueStatus> statuses);
}
