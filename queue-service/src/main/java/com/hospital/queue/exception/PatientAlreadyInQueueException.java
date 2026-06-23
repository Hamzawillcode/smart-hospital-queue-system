package com.hospital.queue.exception;

public class PatientAlreadyInQueueException
        extends RuntimeException {
    public PatientAlreadyInQueueException(String patientId) {
        super("Patient already in queue: " + patientId);
    }
}
