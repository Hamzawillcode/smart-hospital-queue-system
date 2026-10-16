package com.hospital.doctor.model;

public enum DoctorStatus {
    AVAILABLE,
    /** Reserved for exactly one request (see Doctor#currentRequestId). Only the assignment flow sets this. */
    BUSY,
    ON_BREAK,
    OFF_DUTY
}
