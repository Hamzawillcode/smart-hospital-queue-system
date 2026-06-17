package com.hospital.patient.controller;

import com.hospital.patient.dto.CheckInRequest;
import com.hospital.patient.dto.PatientResponse;
import com.hospital.patient.model.PatientStatus;
import com.hospital.patient.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/patients")
public class PatientController {

    @Autowired
    private PatientService patientService;

    @PostMapping("/checkin")
    public ResponseEntity<PatientResponse> checkIn(
            @Valid @RequestBody CheckInRequest request) {
        PatientResponse response = patientService.checkIn(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientResponse> getPatient(
            @PathVariable String id) {
        PatientResponse response = patientService.getById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    public ResponseEntity<List<PatientResponse>> getAllPatients() {
        return ResponseEntity.ok(patientService.getAll());
    }

    @GetMapping("/department/{dept}")
    public ResponseEntity<List<PatientResponse>> getByDepartment(
            @PathVariable String dept) {
        return ResponseEntity.ok(
                patientService.getByDepartment(dept));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<PatientResponse> updateStatus(
            @PathVariable String id,
            @RequestParam PatientStatus status) {
        return ResponseEntity.ok(
                patientService.updateStatus(id, status));
    }
}