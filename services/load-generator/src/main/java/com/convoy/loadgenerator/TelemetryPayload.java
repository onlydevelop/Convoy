package com.convoy.loadgenerator;

public record TelemetryPayload(
        String vehicleId,
        String driverId,
        String timestamp,
        double latitude,
        double longitude,
        double speedKph,
        int headingDeg
) {
}
