package com.hospital.queue.factory;

import com.hospital.queue.config.QueueProperties;
import com.hospital.queue.strategy.QueuePriorityStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Factory pattern: resolves the ordering strategy for a department from configuration.
 *
 * Every {@link QueuePriorityStrategy} bean registers itself by name. Unknown strategy
 * names in configuration fail fast at startup instead of at the first check-in.
 */
@Component
public class QueueStrategyFactory {

    private final Map<String, QueuePriorityStrategy> strategiesByName =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> departmentOverrides;
    private final QueuePriorityStrategy defaultStrategy;

    public QueueStrategyFactory(List<QueuePriorityStrategy> strategies, QueueProperties properties) {
        for (QueuePriorityStrategy strategy : strategies) {
            if (strategiesByName.putIfAbsent(strategy.name(), strategy) != null) {
                throw new IllegalStateException("Duplicate queue strategy name: " + strategy.name());
            }
        }
        this.departmentOverrides = properties.strategy().departmentOverrides();
        this.defaultStrategy = require(properties.strategy().defaultStrategy());
        departmentOverrides.values().forEach(this::require);
    }

    public QueuePriorityStrategy forDepartment(String departmentId) {
        String name = departmentOverrides.get(departmentId);
        return name == null ? defaultStrategy : strategiesByName.get(name);
    }

    private QueuePriorityStrategy require(String name) {
        QueuePriorityStrategy strategy = strategiesByName.get(name);
        if (strategy == null) {
            throw new IllegalStateException("Unknown queue strategy '" + name
                    + "'. Available: " + strategiesByName.keySet());
        }
        return strategy;
    }
}
