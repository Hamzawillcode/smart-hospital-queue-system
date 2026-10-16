package com.hospital.doctor.model;

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

@Entity
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor
public class Doctor {

    @Id
    @Column(length = 36)
    private String doctorId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 32)
    private String departmentId;

    private String specialization;

    @Column(nullable = false, length = 32)
    private String roomNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private DoctorStatus status;

    /** The request this doctor is serving while BUSY; used to make releases idempotent. */
    @Column(length = 36)
    private String currentRequestId;

    /** Fairness: the least recently assigned available doctor is picked first. */
    private Instant lastAssignedAt;

    /**
     * Optimistic lock. Assignment takes a pessimistic row lock, but manual status
     * changes (break, off duty) read without locking; @Version makes a stale manual
     * update fail instead of overwriting an assignment that committed in between.
     */
    @Version
    private long version;
}
