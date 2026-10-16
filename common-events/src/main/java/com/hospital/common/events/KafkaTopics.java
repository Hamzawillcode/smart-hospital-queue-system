package com.hospital.common.events;

/**
 * Topic names shared by producers and consumers.
 *
 * Every topic is keyed by departmentId, so all events for one department
 * land on the same partition and are consumed in order by a single
 * consumer thread within each consumer group.
 */
public final class KafkaTopics {

    /** patient-service → queue-service, monitor-service */
    public static final String PATIENT_CHECKED_IN = "patient.checked-in";

    /** queue-service → notification-service, monitor-service (compacted: latest snapshot per department) */
    public static final String QUEUE_UPDATED = "queue.updated";

    /** queue-service → doctor-service, monitor-service */
    public static final String DOCTOR_REQUESTED = "doctor.requested";

    /** doctor-service → queue-service, patient-service, monitor-service */
    public static final String DOCTOR_ASSIGNED = "doctor.assigned";

    /** queue-service → doctor-service, patient-service, monitor-service */
    public static final String DOCTOR_RELEASED = "doctor.released";

    /** doctor-service → monitor-service */
    public static final String DOCTOR_STATUS_CHANGED = "doctor.status-changed";

    private KafkaTopics() {
    }
}
