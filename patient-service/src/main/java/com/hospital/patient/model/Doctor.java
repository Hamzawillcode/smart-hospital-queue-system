package com.hospital.patient.model;

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

    private String departmentId;

    private String specialization;

    private boolean isAvailable;

    private String roomNumber;
}
