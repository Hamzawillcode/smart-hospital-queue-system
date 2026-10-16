package com.hospital.queue.exception;

public class QueueEntryNotFoundException extends RuntimeException {
    public QueueEntryNotFoundException(String message) {
        super(message);
    }
}
