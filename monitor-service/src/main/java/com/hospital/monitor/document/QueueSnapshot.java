package com.hospital.monitor.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * One document per queue.updated version: a full history of each department's
 * queue for charts and post-incident analysis. id = "{departmentId}:{version}"
 * makes storage idempotent.
 *
 * @param waitingTokens tokens only, no patient names
 */
@Document(collection = "queue_snapshots")
@CompoundIndex(name = "dept_version", def = "{'departmentId': 1, 'version': -1}")
public record QueueSnapshot(
        @Id String id,
        String departmentId,
        long version,
        String strategy,
        int waitingCount,
        int activeCount,
        double avgWaitMinutes,
        double longestWaitMinutes,
        List<String> waitingTokens,
        Instant occurredAt) {
}
