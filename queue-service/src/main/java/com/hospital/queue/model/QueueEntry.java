package com.hospital.queue.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "queue_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String queueId;

    @Column(nullable = false)
    private String patientId;

    @Column(nullable = false)
    private String patientName;

    @Column(nullable = false)
    private String departmentId;

    private String tokenNumber;

    private Integer position;

    @Enumerated(EnumType.STRING)
    private QueueStatus status;

    private String assignedDoctorId;

    private String assignedDoctorName;

    private String roomNumber;

    private LocalDateTime joinedAt;

    private LocalDateTime assignedAt;

    @Version
    private Long version;
}
