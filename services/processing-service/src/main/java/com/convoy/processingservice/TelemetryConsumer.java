package com.convoy.processingservice;

import com.convoy.telemetry.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes telemetry off the Kafka topic. Intentionally a no-op beyond
 * logging receipt for now - enrichment and DB persistence are a future spec.
 */
@Component
public class TelemetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConsumer.class);

    @KafkaListener(topics = "${app.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(TelemetryEvent event) {
        log.info("Received telemetry for vehicle {}", event.vehicleId());
    }
}
