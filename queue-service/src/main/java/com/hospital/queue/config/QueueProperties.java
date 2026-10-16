package com.hospital.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;
import java.util.TreeMap;

/**
 * Queue engine settings.
 *
 * Example:
 * <pre>
 * queue.strategy.default-strategy=FIFO
 * queue.strategy.departments.EMERGENCY=PRIORITY
 * </pre>
 */
@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
        @DefaultValue("10") int avgConsultationMinutes,
        @DefaultValue("15") int agingMinutesPerLevel,
        @DefaultValue Strategy strategy) {

    public record Strategy(@DefaultValue("FIFO") String defaultStrategy,
                           Map<String, String> departments) {

        /** Department ids are matched case-insensitively. */
        public Map<String, String> departmentOverrides() {
            Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            if (departments != null) {
                result.putAll(departments);
            }
            return result;
        }
    }
}
