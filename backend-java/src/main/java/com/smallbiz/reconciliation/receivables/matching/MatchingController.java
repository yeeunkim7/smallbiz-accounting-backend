package com.smallbiz.reconciliation.receivables.matching;

import com.smallbiz.reconciliation.receivables.ReceivablesException;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/matching-runs")
public class MatchingController {
    private final MatchingService service;
    public MatchingController(MatchingService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<MatchingService.RunResponse> create(@PathVariable UUID tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(service.create(tenantId, key, body));
    }
    @GetMapping("/{id}")
    public MatchingService.RunResponse get(@PathVariable UUID tenantId, @PathVariable UUID id) { return service.get(tenantId, id); }
    @GetMapping
    public MatchingService.Page list(@PathVariable UUID tenantId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.list(tenantId, page, size); }
    @ExceptionHandler(MatchingException.class)
    public ResponseEntity<Map<String, String>> error(MatchingException ex) {
        return ResponseEntity.status(ex.status).body(Map.of("code", ex.code, "message", ex.getMessage()));
    }
    @ExceptionHandler(ReceivablesException.class)
    public ResponseEntity<Map<String, String>> invalid(ReceivablesException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("code", ex.code(), "message", ex.getMessage()));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> storage() {
        return ResponseEntity.status(503).body(Map.of("code", "MATCHING_STORAGE_UNAVAILABLE", "message", "Matching storage is unavailable."));
    }
}
