package com.hospital.doctor.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "doctors")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String doctorId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String departmentId;

    private String specialization;

    private String roomNumber;

    @Enumerated(EnumType.STRING)
    private DoctorStatus status;

    @Version
    private Long version;
}
