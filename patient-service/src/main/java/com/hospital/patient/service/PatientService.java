package com.hospital.patient.service;

import com.hospital.common.events.DoctorAssignedEvent;
import com.hospital.common.events.DoctorReleasedEvent;
import com.hospital.patient.dto.CheckInRequest;
import com.hospital.patient.dto.PatientResponse;
import com.hospital.patient.model.Department;
import com.hospital.patient.model.PatientStatus;

import java.time.LocalDate;
import java.util.List;

public interface PatientService {

    PatientResponse checkIn(CheckInRequest request);

    PatientResponse getById(String patientId);

    List<PatientResponse> getByDepartment(String departmentId, LocalDate date);

    List<Department> getDepartments();

    /** Staff correction; must respect the status state machine. */
    PatientResponse updateStatus(String patientId, PatientStatus status);

    void onDoctorAssigned(DoctorAssignedEvent event);

    void onDoctorReleased(DoctorReleasedEvent event);
}
