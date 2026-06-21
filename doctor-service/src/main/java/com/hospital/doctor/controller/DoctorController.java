package com.hospital.doctor.controller;

import com.hospital.doctor.dto.DoctorRegisterRequest;
import com.hospital.doctor.dto.DoctorResponse;
import com.hospital.doctor.dto.DoctorStatusUpdateRequest;
import com.hospital.doctor.model.DoctorStatus;
import com.hospital.doctor.service.DoctorService;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/doctors")
public class DoctorController {

    private static final Logger log =
            LogManager.getLogger(DoctorController.class);

    @Autowired
    private DoctorService doctorService;

    // Register new doctor
    @PostMapping("/register")
    public ResponseEntity<DoctorResponse> register(
            @Valid @RequestBody DoctorRegisterRequest request) {
        log.info("Register request for doctor: {}",
                request.getName());
        return new ResponseEntity<>(
                doctorService.register(request),
                HttpStatus.CREATED);
    }

    // Get doctor by ID
    @GetMapping("/{id}")
    public ResponseEntity<DoctorResponse> getById(
            @PathVariable String id) {
        return ResponseEntity.ok(doctorService.getById(id));
    }

    // Get all doctors
    @GetMapping("/all")
    public ResponseEntity<List<DoctorResponse>> getAll() {
        return ResponseEntity.ok(doctorService.getAll());
    }

    // Get all doctors in a department
    @GetMapping("/department/{deptId}")
    public ResponseEntity<List<DoctorResponse>> getByDepartment(
            @PathVariable String deptId) {
        return ResponseEntity.ok(
                doctorService.getByDepartment(deptId));
    }

    // Get available doctors in a department
    @GetMapping("/department/{deptId}/available")
    public ResponseEntity<List<DoctorResponse>> getAvailable(
            @PathVariable String deptId) {
        return ResponseEntity.ok(
                doctorService.getAvailableByDepartment(deptId));
    }

    // Find first available doctor in a department
    // Queue service will call THIS endpoint on Day 13
    @GetMapping("/department/{deptId}/first-available")
    public ResponseEntity<DoctorResponse> findFirstAvailable(
            @PathVariable String deptId) {
        log.info("Finding first available doctor in: {}",
                deptId);
        return ResponseEntity.ok(
                doctorService.findFirstAvailable(deptId));
    }

    // Update doctor status
    @PutMapping("/{id}/status")
    public ResponseEntity<DoctorResponse> updateStatus(
            @PathVariable String id,
            @RequestBody DoctorStatusUpdateRequest request) {
        return ResponseEntity.ok(
                doctorService.updateStatus(
                        id, request.getStatus()));
    }

    // Assign doctor to patient (sets BUSY)
    @PutMapping("/{id}/assign")
    public ResponseEntity<DoctorResponse> assign(
            @PathVariable String id) {
        log.info("Assigning doctor: {}", id);
        return ResponseEntity.ok(
                doctorService.assignToPatient(id));
    }

    // Free doctor after consultation (sets AVAILABLE)
    @PutMapping("/{id}/free")
    public ResponseEntity<DoctorResponse> free(
            @PathVariable String id) {
        log.info("Freeing doctor: {}", id);
        return ResponseEntity.ok(doctorService.freeDoctor(id));
    }
}
