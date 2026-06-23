package com.hospital.queue.controller;

import com.hospital.queue.dto.*;
import com.hospital.queue.service.QueueService;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/queue")
public class QueueController {

    private static final Logger log =
            LogManager.getLogger(QueueController.class);

    @Autowired
    private QueueService queueService;

    // Add patient to queue
    @PostMapping("/add")
    public ResponseEntity<QueueEntryResponse> addToQueue(
            @Valid @RequestBody AddToQueueRequest request) {
        log.info("Add to queue request: patient: {}",
                request.getPatientName());
        return new ResponseEntity<>(
                queueService.addToQueue(request),
                HttpStatus.CREATED);
    }

    // Get queue status for a department
    @GetMapping("/{departmentId}/status")
    public ResponseEntity<QueueStatusResponse> getStatus(
            @PathVariable String departmentId) {
        return ResponseEntity.ok(
                queueService.getQueueStatus(departmentId));
    }

    // Get a patient's position in queue
    @GetMapping("/{departmentId}/patient/{patientId}")
    public ResponseEntity<QueueEntryResponse> getPatientInfo(
            @PathVariable String departmentId,
            @PathVariable String patientId) {
        return ResponseEntity.ok(
                queueService.getPatientQueueInfo(
                        patientId, departmentId));
    }

    // Call next patient (doctor is ready)
    @PutMapping("/{departmentId}/next")
    public ResponseEntity<QueueEntryResponse> callNext(
            @PathVariable String departmentId) {
        log.info("Calling next patient for dept: {}",
                departmentId);
        return ResponseEntity.ok(
                queueService.callNextPatient(departmentId));
    }

    // Assign doctor to patient
    @PutMapping("/patient/{patientId}/assign-doctor")
    public ResponseEntity<QueueEntryResponse> assignDoctor(
            @PathVariable String patientId,
            @RequestBody AssignDoctorRequest request) {
        return ResponseEntity.ok(
                queueService.assignDoctor(patientId, request));
    }

    // Mark consultation done
    @PutMapping("/{departmentId}/patient/{patientId}/done")
    public ResponseEntity<QueueEntryResponse> markDone(
            @PathVariable String departmentId,
            @PathVariable String patientId) {
        return ResponseEntity.ok(
                queueService.markDone(patientId, departmentId));
    }

    // Remove patient from queue (patient leaves)
    @DeleteMapping("/{departmentId}/patient/{patientId}")
    public ResponseEntity<String> removeFromQueue(
            @PathVariable String departmentId,
            @PathVariable String patientId) {
        boolean removed = queueService
                .removeFromQueue(patientId, departmentId);
        if (removed) {
            return ResponseEntity.ok(
                    "Patient removed from queue");
        }
        return ResponseEntity.notFound().build();
    }

    // Get all department queues
    @GetMapping("/all")
    public ResponseEntity<List<QueueStatusResponse>> getAllQueues() {
        return ResponseEntity.ok(queueService.getAllQueues());
    }
}
