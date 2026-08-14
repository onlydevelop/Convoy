package com.convoy.loadgenerator;

import com.convoy.telemetry.TelemetryEvent;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Simulates the fleet: once per second, sends a random number of pings
 * between {@code min-requests-per-second} and {@code max-requests-per-second}
 * (spec §3's ~1,000 events/sec is a sustained average, not a fixed rate -
 * this exercises the burst variability called out but not yet quantified
 * there). Vehicles are drawn round-robin off a rotating cursor rather than
 * resampled each tick, so load stays random in volume while every vehicle
 * still gets an even share of pings over time.
 */
@Service
public class LoadGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(LoadGeneratorService.class);

    private final WebClient webClient;
    private final int vehicleCount;
    private final int minRequestsPerSecond;
    private final int maxRequestsPerSecond;
    private int cursor;
    private List<VehicleState> fleet;

    public LoadGeneratorService(
            WebClient ingestionWebClient,
            @Value("${app.simulation.vehicle-count}") int vehicleCount,
            @Value("${app.simulation.min-requests-per-second}") int minRequestsPerSecond,
            @Value("${app.simulation.max-requests-per-second}") int maxRequestsPerSecond) {
        this.webClient = ingestionWebClient;
        this.vehicleCount = vehicleCount;
        this.minRequestsPerSecond = minRequestsPerSecond;
        this.maxRequestsPerSecond = maxRequestsPerSecond;
    }

    @PostConstruct
    void initFleet() {
        fleet = new ArrayList<>(vehicleCount);
        for (int i = 0; i < vehicleCount; i++) {
            fleet.add(new VehicleState(i));
        }
        log.info("Initialized {} simulated vehicles, sending {}-{} req/sec", vehicleCount, minRequestsPerSecond, maxRequestsPerSecond);
    }

    @Scheduled(fixedRate = 1000)
    void tick() {
        int target = ThreadLocalRandom.current().nextInt(minRequestsPerSecond, maxRequestsPerSecond + 1);
        for (int i = 0; i < target; i++) {
            VehicleState vehicle = fleet.get(cursor);
            cursor = (cursor + 1) % vehicleCount;

            TelemetryEvent payload = vehicle.step();
            log.info("Sending telemetry for vehicle {}", payload.vehicleId());
            webClient.post()
                    .uri("/v1/telemetry")
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .subscribe(
                            response -> log.info("Telemetry accepted for vehicle {}: {}", payload.vehicleId(), response.getStatusCode()),
                            error -> log.warn("Telemetry POST failed for {}: {}", payload.vehicleId(), error.getMessage()));
        }
    }
}
