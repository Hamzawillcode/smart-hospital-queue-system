package com.hospital.patient.service;

import com.hospital.patient.dto.CheckInRequest;
import com.hospital.patient.dto.PatientResponse;
import com.hospital.patient.model.PatientStatus;
import java.util.List;

public interface PatientService {

    PatientResponse checkIn(CheckInRequest request);

    PatientResponse getById(String patientId);

    List<PatientResponse> getAll();

    List<PatientResponse> getByDepartment(String departmentId);

    PatientResponse updateStatus(String patientId,
                                 PatientStatus newStatus);
}
