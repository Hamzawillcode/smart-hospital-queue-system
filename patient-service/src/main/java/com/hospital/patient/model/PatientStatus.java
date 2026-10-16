package com.hospital.patient.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * A patient's visit only ever moves forward. Status updates arrive from two Kafka topics,
 * possibly out of order (retry topics reorder messages), so an event that would move
 * a patient backwards, e.g. doctor.assigned arriving after the visit completed, is ignored.
 */
public enum PatientStatus {
    CHECKED_IN,
    IN_CONSULTATION,
    DONE,
    LEFT;

    public Set<PatientStatus> allowedNext() {
        return switch (this) {
            case CHECKED_IN -> EnumSet.of(IN_CONSULTATION, DONE, LEFT);
            case IN_CONSULTATION -> EnumSet.of(DONE, LEFT);
            case DONE, LEFT -> EnumSet.noneOf(PatientStatus.class);
        };
    }

    public boolean canMoveTo(PatientStatus next) {
        return allowedNext().contains(next);
    }
}
