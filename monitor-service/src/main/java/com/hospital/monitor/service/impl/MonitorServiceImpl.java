package com.hospital.monitor.service.impl;

import com.hospital.monitor.client.DoctorServiceClient;
import com.hospital.monitor.client.QueueServiceClient;
import com.hospital.monitor.document.DoctorActivityLog;
import com.hospital.monitor.document.EventLog;
import com.hospital.monitor.document.QueueSnapshot;
import com.hospital.monitor.dto.*;
import com.hospital.monitor.repository.DoctorActivityLogRepository;
import com.hospital.monitor.repository.EventLogRepository;
import com.hospital.monitor.repository.QueueSnapshotRepository;
import com.hospital.monitor.service.MonitorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MonitorServiceImpl implements MonitorService {

    private static final Logger log =
            LogManager.getLogger(MonitorServiceImpl.class);

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private QueueSnapshotRepository snapshotRepository;

    @Autowired
    private DoctorActivityLogRepository doctorLogRepository;

    @Autowired
    private QueueServiceClient queueClient;

    @Autowired
    private DoctorServiceClient doctorClient;

    // ─────────────────────────────────────────
    // LOG EVENT
    // ─────────────────────────────────────────
    @Override
    public void logEvent(LogEventRequest request) {
        log.debug("Logging event: {} from: {}",
                request.getEventType(),
                request.getSourceService());

        EventLog eventLog = new EventLog(
                null,
                request.getEventType(),
                request.getSourceService(),
                request.getPayload(),
                request.getDepartmentId(),
                request.getPatientId(),
                LocalDateTime.now()
        );

        eventLogRepository.save(eventLog);

        log.info("Event logged: {} for patient: {}",
                request.getEventType(),
                request.getPatientId());
    }

    // ─────────────────────────────────────────
    // GET DASHBOARD
    // ─────────────────────────────────────────
    @Override
    public DashboardResponse getDashboard() {
        log.info("Building dashboard response");

        // Today's start time
        LocalDateTime todayStart = LocalDateTime.of(
                LocalDate.now(), LocalTime.MIDNIGHT);

        // Count today's events
        long totalEventsToday = eventLogRepository
                .countByRecordedAtAfter(todayStart);

        // Count today's patient checkins
        long totalPatientsToday = eventLogRepository
                .findByEventTypeOrderByRecordedAtDesc(
                        "PATIENT_CHECKED_IN")
                .stream()
                .filter(e -> e.getRecordedAt()
                        .isAfter(todayStart))
                .count();

        // Queue sizes per department
        Map<String, Integer> queueSizes = new HashMap<>();
        List<String> departments = List.of(
                "OPD", "EMERGENCY", "CARDIOLOGY");

        for (String dept : departments) {
            Map queueStatus = queueClient
                    .getQueueStatus(dept);
            Object waiting = queueStatus
                    .get("totalWaiting");
            queueSizes.put(dept,
                    waiting != null
                            ? ((Number) waiting).intValue()
                            : 0);
        }

        // Available doctors per department
        Map<String, Long> availableDoctors = new HashMap<>();
        for (String dept : departments) {
            List docs = doctorClient
                    .getAvailableDoctors(dept);
            availableDoctors.put(dept,
                    (long) docs.size());
        }

        // Recent 10 events
        List<EventLogResponse> recentEvents =
                getRecentEvents(10);

        log.info("Dashboard built: {} events today, " +
                        "{} patients today",
                totalEventsToday, totalPatientsToday);

        return new DashboardResponse(
                totalPatientsToday,
                totalEventsToday,
                queueSizes,
                availableDoctors,
                recentEvents
        );
    }

    // ─────────────────────────────────────────
    // GET DEPARTMENT STATS
    // ─────────────────────────────────────────
    @Override
    public DepartmentStatsResponse getDepartmentStats(
            String departmentId) {

        log.debug("Getting stats for dept: {}", departmentId);

        // Get live queue data
        Map queueStatus = queueClient
                .getQueueStatus(departmentId);

        int waiting = queueStatus.get("totalWaiting") != null
                ? ((Number) queueStatus
                .get("totalWaiting")).intValue()
                : 0;

        int assigned = queueStatus.get("totalAssigned") != null
                ? ((Number) queueStatus
                .get("totalAssigned")).intValue()
                : 0;

        // Get available doctors
        List availableDocs = doctorClient
                .getAvailableDoctors(departmentId);

        // Get latest snapshot for avg wait
        double avgWait = snapshotRepository
                .findTopByDepartmentIdOrderByRecordedAtDesc(
                        departmentId)
                .map(QueueSnapshot::getAvgWaitMinutes)
                .orElse(0.0);

        // Count served today
        LocalDateTime todayStart = LocalDateTime.of(
                LocalDate.now(), LocalTime.MIDNIGHT);
        long servedToday = eventLogRepository
                .findByDepartmentIdOrderByRecordedAtDesc(
                        departmentId)
                .stream()
                .filter(e -> e.getEventType()
                        .equals("CONSULTATION_DONE")
                        && e.getRecordedAt()
                        .isAfter(todayStart))
                .count();

        return new DepartmentStatsResponse(
                departmentId,
                waiting,
                assigned,
                availableDocs.size(),
                avgWait,
                servedToday
        );
    }

    // ─────────────────────────────────────────
    // GET RECENT EVENTS
    // ─────────────────────────────────────────
    @Override
    public List<EventLogResponse> getRecentEvents(int limit) {
        log.debug("Fetching recent {} events", limit);

        return eventLogRepository
                .findTop10ByOrderByRecordedAtDesc()
                .stream()
                .limit(limit)
                .map(this::mapToEventResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────
    // GET PATIENT HISTORY
    // ─────────────────────────────────────────
    @Override
    public List<EventLogResponse> getPatientHistory(
            String patientId) {

        log.debug("Fetching history for patient: {}",
                patientId);

        return eventLogRepository
                .findByPatientIdOrderByRecordedAtDesc(patientId)
                .stream()
                .map(this::mapToEventResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────
    // TAKE QUEUE SNAPSHOT
    // ─────────────────────────────────────────
    @Override
    public void takeQueueSnapshot(String departmentId) {
        log.info("Taking queue snapshot for dept: {}",
                departmentId);

        Map queueStatus = queueClient
                .getQueueStatus(departmentId);

        int totalWaiting = queueStatus
                .get("totalWaiting") != null
                ? ((Number) queueStatus
                .get("totalWaiting")).intValue()
                : 0;

        // Extract patient names from queue
        List<String> patientNames = new ArrayList<>();
        Object queueList = queueStatus.get("queue");
        if (queueList instanceof List) {
            ((List<Map>) queueList).forEach(entry -> {
                Object name = entry.get("patientName");
                if (name != null) {
                    patientNames.add(name.toString());
                }
            });
        }

        // Estimate avg wait (5 mins per position)
        double avgWait = totalWaiting * 5.0 / 2;

        QueueSnapshot snapshot = new QueueSnapshot(
                null,
                departmentId,
                totalWaiting,
                0,
                avgWait,
                patientNames,
                LocalDateTime.now()
        );

        snapshotRepository.save(snapshot);
        log.info("Snapshot saved for dept: {} " +
                        "with {} waiting",
                departmentId, totalWaiting);
    }

    // ─────────────────────────────────────────
    // PRIVATE MAPPER
    // ─────────────────────────────────────────
    private EventLogResponse mapToEventResponse(
            EventLog log) {
        return new EventLogResponse(
                log.getEventType(),
                log.getSourceService(),
                log.getDepartmentId(),
                log.getPatientId(),
                log.getPayload(),
                log.getRecordedAt()
        );
    }
}
