package com.hospital.doctor.model;

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

@Entity
@Table(name = "doctor_requests")
@Getter
@Setter
@NoArgsConstructor
public class DoctorRequest {

    public enum Status { PENDING, ASSIGNED, COMPLETED, CANCELLED }

    @Id
    @Column(length = 36)
    private String requestId;

    @Column(nullable = false, length = 36)
    private String patientId;

    @Column(nullable = false, length = 40)
    private String tokenNumber;

    @Column(nullable = false, length = 32)
    private String departmentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(length = 36)
    private String doctorId;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant assignedAt;

    private Instant closedAt;

    @Version
    private long version;

    public boolean isOpen() {
        return status == Status.PENDING || status == Status.ASSIGNED;
    }
}
