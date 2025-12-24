package com.geofleet.tracking.sse;

import com.geofleet.tracking.model.dto.VehicleEventDTO;
import com.geofleet.tracking.service.VehicleStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import jakarta.annotation.PostConstruct;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleSsePublisher {

    private final VehicleStatusService vehicleStatusService;
    private final Sinks.Many<VehicleEventDTO> sink = Sinks.many().replay().limit(1000);

    @PostConstruct
    public void init() {
        log.info("VehicleSsePublisher initialized");
    }

    public void publish(VehicleEventDTO event) {
        try {
            var status = vehicleStatusService.getVehicleStatus(event.getVehicleId());
            if (status != null) {
                event.setStatus(status.getStatus());
                event.setStatusColor(status.getStatusColor());
            }

            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                log.warn("Failed to emit vehicle event for {}: {}", event.getVehicleId(), result);
            } else {
                log.debug("Vehicle update published: {}", event.getVehicleId());
            }
        } catch (Exception e) {
            log.error("Error publishing vehicle event: {}", e.getMessage(), e);
        }
    }

    public Flux<VehicleEventDTO> stream() {
        return sink.asFlux();
    }
}