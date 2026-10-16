package com.hospital.patient.exception;

import com.hospital.patient.model.PatientStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(PatientStatus from, PatientStatus to) {
        super("Cannot change patient status from " + from + " to " + to);
    }
}
