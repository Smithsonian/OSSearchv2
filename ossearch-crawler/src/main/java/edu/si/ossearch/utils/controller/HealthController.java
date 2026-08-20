package edu.si.ossearch.utils.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authenticated mirror of {@code /actuator/health}. The admin UI cannot reach
 * {@code /actuator/**} through the load balancer (the WAF blocks it with 403),
 * so the dashboard and backend-status pages read health from this endpoint,
 * which is proxied and secured like every other {@code /api} route.
 *
 * @author jbirkhimer
 */
@Slf4j
@RestController
@RequestMapping("/api/utils")
@Tag(description = "Utils | Health", name = "Utils | Health")
@SecurityRequirement(name = "bearerAuth")
public class HealthController {

    @Autowired
    HealthEndpoint healthEndpoint;

    @Operation(summary = "Backend health with component details (same JSON shape as /actuator/health)")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        HealthComponent health = healthEndpoint.health();
        HttpStatus httpStatus = Status.DOWN.equals(health.getStatus()) || Status.OUT_OF_SERVICE.equals(health.getStatus())
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(toMap(health));
    }

    private Map<String, Object> toMap(HealthComponent component) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", component.getStatus().getCode());
        if (component instanceof CompositeHealth composite) {
            Map<String, Object> components = new LinkedHashMap<>();
            composite.getComponents().forEach((name, child) -> components.put(name, toMap(child)));
            map.put("components", components);
        } else if (component instanceof Health health && !health.getDetails().isEmpty()) {
            map.put("details", health.getDetails());
        }
        return map;
    }
}
