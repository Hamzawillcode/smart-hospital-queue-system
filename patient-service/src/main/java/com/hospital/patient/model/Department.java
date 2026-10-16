package com.hospital.patient.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Department plus its daily token counter. Only ever modified while holding
 * the row lock (see DepartmentRepository#findByIdForUpdate), so it needs no @Version.
 */
@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
public class Department {

    @Id
    @Column(length = 32)
    private String deptId;

    @Column(nullable = false)
    private String name;

    /** Day the counter belongs to; a check-in on a later day restarts numbering at 1. */
    private LocalDate tokenDate;

    @Column(nullable = false)
    private int lastTokenNumber;

    /** Allocates the next token for {@code today}. Caller must hold the row lock. */
    public int nextToken(LocalDate today) {
        if (!today.equals(tokenDate)) {
            tokenDate = today;
            lastTokenNumber = 0;
        }
        return ++lastTokenNumber;
    }
}
