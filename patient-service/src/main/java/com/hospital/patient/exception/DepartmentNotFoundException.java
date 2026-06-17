package com.hospital.patient.exception;

public class DepartmentNotFoundException extends RuntimeException{
    public DepartmentNotFoundException(String deptId) {
        super("Department not found with id: " + deptId);
    }
}
