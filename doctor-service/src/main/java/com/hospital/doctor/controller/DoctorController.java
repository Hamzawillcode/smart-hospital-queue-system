package com.hospital.doctor.controller;

import com.hospital.doctor.dto.DoctorRegisterRequest;
import com.hospital.doctor.dto.DoctorRequestResponse;
import com.hospital.doctor.dto.DoctorResponse;
import com.hospital.doctor.dto.DoctorStatusUpdateRequest;
import com.hospital.doctor.model.DoctorRequest;
import com.hospital.doctor.service.DoctorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Doctor registry API. Assignment and release happen only through Kafka events,
 * so there are no "assign" or "free" endpoints that could bypass the locking flow.
 */
@RestController
@RequestMapping("/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DoctorResponse> register(@Valid @RequestBody DoctorRegisterRequest request) {
        return new ResponseEntity<>(doctorService.register(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','DOCTOR')")
    public DoctorResponse getById(@PathVariable String id) {
        return doctorService.getById(id);
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','DOCTOR')")
    public List<DoctorResponse> getAll() {
        return doctorService.getAll();
    }

    @GetMapping("/department/{departmentId}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','DOCTOR')")
    public List<DoctorResponse> getByDepartment(@PathVariable String departmentId) {
        return doctorService.getByDepartment(departmentId);
    }

    @GetMapping("/department/{departmentId}/available")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','DOCTOR')")
    public List<DoctorResponse> getAvailable(@PathVariable String departmentId) {
        return doctorService.getAvailableByDepartment(departmentId);
    }

    @GetMapping("/department/{departmentId}/requests")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public List<DoctorRequestResponse> getRequests(@PathVariable String departmentId,
                                                   @RequestParam(defaultValue = "PENDING") DoctorRequest.Status status) {
        return doctorService.getRequests(departmentId, status);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public DoctorResponse updateStatus(@PathVariable String id, @Valid @RequestBody DoctorStatusUpdateRequest request) {
        return doctorService.updateStatus(id, request.status());
    }
}
