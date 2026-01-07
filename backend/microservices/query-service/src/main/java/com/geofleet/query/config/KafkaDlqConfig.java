package com.geofleet.query.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Dead Letter Queue (DLQ) configuration for Query Service Kafka consumers.
 * Failed messages are sent to vehicle-gps-dlq topic after 3 retries.
 */
@Configuration
@Slf4j
@SuppressWarnings({ "null", "unchecked" })
public class KafkaDlqConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private static final String DLQ_TOPIC = "vehicle-gps-dlq";
    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long MAX_RETRIES = 3L;

    @Bean
    public ProducerFactory<String, String> dlqProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> dlqKafkaTemplate() {
        return new KafkaTemplate<>(dlqProducerFactory());
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer() {
        return new DeadLetterPublishingRecoverer(
                (KafkaOperations<Object, Object>) (KafkaOperations<?, ?>) dlqKafkaTemplate(),
                (record, exception) -> {
                    log.error("Query Service - Sending message to DLQ. Topic: {}, Key: {}, Error: {}",
                            record.topic(), record.key(), exception.getMessage());
                    return new org.apache.kafka.common.TopicPartition(DLQ_TOPIC, -1);
                });
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Query Service - Retry attempt {} for record key: {} from topic: {}",
                    deliveryAttempt, record.key(), record.topic());
        });

        errorHandler.addNotRetryableExceptions(
                org.apache.kafka.common.errors.SerializationException.class,
                org.springframework.messaging.converter.MessageConversionException.class);

        log.info("Query Service DLQ configured: {} retries, DLQ topic: {}", MAX_RETRIES, DLQ_TOPIC);
        return errorHandler;
    }
}
