package com.hospital.queue.model;

import com.hospital.common.events.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "queue_entries")
@Getter
@Setter
@NoArgsConstructor
public class QueueEntry {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 36)
    private String patientId;

    @Column(nullable = false)
    private String patientName;

    @Column(nullable = false, length = 32)
    private String departmentId;

    @Column(nullable = false, length = 40)
    private String tokenNumber;

    @Column(nullable = false)
    private int tokenSequence;

    @Column(nullable = false)
    private LocalDate checkinDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Priority priority;

    @Column(nullable = false, length = 64)
    private String accessCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 24)
    private QueueStatus status;

    @Column(length = 36)
    private String doctorId;

    private String doctorName;

    @Column(length = 32)
    private String roomNumber;

    @Column(nullable = false)
    private Instant joinedAt;

    private Instant calledAt;

    private Instant assignedAt;

    private Instant completedAt;

    /**
     * Optimistic lock. All writes to an entry already happen under the department
     * row lock, so this is a safety net: an unexpected concurrent writer fails
     * instead of silently overwriting.
     */
    @Version
    private long version;
}
