package com.hospital.queue.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Per-department coordination row, always read with SELECT ... FOR UPDATE before a mutation.
 */
@Entity
@Table(name = "queue_departments")
@Getter
@Setter
@NoArgsConstructor
public class QueueDepartment {

    @Id
    @Column(length = 32)
    private String departmentId;

    /** Incremented on every committed queue change; the in-memory snapshot carries the same number. */
    @Column(nullable = false)
    private long stateVersion;

    /** Queue entry with an outstanding doctor.requested, or null. At most one per department. */
    @Column(length = 36)
    private String pendingRequestId;

    @Column(nullable = false)
    private Instant updatedAt;
}
