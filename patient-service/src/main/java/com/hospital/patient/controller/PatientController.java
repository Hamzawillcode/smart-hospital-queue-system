package com.hospital.patient.controller;

import com.hospital.patient.dto.CheckInRequest;
import com.hospital.patient.dto.PatientResponse;
import com.hospital.patient.model.Patient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/patients")
public class PatientController {

    // Temporary in-memory storage (Day 2 only — DB comes Day 4)
    private final List<Patient> patientStore = new ArrayList<>();
    private int tokenCounter = 1;

    @PostMapping("/checkin")
    public ResponseEntity<PatientResponse> checkIn(@RequestBody CheckInRequest request) {

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID().toString());
        patient.setName(request.getName());
        patient.setPhone(request.getPhone());
        patient.setDepartment(request.getDepartment());
        patient.setStatus("CHECKED_IN");
        patient.setCheckinTime(LocalDateTime.now());

        patientStore.add(patient);

        String token = "TOKEN-" + tokenCounter++;

        PatientResponse response = new PatientResponse(
                patient.getId(),
                patient.getName(),
                patient.getDepartment(),
                patient.getStatus(),
                token,
                patient.getCheckinTime()
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Patient> getPatient(@PathVariable String id) {
        for (Patient p : patientStore) {
            if (p.getId().equals(id)) {
                return ResponseEntity.ok(p);
            }
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/all")
    public ResponseEntity<List<Patient>> getAllPatients() {
        return ResponseEntity.ok(patientStore);
    }
}
