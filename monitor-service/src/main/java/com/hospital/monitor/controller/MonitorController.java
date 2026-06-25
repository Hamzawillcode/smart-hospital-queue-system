package com.hospital.monitor.controller;

import com.hospital.monitor.dto.*;
import com.hospital.monitor.service.MonitorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/monitor")
public class MonitorController {

    private static final Logger log =
            LogManager.getLogger(MonitorController.class);

    @Autowired
    private MonitorService monitorService;

    // Log an event (called by other services)
    @PostMapping("/log")
    public ResponseEntity<String> logEvent(
            @RequestBody LogEventRequest request) {
        log.info("Event received: {} from: {}",
                request.getEventType(),
                request.getSourceService());
        monitorService.logEvent(request);
        return new ResponseEntity<>(
                "Event logged successfully",
                HttpStatus.CREATED);
    }

    // Hospital admin dashboard
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard() {
        log.info("Dashboard requested");
        return ResponseEntity.ok(
                monitorService.getDashboard());
    }

    // Department specific stats
    @GetMapping("/department/{deptId}/stats")
    public ResponseEntity<DepartmentStatsResponse> getDeptStats(
            @PathVariable String deptId) {
        return ResponseEntity.ok(
                monitorService.getDepartmentStats(deptId));
    }

    // Recent events
    @GetMapping("/events/recent")
    public ResponseEntity<List<EventLogResponse>> getRecent(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(
                monitorService.getRecentEvents(limit));
    }

    // Patient event history
    @GetMapping("/patient/{patientId}/history")
    public ResponseEntity<List<EventLogResponse>> getHistory(
            @PathVariable String patientId) {
        return ResponseEntity.ok(
                monitorService.getPatientHistory(patientId));
    }

    // Take queue snapshot for a department
    @PostMapping("/snapshot/{deptId}")
    public ResponseEntity<String> takeSnapshot(
            @PathVariable String deptId) {
        monitorService.takeQueueSnapshot(deptId);
        return ResponseEntity.ok(
                "Snapshot taken for: " + deptId);
    }
}
