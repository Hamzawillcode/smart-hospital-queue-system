package com.hospital.patient.controller;

import com.hospital.patient.dto.CheckInRequest;
import com.hospital.patient.dto.PatientResponse;
import com.hospital.patient.model.Department;
import com.hospital.patient.model.PatientStatus;
import com.hospital.patient.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/patients")
public class PatientController {

    private final PatientService patientService;
    private final ZoneId hospitalZone;

    public PatientController(PatientService patientService,
                             @Value("${hospital.time-zone}")
                             ZoneId hospitalZone) {
        this.patientService = patientService;
        this.hospitalZone = hospitalZone;
    }

    /** Reception checks a patient in; the response contains the token and the patient's tracking link. */
    @PostMapping("/checkin")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ResponseEntity<PatientResponse> checkIn(@Valid @RequestBody CheckInRequest request) {
        return new ResponseEntity<>(patientService.checkIn(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','DOCTOR')")
    public PatientResponse getPatient(@PathVariable String id) {
        return patientService.getById(id);
    }

    /** Patients of one department for one day (today by default). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public List<PatientResponse> getByDepartment(
            @RequestParam String department,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return patientService.getByDepartment(department, date != null ? date : LocalDate.now(hospitalZone));
    }

    @GetMapping("/departments")
    @PreAuthorize("isAuthenticated()")
    public List<Department> getDepartments() {
        return patientService.getDepartments();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public PatientResponse updateStatus(@PathVariable String id, @RequestParam PatientStatus status) {
        return patientService.updateStatus(id, status);
    }
}
