package com.hospital.queue.manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, versioned state of one department's waiting queue.
 *
 * A new snapshot replaces the old one on every committed change
 * (copy-on-write), so readers never need a lock and never see a half-applied update.
 *
 * @param version matches queue_departments.state_version of the commit that produced it;
 *                -1 means "not loaded yet"
 */
public record DepartmentSnapshot(long version, List<QueueItem> waiting, Map<String, Integer> positions) {

    private static final DepartmentSnapshot UNLOADED = new DepartmentSnapshot(-1, List.of(), Map.of());

    public static DepartmentSnapshot unloaded() {
        return UNLOADED;
    }

    /** @param ordered waiting patients already sorted by the department's strategy */
    public static DepartmentSnapshot of(long version, List<QueueItem> ordered) {
        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            positions.put(ordered.get(i).patientId(), i + 1);
        }
        return new DepartmentSnapshot(version, List.copyOf(ordered), Map.copyOf(positions));
    }

    public boolean isLoaded() {
        return version >= 0;
    }

    /** 1-based position, O(1). */
    public Optional<Integer> positionOf(String patientId) {
        return Optional.ofNullable(positions.get(patientId));
    }
}
