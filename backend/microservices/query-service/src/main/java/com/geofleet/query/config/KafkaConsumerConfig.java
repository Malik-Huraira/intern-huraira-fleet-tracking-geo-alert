package com.geofleet.query.config;

import com.geofleet.common.dto.AlertEventDTO;
import com.geofleet.common.dto.VehicleEventDTO;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@SuppressWarnings("null")
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }

    @Bean
    public ConsumerFactory<String, VehicleEventDTO> vehicleEventConsumerFactory() {
        JsonDeserializer<VehicleEventDTO> deserializer = new JsonDeserializer<>(VehicleEventDTO.class, false);
        deserializer.addTrustedPackages("*");
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(), new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, VehicleEventDTO> vehicleKafkaListenerContainerFactory(
            CommonErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, VehicleEventDTO> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(vehicleEventConsumerFactory());
        factory.setCommonErrorHandler(kafkaErrorHandler); // DLQ support
        return factory;
    }

    @Bean
    public ConsumerFactory<String, AlertEventDTO> alertEventConsumerFactory() {
        JsonDeserializer<AlertEventDTO> deserializer = new JsonDeserializer<>(AlertEventDTO.class, false);
        deserializer.addTrustedPackages("*");
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(), new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AlertEventDTO> alertKafkaListenerContainerFactory(
            CommonErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, AlertEventDTO> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(alertEventConsumerFactory());
        factory.setCommonErrorHandler(kafkaErrorHandler); // DLQ support
        return factory;
    }
}
