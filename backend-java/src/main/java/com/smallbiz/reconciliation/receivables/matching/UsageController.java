package com.smallbiz.reconciliation.receivables.matching;

import com.smallbiz.reconciliation.receivables.ReceivablesException;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/matching-usage")
public class UsageController {
    private final UsageService service;
    public UsageController(UsageService service) { this.service = service; }
    @GetMapping
    public UsageService.Summary current(@PathVariable UUID tenantId, @RequestParam Map<String,String> query) {
        com.smallbiz.reconciliation.receivables.ReceivablesValidation.fields(new java.util.HashMap<>(query), java.util.Set.of());
        return service.current(tenantId);
    }
    @ExceptionHandler(ReceivablesException.class)
    public ResponseEntity<Map<String,String>> invalid(ReceivablesException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("code", ex.code(), "message", ex.getMessage()));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String,String>> storage() {
        return ResponseEntity.status(503).body(Map.of("code", "MATCHING_STORAGE_UNAVAILABLE", "message", "Matching storage is unavailable."));
    }
}
