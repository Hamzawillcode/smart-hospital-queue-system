package com.hospital.doctor.exception;

/** The request is valid but not allowed in the doctor's current state. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
