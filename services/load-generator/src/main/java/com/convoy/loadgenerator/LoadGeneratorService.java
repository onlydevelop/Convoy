package com.convoy.loadgenerator;

import com.convoy.telemetry.TelemetryEvent;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Simulates the fleet: each vehicle pings once per {@code ping-interval-seconds}.
 * Vehicles are bucketed by index so pings are spread evenly across the
 * interval window (one bucket fired per second) instead of bursting all at
 * once, matching the sustained-rate assumption in spec §3.
 */
@Service
public class LoadGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(LoadGeneratorService.class);

    private final WebClient webClient;
    private final int vehicleCount;
    private final int pingIntervalSeconds;
    private final AtomicLong tick = new AtomicLong();
    private List<List<VehicleState>> buckets;

    public LoadGeneratorService(
            WebClient ingestionWebClient,
            @Value("${app.simulation.vehicle-count}") int vehicleCount,
            @Value("${app.simulation.ping-interval-seconds}") int pingIntervalSeconds) {
        this.webClient = ingestionWebClient;
        this.vehicleCount = vehicleCount;
        this.pingIntervalSeconds = pingIntervalSeconds;
    }

    @PostConstruct
    void initFleet() {
        buckets = new ArrayList<>(pingIntervalSeconds);
        for (int i = 0; i < pingIntervalSeconds; i++) {
            buckets.add(new ArrayList<>());
        }
        for (int i = 0; i < vehicleCount; i++) {
            buckets.get(i % pingIntervalSeconds).add(new VehicleState(i));
        }
        log.info("Initialized {} simulated vehicles across {} buckets", vehicleCount, pingIntervalSeconds);
    }

    @Scheduled(fixedRate = 1000)
    void tick() {
        int bucket = (int) (tick.getAndIncrement() % pingIntervalSeconds);
        for (VehicleState vehicle : buckets.get(bucket)) {
            TelemetryEvent payload = vehicle.step();
            webClient.post()
                    .uri("/v1/telemetry")
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .subscribe(
                            response -> { },
                            error -> log.warn("Telemetry POST failed for {}: {}", payload.vehicleId(), error.getMessage()));
        }
    }
}
