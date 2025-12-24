package com.geofleet.tracking.sse;

import com.geofleet.tracking.model.dto.AlertEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import jakarta.annotation.PostConstruct;

@Slf4j
@Service
public class AlertSsePublisher {

    private final Sinks.Many<AlertEventDTO> sink = Sinks.many().replay().limit(1000);

    @PostConstruct
    public void init() {
        log.info("AlertSsePublisher initialized");
    }

    public void publish(AlertEventDTO alert) {
        try {
            log.info("📡 [SSE-PUBLISHER] Publishing alert via SSE: {} for vehicle {}",
                    alert.getAlertType(), alert.getVehicleId());

            Sinks.EmitResult result = sink.tryEmitNext(alert);
            if (result.isFailure()) {
                log.warn("⚠️ [SSE-PUBLISHER] Failed to emit alert for {}: {}",
                        alert.getVehicleId(), result);
            } else {
                log.info("✅ [SSE-PUBLISHER] Alert successfully published to SSE: {} - {}",
                        alert.getVehicleId(), alert.getAlertType());
            }
        } catch (Exception e) {
            log.error("❌ [SSE-PUBLISHER] Error publishing alert: {}", e.getMessage(), e);
        }
    }

    public Flux<AlertEventDTO> stream() {
        return sink.asFlux();
    }
}