package com.hospital.notification.service.impl;

import com.hospital.common.events.QueueUpdatedEvent;
import com.hospital.notification.dto.DepartmentQueueMessage;
import com.hospital.notification.dto.NotificationMessage;
import com.hospital.notification.service.NotificationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns versioned department snapshots into per-patient pushes.
 *
 * <ul>
 *   <li>Stale or duplicate snapshots (version not newer) are dropped, so redelivery or
 *       reordering can never move a patient's position backwards on screen.</li>
 *   <li>Patients who disappear from a snapshot get a final COMPLETED message.</li>
 *   <li>The latest view per access code is kept so a page that (re)connects immediately
 *       gets its current position instead of waiting for the next change.</li>
 * </ul>
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LogManager.getLogger(NotificationServiceImpl.class);

    private final SimpMessageSendingOperations messaging;
    private final Clock clock;

    private final ConcurrentHashMap<String, QueueUpdatedEvent> latestByDepartment = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NotificationMessage> viewsByAccessCode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> departmentLocks = new ConcurrentHashMap<>();

    public NotificationServiceImpl(SimpMessageSendingOperations messaging, Clock clock) {
        this.messaging = messaging;
        this.clock = clock;
    }

    @Override
    public boolean onQueueUpdated(QueueUpdatedEvent event) {
        String department = event.departmentId();
        synchronized (departmentLocks.computeIfAbsent(department, d -> new Object())) {
            QueueUpdatedEvent previous = latestByDepartment.get(department);
            if (previous != null && event.version() <= previous.version()) {
                log.debug("Dropping stale queue.updated for {}: v{} <= v{}", department, event.version(),
                        previous.version());
                return false;
            }
            latestByDepartment.put(department, event);

            Set<String> present = new HashSet<>();
            Instant now = Instant.now(clock);
            for (QueueUpdatedEvent.WaitingEntry entry : nullSafe(event.waiting())) {
                push(entry.accessCode(), waitingView(event, entry, now));
                present.add(entry.accessCode());
            }
            for (QueueUpdatedEvent.ActiveEntry entry : nullSafe(event.active())) {
                push(entry.accessCode(), activeView(event, entry, now));
                present.add(entry.accessCode());
            }
            if (previous != null) {
                for (String code : accessCodes(previous)) {
                    if (!present.contains(code)) {
                        NotificationMessage last = viewsByAccessCode.remove(code);
                        messaging.convertAndSend("/topic/patient/" + code, new NotificationMessage(
                                last == null ? null : last.tokenNumber(), department, "COMPLETED", null, null, null,
                                "Your visit is complete or you have left the queue. Thank you.", null, null,
                                event.version(), now));
                    }
                }
            }
            messaging.convertAndSend("/topic/department/" + department, new DepartmentQueueMessage(department,
                    nullSafe(event.waiting()).size(), nullSafe(event.active()).size(), event.version(), now));
            return true;
        }
    }

    @Override
    public Optional<NotificationMessage> currentView(String accessCode) {
        return Optional.ofNullable(viewsByAccessCode.get(accessCode));
    }

    private void push(String accessCode, NotificationMessage view) {
        viewsByAccessCode.put(accessCode, view);
        messaging.convertAndSend("/topic/patient/" + accessCode, view);
    }

    private static NotificationMessage waitingView(QueueUpdatedEvent event, QueueUpdatedEvent.WaitingEntry entry,
                                                   Instant now) {
        int ahead = entry.position() - 1;
        String message;
        if (entry.position() == 1) {
            message = "You're next! Please stay near the consultation area.";
        } else if (entry.position() <= 5) {
            message = ahead + " patients ahead of you. Please stay nearby.";
        } else {
            message = ahead + " patients ahead of you. Estimated wait: " + entry.estimatedWaitMinutes() + " min.";
        }
        return new NotificationMessage(entry.tokenNumber(), event.departmentId(), "WAITING", entry.position(), ahead,
                entry.estimatedWaitMinutes(), message, null, null, event.version(), now);
    }

    private static NotificationMessage activeView(QueueUpdatedEvent event, QueueUpdatedEvent.ActiveEntry entry,
                                                  Instant now) {
        boolean withDoctor = "IN_CONSULTATION".equals(entry.status());
        String message = withDoctor
                ? "Please proceed to room " + entry.roomNumber() + " (" + entry.doctorName() + ")."
                : "You've been called! A doctor will be assigned to you shortly.";
        return new NotificationMessage(entry.tokenNumber(), event.departmentId(),
                withDoctor ? "IN_CONSULTATION" : "CALLED", 0, 0, 0, message,
                entry.doctorName(), entry.roomNumber(), event.version(), now);
    }

    private static Set<String> accessCodes(QueueUpdatedEvent event) {
        Set<String> codes = new HashSet<>();
        nullSafe(event.waiting()).forEach(e -> codes.add(e.accessCode()));
        nullSafe(event.active()).forEach(e -> codes.add(e.accessCode()));
        return codes;
    }

    private static <T> java.util.List<T> nullSafe(java.util.List<T> list) {
        return list == null ? java.util.List.of() : list;
    }
}
