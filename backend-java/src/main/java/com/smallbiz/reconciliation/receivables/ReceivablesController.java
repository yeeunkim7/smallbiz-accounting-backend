package com.smallbiz.reconciliation.receivables;

import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class ReceivablesController {
    private final ReceivablesService service;
    public ReceivablesController(ReceivablesService service) { this.service = service; }

    @PostMapping("/counterparties")
    public ResponseEntity<ReceivablesService.CounterpartyResponse> createCounterparty(@PathVariable UUID tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(service.createCounterparty(tenantId, key, body));
    }
    @GetMapping("/counterparties")
    public ReceivablesService.Page<ReceivablesService.CounterpartyResponse> counterparties(@PathVariable UUID tenantId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listCounterparties(tenantId, page, size);
    }
    @GetMapping("/counterparties/{id}")
    public ReceivablesService.CounterpartyResponse counterparty(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return service.getCounterparty(tenantId, id);
    }
    @PostMapping("/invoices")
    public ResponseEntity<ReceivablesService.InvoiceResponse> createInvoice(@PathVariable UUID tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(service.createInvoice(tenantId, key, body));
    }
    @GetMapping("/invoices")
    public ReceivablesService.Page<ReceivablesService.InvoiceResponse> invoices(@PathVariable UUID tenantId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID counterpartyId) {
        return service.listInvoices(tenantId, page, size, counterpartyId);
    }
    @GetMapping("/invoices/{id}")
    public ReceivablesService.InvoiceResponse invoice(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return service.getInvoice(tenantId, id);
    }
    @PostMapping("/payments")
    public ResponseEntity<ReceivablesService.PaymentResponse> createPayment(@PathVariable UUID tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(service.createPayment(tenantId, key, body));
    }
    @GetMapping("/payments")
    public ReceivablesService.Page<ReceivablesService.PaymentResponse> payments(@PathVariable UUID tenantId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listPayments(tenantId, page, size);
    }
    @GetMapping("/payments/{id}")
    public ReceivablesService.PaymentResponse payment(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return service.getPayment(tenantId, id);
    }
    @ExceptionHandler(ReceivablesException.class)
    public ResponseEntity<Map<String, String>> invalid(ReceivablesException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("code", ex.code(), "message", ex.getMessage()));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> storageUnavailable() {
        // SQL exception messages can contain financial input; do not log or return them.
        return ResponseEntity.status(503).body(Map.of("code", "RECEIVABLE_STORAGE_UNAVAILABLE", "message", "Receivables storage is unavailable."));
    }
}
