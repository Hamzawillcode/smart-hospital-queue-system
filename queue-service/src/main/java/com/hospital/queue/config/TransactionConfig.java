package com.hospital.queue.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration
public class TransactionConfig {

    /**
     * Queue mutations use a programmatic transaction so the per-department
     * ReentrantLock can be acquired before the transaction starts and released
     * only after it commits (see QueueServiceImpl#mutate).
     *
     * READ_COMMITTED: correctness comes from the explicit row lock on the
     * department, not from snapshot isolation, and it avoids InnoDB gap locks.
     */
    @Bean
    public TransactionTemplate queueTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(10);
        return template;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
