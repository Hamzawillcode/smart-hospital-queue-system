package com.hospital.queue.config;

import com.hospital.common.events.DoctorAssignedEvent;
import com.hospital.common.events.PatientCheckedInEvent;
import com.hospital.common.events.QueueUpdatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config
        .ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer
        .JsonDeserializer;
import org.springframework.kafka.support.serializer
        .JsonSerializer;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ─────────────────────────────────────────
    // CONSUMER — PatientCheckedInEvent
    // ─────────────────────────────────────────
    @Bean
    public ConsumerFactory<String, PatientCheckedInEvent>
    patientEventConsumerFactory() {

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG,
                "queue-service-group");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES,
                "com.hospital.common.events");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                PatientCheckedInEvent.class.getName());

        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String,
            PatientCheckedInEvent>
    patientCheckedInListenerFactory() {

        ConcurrentKafkaListenerContainerFactory<String,
                PatientCheckedInEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(
                patientEventConsumerFactory());
        // 3 concurrent consumers = 3 partitions
        factory.setConcurrency(3);
        return factory;
    }

    // ─────────────────────────────────────────
    // PRODUCER — QueueUpdatedEvent
    // ─────────────────────────────────────────
    @Bean
    public ProducerFactory<String, QueueUpdatedEvent>
    queueUpdatedProducerFactory() {

        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, QueueUpdatedEvent>
    queueUpdatedTemplate() {
        return new KafkaTemplate<>(
                queueUpdatedProducerFactory());
    }

    // ─────────────────────────────────────────
    // PRODUCER — DoctorAssignedEvent
    // ─────────────────────────────────────────
    @Bean
    public ProducerFactory<String, DoctorAssignedEvent>
    doctorAssignedProducerFactory() {

        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, DoctorAssignedEvent>
    doctorAssignedTemplate() {
        return new KafkaTemplate<>(
                doctorAssignedProducerFactory());
    }
    // ─────────────────────────────────────────
    // PRODUCER — Default (Required for @RetryableTopic)
    // ─────────────────────────────────────────
    @Bean
    public ProducerFactory<String, Object> defaultRetryProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(config);
    }

    // Notice the exact Bean name here!
    @Bean(name = "defaultRetryTopicKafkaTemplate")
    public KafkaTemplate<String, Object> defaultRetryTopicKafkaTemplate() {
        return new KafkaTemplate<>(defaultRetryProducerFactory());
    }
}