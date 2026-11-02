package com.smallbiz.reconciliation.receivables.allocation;

import com.smallbiz.reconciliation.receivables.ReceivablesException;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class AllocationController {
    private final AllocationService service;
    public AllocationController(AllocationService service) { this.service = service; }
    @PostMapping("/allocations")
    public ResponseEntity<AllocationService.AllocationResponse> confirm(@PathVariable UUID tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(service.confirm(tenantId, key, body));
    }
    @PostMapping("/allocations/{id}/cancel")
    public AllocationService.AllocationResponse cancel(@PathVariable UUID tenantId, @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody Map<String, Object> body) {
        return service.cancel(tenantId, id, key, body);
    }
    @GetMapping("/allocations/{id}")
    public AllocationService.AllocationResponse get(@PathVariable UUID tenantId, @PathVariable UUID id) { return service.get(tenantId, id); }
    @GetMapping("/allocations")
    public AllocationService.Page list(@PathVariable UUID tenantId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.list(tenantId, page, size); }
    @GetMapping("/invoices/{id}/balance")
    public AllocationService.InvoiceBalance invoice(@PathVariable UUID tenantId, @PathVariable UUID id, @RequestParam String asOf) {
        return service.invoiceBalance(tenantId, id, asOf);
    }
    @GetMapping("/payments/{id}/balance")
    public AllocationService.PaymentBalance payment(@PathVariable UUID tenantId, @PathVariable UUID id) { return service.paymentBalance(tenantId, id); }
    @GetMapping("/receivables/summary")
    public AllocationService.Summary summary(@PathVariable UUID tenantId, @RequestParam String asOf) { return service.summary(tenantId, asOf); }
    @ExceptionHandler(AllocationException.class)
    public ResponseEntity<Map<String, String>> error(AllocationException ex) {
        return ResponseEntity.status(ex.status).body(Map.of("code", ex.code, "message", ex.getMessage()));
    }
    @ExceptionHandler(ReceivablesException.class)
    public ResponseEntity<Map<String, String>> inputError(ReceivablesException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("code", ex.code(), "message", ex.getMessage()));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> storageError() {
        return ResponseEntity.status(503).body(Map.of("code", "ALLOCATION_STORAGE_UNAVAILABLE", "message", "Allocation storage is unavailable."));
    }
}
