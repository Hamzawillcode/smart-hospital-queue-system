package com.hospital.doctor.service;

import com.hospital.doctor.dto.DoctorRegisterRequest;
import com.hospital.doctor.dto.DoctorRequestResponse;
import com.hospital.doctor.dto.DoctorResponse;
import com.hospital.doctor.model.DoctorRequest;
import com.hospital.doctor.model.DoctorStatus;

import java.util.List;

public interface DoctorService {

    DoctorResponse register(DoctorRegisterRequest request);

    DoctorResponse getById(String doctorId);

    List<DoctorResponse> getAll();

    List<DoctorResponse> getByDepartment(String departmentId);

    List<DoctorResponse> getAvailableByDepartment(String departmentId);

    /** Manual change (break, off duty, back on shift). Retried automatically on optimistic-lock conflicts. */
    DoctorResponse updateStatus(String doctorId, DoctorStatus newStatus);

    List<DoctorRequestResponse> getRequests(String departmentId, DoctorRequest.Status status);
}
