package com.hospital.doctor.exception;

public class DoctorNotFoundException
        extends RuntimeException {
    public DoctorNotFoundException(String doctorId) {
        super("Doctor not found with id: " + doctorId);
    }
}
