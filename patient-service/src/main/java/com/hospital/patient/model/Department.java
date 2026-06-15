package com.hospital.patient.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "departments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Department {

    @Id
    private String deptId;       // e.g. "OPD", "EMERGENCY", "CARDIOLOGY"

    @Column(nullable = false)
    private String name;

    private int currentQueueSize;
}
