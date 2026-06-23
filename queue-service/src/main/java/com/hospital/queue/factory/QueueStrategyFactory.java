package com.hospital.queue.factory;

import com.hospital.queue.strategy.EmergencyPriorityStrategy;
import com.hospital.queue.strategy.FifoStrategy;
import com.hospital.queue.strategy.QueuePriorityStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class QueueStrategyFactory {

    @Autowired
    private FifoStrategy fifoStrategy;

    @Autowired
    private EmergencyPriorityStrategy emergencyStrategy;

    public QueuePriorityStrategy getStrategy(
            String departmentId) {

        // Emergency department gets priority strategy
        if ("EMERGENCY".equalsIgnoreCase(departmentId)) {
            return emergencyStrategy;
        }

        // All other departments use FIFO
        return fifoStrategy;
    }
}
