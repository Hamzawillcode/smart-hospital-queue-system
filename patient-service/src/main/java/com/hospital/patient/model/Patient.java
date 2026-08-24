package com.hospital.patient.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Entity
@Table(name = "patients")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    private String phone;

    private String department;

    @Enumerated(EnumType.STRING)
    private PatientStatus status;

    private String tokenNumber;

    private LocalDateTime checkinTime;


    private String assignedDoctorName;

    @Version
    private Long version;
}
