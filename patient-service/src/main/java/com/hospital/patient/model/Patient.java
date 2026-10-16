package com.hospital.patient.model;

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
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 10)
    private String phone;

    @Column(nullable = false, length = 32)
    private String department;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 24)
    private PatientStatus status;

    @Column(nullable = false, length = 40)
    private String tokenNumber;

    @Column(nullable = false)
    private int tokenSequence;

    @Column(nullable = false)
    private LocalDate checkinDate;

    @Column(nullable = false)
    private Instant checkinTime;

    /** Random, unguessable code the patient uses to follow their position; never the token number. */
    @Column(nullable = false, unique = true, length = 64)
    private String accessCode;

    private String assignedDoctorName;

    @Column(length = 32)
    private String roomNumber;

    /**
     * Optimistic lock. doctor.assigned and doctor.released are consumed on different
     * threads and an admin can also correct a status over REST; a stale writer fails
     * with ObjectOptimisticLockingFailureException and is retried (see PatientServiceImpl).
     */
    @Version
    private long version;
}
