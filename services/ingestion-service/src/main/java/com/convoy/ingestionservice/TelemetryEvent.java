package com.convoy.ingestionservice;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TelemetryEvent(
        @NotBlank String vehicleId,
        @NotBlank String driverId,
        @NotBlank String timestamp,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @PositiveOrZero Double speedKph,
        @Min(0) @Max(359) Integer headingDeg
) {
}
