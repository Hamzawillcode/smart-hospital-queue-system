package com.hospital.queue.manager;

import com.hospital.queue.factory.QueueStrategyFactory;
import com.hospital.queue.model.QueueEntry;
import com.hospital.queue.strategy.QueuePriorityStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
public class QueueManager {

    private static final Logger log =
            LogManager.getLogger(QueueManager.class);

    @Autowired
    private QueueStrategyFactory strategyFactory;

    // ─────────────────────────────────────────
    // IN-MEMORY STATE
    // ─────────────────────────────────────────

    // departmentId → ordered list of queue entries
    // ConcurrentHashMap: thread-safe map
    // Key = departmentId (e.g. "OPD")
    // Value = list of QueueEntries in position order
    private final ConcurrentHashMap<String, List<QueueEntry>>
            activeQueues = new ConcurrentHashMap<>();

    // departmentId → lock for that department
    // Per-department locking: OPD and EMERGENCY
    // can be modified concurrently — no global lock
    private final ConcurrentHashMap<String, ReentrantLock>
            departmentLocks = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────
    // ADD PATIENT TO QUEUE
    // ─────────────────────────────────────────
    public int addToQueue(QueueEntry entry) {
        String deptId = entry.getDepartmentId();
        ReentrantLock lock = getLockForDepartment(deptId);

        lock.lock();
        try {
            log.debug("Adding patient: {} to dept: {} queue",
                    entry.getPatientName(), deptId);

            // Get or create queue for this department
            List<QueueEntry> queue = activeQueues
                    .computeIfAbsent(deptId,
                            k -> new ArrayList<>());

            // Add the entry
            queue.add(entry);

            // Sort by strategy (FIFO or Emergency priority)
            QueuePriorityStrategy strategy =
                    strategyFactory.getStrategy(deptId);
            queue.sort(strategy::compare);

            // Recalculate positions for ALL entries
            recalculatePositions(queue);

            // Return this patient's position
            int position = entry.getPosition();
            log.info("Patient: {} added at position: {} " +
                            "in dept: {}",
                    entry.getPatientName(), position, deptId);
            return position;

        } finally {
            // ALWAYS release lock — even if exception occurs
            lock.unlock();
        }
    }

    // ─────────────────────────────────────────
    // REMOVE NEXT PATIENT (when called for consultation)
    // ─────────────────────────────────────────
    public Optional<QueueEntry> removeNext(String deptId) {
        ReentrantLock lock = getLockForDepartment(deptId);

        lock.lock();
        try {
            List<QueueEntry> queue = activeQueues.get(deptId);

            if (queue == null || queue.isEmpty()) {
                log.warn("Queue empty for dept: {}", deptId);
                return Optional.empty();
            }

            // Remove position 1 (first in queue)
            QueueEntry next = queue.remove(0);
            log.info("Removed patient: {} from dept: {} queue",
                    next.getPatientName(), deptId);

            // Recalculate positions for remaining patients
            recalculatePositions(queue);

            log.info("Queue for dept: {} now has {} patients",
                    deptId, queue.size());
            return Optional.of(next);

        } finally {
            lock.unlock();
        }
    }

    // ─────────────────────────────────────────
    // REMOVE SPECIFIC PATIENT (e.g. patient leaves)
    // ─────────────────────────────────────────
    public boolean removePatient(String patientId,
                                 String deptId) {
        ReentrantLock lock = getLockForDepartment(deptId);

        lock.lock();
        try {
            List<QueueEntry> queue = activeQueues.get(deptId);

            if (queue == null) {
                return false;
            }

            boolean removed = queue.removeIf(
                    e -> e.getPatientId().equals(patientId));

            if (removed) {
                recalculatePositions(queue);
                log.info("Patient: {} removed from dept: {} queue",
                        patientId, deptId);
            }

            return removed;

        } finally {
            lock.unlock();
        }
    }

    // ─────────────────────────────────────────
    // GET QUEUE SNAPSHOT (read-only copy)
    // ─────────────────────────────────────────
    public List<QueueEntry> getQueueSnapshot(String deptId) {
        List<QueueEntry> queue = activeQueues.get(deptId);

        if (queue == null) {
            return new ArrayList<>();
        }

        // Return a COPY — not the actual list
        // Prevents external code from modifying queue
        // without going through the lock
        return new ArrayList<>(queue);
    }

    // ─────────────────────────────────────────
    // GET PATIENT POSITION
    // ─────────────────────────────────────────
    public Optional<Integer> getPatientPosition(
            String patientId, String deptId) {

        List<QueueEntry> queue = activeQueues.get(deptId);

        if (queue == null) {
            return Optional.empty();
        }

        return queue.stream()
                .filter(e -> e.getPatientId()
                        .equals(patientId))
                .map(QueueEntry::getPosition)
                .findFirst();
    }

    // ─────────────────────────────────────────
    // GET QUEUE SIZE
    // ─────────────────────────────────────────
    public int getQueueSize(String deptId) {
        List<QueueEntry> queue = activeQueues.get(deptId);
        return queue == null ? 0 : queue.size();
    }

    // ─────────────────────────────────────────
    // LOAD QUEUE FROM DB (called on startup)
    // ─────────────────────────────────────────
    public void loadQueue(String deptId,
                          List<QueueEntry> entries) {
        ReentrantLock lock = getLockForDepartment(deptId);

        lock.lock();
        try {
            // Sort by existing position
            List<QueueEntry> sorted = entries.stream()
                    .sorted(Comparator.comparing(
                            QueueEntry::getPosition))
                    .collect(Collectors.toList());

            activeQueues.put(deptId, sorted);
            log.info("Loaded {} entries for dept: {} from DB",
                    sorted.size(), deptId);
        } finally {
            lock.unlock();
        }
    }

    // ─────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────

    // Recalculate position numbers 1, 2, 3...
    private void recalculatePositions(
            List<QueueEntry> queue) {
        for (int i = 0; i < queue.size(); i++) {
            queue.get(i).setPosition(i + 1);
        }
    }

    // Get or create lock for a department
    private ReentrantLock getLockForDepartment(
            String deptId) {
        return departmentLocks.computeIfAbsent(
                deptId, k -> new ReentrantLock());
    }
}
