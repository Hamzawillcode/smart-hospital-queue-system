package com.hospital.queue.exception;

/** The request is valid but not allowed in the entry's current state. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
