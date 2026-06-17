package com.hospital.patient.exception;

public class PatientNotFoundException extends RuntimeException{
    public PatientNotFoundException(String patientId) {
        super("Patient not found with id: " + patientId);
    }
}
