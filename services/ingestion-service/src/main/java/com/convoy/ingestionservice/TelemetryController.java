package com.convoy.ingestionservice;

import com.convoy.telemetry.TelemetryEvent;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1")
public class TelemetryController {

    private final KafkaTemplate<String, TelemetryEvent> kafkaTemplate;
    private final String topic;

    public TelemetryController(
            KafkaTemplate<String, TelemetryEvent> kafkaTemplate,
            @Value("${app.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @PostMapping("/telemetry")
    public ResponseEntity<Void> ingest(@Valid @RequestBody TelemetryEvent event) {
        try {
            Instant.parse(event.timestamp());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timestamp must be ISO 8601", e);
        }

        try {
            // Synchronous produce (acks=1 per config) so a 202 means durably queued, per spec §5.
            kafkaTemplate.send(topic, event.vehicleId(), event).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "queue unreachable", e);
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
