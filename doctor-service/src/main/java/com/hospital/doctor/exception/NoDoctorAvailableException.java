package com.hospital.doctor.exception;

public class NoDoctorAvailableException
        extends RuntimeException {
    public NoDoctorAvailableException(String deptId) {
        super("No doctor available in department: "
                + deptId);
    }
}
