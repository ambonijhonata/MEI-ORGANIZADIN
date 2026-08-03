package com.api.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    // Reuses a single immutable response for the health check endpoint.
    private final ResponseEntity<Boolean> healthyResponse;

    public HealthController() {
        this.healthyResponse = ResponseEntity.ok(Boolean.TRUE);
    }

    @GetMapping("/healthz")
    public ResponseEntity<Boolean> healthz() {
        return this.healthyResponse;
    }
}
