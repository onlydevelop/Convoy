package com.convoy.loadgenerator;

import java.util.concurrent.ThreadLocalRandom;

/**
 * In-memory simulated state for one vehicle. Each tick nudges position, speed
 * and heading by a small random delta so movement looks like a continuous
 * track rather than teleporting between unrelated points.
 */
final class VehicleState {

    private final String vehicleId;
    private final String driverId;
    private double latitude;
    private double longitude;
    private double speedKph;
    private int headingDeg;

    VehicleState(int index) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.vehicleId = "vehicle-" + index;
        this.driverId = "driver-" + index;
        // Roughly the contiguous continental US bounding box, as a plausible fleet operating area.
        this.latitude = random.nextDouble(25.0, 49.0);
        this.longitude = random.nextDouble(-124.0, -67.0);
        this.speedKph = random.nextDouble(0.0, 110.0);
        this.headingDeg = random.nextInt(0, 360);
    }

    TelemetryPayload step() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        headingDeg = Math.floorMod(headingDeg + random.nextInt(-10, 11), 360);
        speedKph = clamp(speedKph + random.nextDouble(-5.0, 5.0), 0.0, 120.0);

        double headingRad = Math.toRadians(headingDeg);
        double distanceDeg = (speedKph / 3600.0) * 10.0 / 111.0; // ~10s worth of travel, in degrees latitude
        latitude = clamp(latitude + Math.cos(headingRad) * distanceDeg, -90.0, 90.0);
        longitude = wrapLongitude(longitude + Math.sin(headingRad) * distanceDeg);

        return new TelemetryPayload(
                vehicleId, driverId, java.time.Instant.now().toString(),
                latitude, longitude, speedKph, headingDeg);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double wrapLongitude(double value) {
        double wrapped = ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return wrapped;
    }
}
