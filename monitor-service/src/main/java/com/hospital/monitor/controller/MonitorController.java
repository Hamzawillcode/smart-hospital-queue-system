package com.hospital.monitor.controller;

import com.hospital.monitor.document.DeadLetterRecord;
import com.hospital.monitor.document.QueueSnapshot;
import com.hospital.monitor.dto.DashboardResponse;
import com.hospital.monitor.dto.DepartmentStatsResponse;
import com.hospital.monitor.dto.EventLogResponse;
import com.hospital.monitor.service.MonitorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only operations API. Data arrives only through Kafka; there is no
 * "log an event" endpoint any more, so nobody can inject fake events over HTTP.
 */
@RestController
@RequestMapping("/monitor")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return monitorService.getDashboard();
    }

    @GetMapping("/departments/{departmentId}/stats")
    public DepartmentStatsResponse departmentStats(@PathVariable String departmentId) {
        return monitorService.getDepartmentStats(departmentId);
    }

    @GetMapping("/departments/{departmentId}/snapshots")
    public List<QueueSnapshot> snapshots(@PathVariable String departmentId,
                                         @RequestParam(defaultValue = "50") int limit) {
        return monitorService.getSnapshotHistory(departmentId, limit);
    }

    @GetMapping("/events/recent")
    public List<EventLogResponse> recentEvents(@RequestParam(defaultValue = "20") int limit) {
        return monitorService.getRecentEvents(limit);
    }

    @GetMapping("/patients/{patientId}/events")
    public List<EventLogResponse> patientHistory(@PathVariable String patientId) {
        return monitorService.getPatientHistory(patientId);
    }

    @GetMapping("/dead-letters")
    public List<DeadLetterRecord> deadLetters(@RequestParam(defaultValue = "NEW") DeadLetterRecord.Status status) {
        return monitorService.getDeadLetters(status);
    }

    @PostMapping("/dead-letters/{id}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    public DeadLetterRecord replay(@PathVariable String id) {
        return monitorService.replayDeadLetter(id);
    }
}
