package com.geofleet.tracking.kafka.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VehicleStatsAggregateSerde implements Serde<VehicleStatsAggregate> {

    private final ObjectMapper objectMapper;

    @Override
    public Serializer<VehicleStatsAggregate> serializer() {
        return new Serializer<VehicleStatsAggregate>() {
            @Override
            public void configure(Map<String, ?> configs, boolean isKey) {
            }

            @Override
            public byte[] serialize(String topic, VehicleStatsAggregate data) {
                try {
                    return objectMapper.writeValueAsBytes(data);
                } catch (IOException e) {
                    throw new RuntimeException("Error serializing VehicleStatsAggregate", e);
                }
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public Deserializer<VehicleStatsAggregate> deserializer() {
        return new Deserializer<VehicleStatsAggregate>() {
            @Override
            public void configure(Map<String, ?> configs, boolean isKey) {
            }

            @Override
            public VehicleStatsAggregate deserialize(String topic, byte[] data) {
                try {
                    if (data == null) {
                        return new VehicleStatsAggregate();
                    }
                    return objectMapper.readValue(data, VehicleStatsAggregate.class);
                } catch (IOException e) {
                    throw new RuntimeException("Error deserializing VehicleStatsAggregate", e);
                }
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
    }

    @Override
    public void close() {
    }
}