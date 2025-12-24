package com.geofleet.tracking.controller;

import com.geofleet.tracking.model.dto.AlertEventDTO;
import com.geofleet.tracking.model.dto.VehicleEventDTO;
import com.geofleet.tracking.sse.AlertSsePublisher;
import com.geofleet.tracking.sse.VehicleSsePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/stream")
@RequiredArgsConstructor
public class SseController {

        private final VehicleSsePublisher vehiclePublisher;
        private final AlertSsePublisher alertPublisher;

        @GetMapping(value = "/vehicles", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public Flux<ServerSentEvent<VehicleEventDTO>> streamVehicles() {
                // Send immediate connection confirmation
                VehicleEventDTO connectionData = new VehicleEventDTO();
                connectionData.setMessage("SSE connection established");
                connectionData.setTimestamp(LocalDateTime.now());
                connectionData.setStatus("CONNECTED");
                Mono<ServerSentEvent<VehicleEventDTO>> connectionConfirmation = Mono.just(
                                ServerSentEvent.<VehicleEventDTO>builder()
                                                .event("connected")
                                                .data(connectionData)
                                                .build());

                return connectionConfirmation
                                .concatWith(
                                                vehiclePublisher.stream()
                                                                .map(event -> ServerSentEvent.<VehicleEventDTO>builder()
                                                                                .event("vehicle-update")
                                                                                .data(event)
                                                                                .build())
                                                                .mergeWith(heartbeatFlux()))
                                .doOnSubscribe(sub -> log.info("✅ New SSE connection for vehicles"))
                                .doOnCancel(() -> log.info("🔴 Vehicle SSE connection closed"))
                                .doOnError(e -> log.error("❌ Vehicle SSE error: {}", e.getMessage()))
                                .onErrorResume(e -> {
                                        log.error("Vehicle SSE connection error, resuming with empty: {}",
                                                        e.getMessage());
                                        return Flux.empty();
                                });
        }

        @GetMapping(value = "/alerts", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public Flux<ServerSentEvent<AlertEventDTO>> streamAlerts() {
                log.info("🌐 [SSE-CONTROLLER] New alert SSE connection requested");
                // Send immediate connection confirmation
                AlertEventDTO connectionData = new AlertEventDTO();
                connectionData.setMessage("SSE connection established");
                connectionData.setTimestamp(LocalDateTime.now());
                connectionData.setStatus("CONNECTED");
                Mono<ServerSentEvent<AlertEventDTO>> connectionConfirmation = Mono.just(
                                ServerSentEvent.<AlertEventDTO>builder()
                                                .event("connected")
                                                .data(connectionData)
                                                .build());

                return connectionConfirmation
                                .concatWith(
                                                alertPublisher.stream()
                                                                .map(alert -> ServerSentEvent.<AlertEventDTO>builder()
                                                                                .event("alert")
                                                                                .data(alert)
                                                                                .build())
                                                                .mergeWith(heartbeatFlux()))
                                .doOnSubscribe(sub -> log.info("✅ New SSE connection for alerts"))
                                .doOnCancel(() -> log.info("🔴 Alert SSE connection closed"))
                                .doOnError(e -> log.error("❌ Alert SSE error: {}", e.getMessage()))
                                .onErrorResume(e -> {
                                        log.error("Alert SSE connection error, resuming with empty: {}",
                                                        e.getMessage());
                                        return Flux.empty();
                                });
        }

        // REQUIRED: Keep-alive heartbeat for reconnection (30 seconds)
        private <T> Flux<ServerSentEvent<T>> heartbeatFlux() {
                return Flux.interval(Duration.ofSeconds(30))
                                .map(seq -> ServerSentEvent.<T>builder()
                                                .event("keepalive")
                                                .comment("heartbeat")
                                                .build())
                                .doOnNext(hb -> log.trace("Sent heartbeat"));
        }
}