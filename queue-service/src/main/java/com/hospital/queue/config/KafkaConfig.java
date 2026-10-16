package com.hospital.queue.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.events.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Kafka wiring.
 *
 * Values travel as JSON strings. Producers send the JSON written to the outbox;
 * consumers receive a String and {@link StringJsonMessageConverter} converts it to
 * the listener's parameter type. Because the record value is always a String,
 * retry and dead-letter republishing work with Boot's default String KafkaTemplate,
 * and a malformed payload fails conversion and goes straight to the DLT
 * (conversion errors are non-retryable).
 */
@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.partitions}")
    private int partitions;

    @Value("${kafka.topics.replicas}")
    private int replicas;

    @Bean
    public RecordMessageConverter recordMessageConverter(ObjectMapper objectMapper) {
        return new StringJsonMessageConverter(objectMapper);
    }

    /**
     * Compacted: Kafka keeps at least the latest snapshot per department, so a
     * notification instance starting with a fresh consumer group can rebuild
     * its view from the topic itself.
     */
    @Bean
    public NewTopic queueUpdatedTopic() {
        return TopicBuilder.name(KafkaTopics.QUEUE_UPDATED)
                .partitions(partitions)
                .replicas(replicas)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .config(TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG, "3600000")
                .build();
    }

    @Bean
    public NewTopic doctorRequestedTopic() {
        return TopicBuilder.name(KafkaTopics.DOCTOR_REQUESTED).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic doctorReleasedTopic() {
        return TopicBuilder.name(KafkaTopics.DOCTOR_RELEASED).partitions(partitions).replicas(replicas).build();
    }
}
