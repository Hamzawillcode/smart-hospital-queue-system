package com.hospital.patient.controller;

import com.hospital.patient.dto.CheckInRequest;
import com.hospital.patient.dto.PatientResponse;
import com.hospital.patient.model.Patient;
import com.hospital.patient.model.PatientStatus;
import com.hospital.patient.repository.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/patients")
public class PatientController {

    @Autowired
    private PatientRepository patientRepository;
    private int tokenCounter = 1;

    @PostMapping("/checkin")
    public ResponseEntity<PatientResponse> checkIn(@RequestBody CheckInRequest request) {
        Patient patient = new Patient();
        patient.setName(request.getName());
        patient.setPhone(request.getPhone());
        patient.setDepartment(request.getDepartment());
        patient.setStatus(PatientStatus.CHECKED_IN);
        patient.setCheckinTime(LocalDateTime.now());

        // TODO: Race condition here — two simultaneous checkins
        // can get the same token number.
        // Fix 1: pessimistic lock on Department row (Day 12)
        // Fix 2: token generation moves to queue-service (Day 20)
        long count = patientRepository.countByDepartmentAndStatus(
                request.getDepartment(), PatientStatus.CHECKED_IN);
        patient.setTokenNumber(request.getDepartment()
                + "-" + (count + 1));

        Patient saved = patientRepository.save(patient);
        return new ResponseEntity<>(
                mapToResponse(saved), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientResponse> getPatient(@PathVariable String id) {
        Optional<Patient> patient = patientRepository.findById(id);

        if (patient.isPresent()) {
            return ResponseEntity.ok(mapToResponse(patient.get()));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<PatientResponse>> getAllPatients() {
        List<Patient> patients = patientRepository.findAll();

        List<PatientResponse> response = patients.stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(response);
    }
    @GetMapping("/department/{dept}")
    public ResponseEntity<List<PatientResponse>> getByDepartment(
            @PathVariable String dept) {

        List<Patient> patients =
                patientRepository.findByDepartment(dept);

        List<PatientResponse> response = patients.stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(response);
    }
    private PatientResponse mapToResponse(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getName(),
                patient.getDepartment(),
                patient.getStatus(),
                patient.getTokenNumber(),
                patient.getCheckinTime()
        );
    }
}
