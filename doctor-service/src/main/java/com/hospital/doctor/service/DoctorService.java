package com.hospital.doctor.service;

import com.hospital.doctor.dto.DoctorRegisterRequest;
import com.hospital.doctor.dto.DoctorResponse;
import com.hospital.doctor.model.DoctorStatus;
import java.util.List;

public interface DoctorService {

    DoctorResponse register(DoctorRegisterRequest request);

    DoctorResponse getById(String doctorId);

    List<DoctorResponse> getAll();

    List<DoctorResponse> getByDepartment(String deptId);

    List<DoctorResponse> getAvailableByDepartment(String deptId);

    DoctorResponse findFirstAvailable(String deptId);

    DoctorResponse updateStatus(String doctorId,
                                DoctorStatus newStatus);

    DoctorResponse assignToPatient(String doctorId);

    DoctorResponse freeDoctor(String doctorId);
}
