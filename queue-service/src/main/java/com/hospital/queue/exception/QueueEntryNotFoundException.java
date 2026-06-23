package com.hospital.queue.exception;

public class QueueEntryNotFoundException
        extends RuntimeException {
    public QueueEntryNotFoundException(String id) {
        super("Queue entry not found: " + id);
    }
}
