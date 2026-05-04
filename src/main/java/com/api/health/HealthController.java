package com.api.health;

import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/healthz")
    public ResponseEntity<HealthResponse> healthz() {
        return ResponseEntity.ok(new HealthResponse("ok", Instant.now().toString()));
    }

    public record HealthResponse(String status, String timestamp) {}
}
