package com.hospital.doctor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.events.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

import java.time.Clock;

/** JSON-as-String Kafka wiring; see queue-service KafkaConfig for the rationale. */
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

    @Bean
    public NewTopic doctorAssignedTopic() {
        return TopicBuilder.name(KafkaTopics.DOCTOR_ASSIGNED).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic doctorStatusChangedTopic() {
        return TopicBuilder.name(KafkaTopics.DOCTOR_STATUS_CHANGED).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
