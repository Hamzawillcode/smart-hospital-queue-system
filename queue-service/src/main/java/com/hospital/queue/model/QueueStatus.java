package com.hospital.queue.model;

public enum QueueStatus {
    /** In the in-memory waiting queue. */
    WAITING,
    /** Called as next patient; a doctor.requested event is outstanding. */
    AWAITING_DOCTOR,
    /** doctor-service reserved a doctor. */
    IN_CONSULTATION,
    DONE,
    LEFT
}
