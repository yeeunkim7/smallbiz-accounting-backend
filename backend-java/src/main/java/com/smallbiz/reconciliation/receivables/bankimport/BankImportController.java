package com.smallbiz.reconciliation.receivables.bankimport;

import com.smallbiz.reconciliation.receivables.ReceivablesException;
import com.smallbiz.reconciliation.receivables.ReceivablesService.Page;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class BankImportController {
    private final BankImportService service;
    public BankImportController(BankImportService service) { this.service=service; }
    @PostMapping(value="/bank-imports",consumes="multipart/form-data")
    public ResponseEntity<BankImportService.ImportResponse> upload(@PathVariable UUID tenantId,
            @RequestHeader(value="Idempotency-Key",required=false) String key,
            @RequestParam(required=false) String bankAccountKey,@RequestParam(required=false) MultipartFile file) {
        return ResponseEntity.status(201).body(service.importFile(tenantId,key,bankAccountKey,file));
    }
    @GetMapping("/bank-imports")
    public Page<BankImportService.ImportResponse> list(@PathVariable UUID tenantId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(tenantId,page,size);
    }
    @GetMapping("/bank-imports/{id}")
    public BankImportService.ImportResponse detail(@PathVariable UUID tenantId,@PathVariable UUID id) { return service.get(tenantId,id); }
    @GetMapping("/bank-imports/{id}/rows")
    public Page<BankImportService.SourceRowResponse> rows(@PathVariable UUID tenantId,@PathVariable UUID id,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.rows(tenantId,id,page,size); }
    @GetMapping("/payments/{id}/source")
    public BankImportService.SourceRowResponse source(@PathVariable UUID tenantId,@PathVariable UUID id) { return service.paymentSource(tenantId,id); }
    @ExceptionHandler(BankImportException.class)
    public ResponseEntity<Map<String,Object>> invalid(BankImportException ex) {
        Map<String,Object> body=new LinkedHashMap<>(); body.put("code",ex.code()); body.put("message",ex.getMessage());
        if (ex.recordNumber()!=null) body.put("recordNumber",ex.recordNumber());
        if (ex.field()!=null) body.put("field",ex.field());
        return ResponseEntity.status(ex.status()).body(body);
    }
    @ExceptionHandler(ReceivablesException.class)
    public ResponseEntity<Map<String,String>> invalidPage(ReceivablesException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("code",ex.code(),"message",ex.getMessage()));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String,String>> storage() {
        return ResponseEntity.status(503).body(Map.of("code","BANK_IMPORT_STORAGE_UNAVAILABLE","message","Bank import storage is unavailable."));
    }
}
