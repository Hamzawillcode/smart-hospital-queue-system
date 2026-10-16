package com.hospital.queue.controller;

import com.hospital.queue.dto.QueueEntryResponse;
import com.hospital.queue.dto.QueueStatusResponse;
import com.hospital.queue.service.QueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Staff-facing queue API. Patients join only through the patient.checked-in event;
 * doctors are assigned only through doctor-service. There is deliberately no REST
 * endpoint that writes either.
 */
@RestController
@RequestMapping("/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','DOCTOR')")
    public List<QueueStatusResponse> getAllQueues() {
        return queueService.getAllQueues();
    }

    @GetMapping("/{departmentId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','DOCTOR')")
    public QueueStatusResponse getStatus(@PathVariable String departmentId) {
        return queueService.getQueueStatus(departmentId);
    }

    @GetMapping("/patients/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','DOCTOR')")
    public QueueEntryResponse getPatient(@PathVariable String patientId) {
        return queueService.getPatientQueueInfo(patientId);
    }

    /** Calls the next patient if the department is idle; re-sends a stuck doctor request otherwise. */
    @PutMapping("/{departmentId}/next")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public QueueStatusResponse callNext(@PathVariable String departmentId) {
        return queueService.dispatchNext(departmentId);
    }

    @PutMapping("/patients/{patientId}/done")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','DOCTOR')")
    public QueueEntryResponse markDone(@PathVariable String patientId) {
        return queueService.completeConsultation(patientId);
    }

    @DeleteMapping("/patients/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ResponseEntity<QueueEntryResponse> leave(@PathVariable String patientId) {
        return ResponseEntity.ok(queueService.leaveQueue(patientId));
    }
}
