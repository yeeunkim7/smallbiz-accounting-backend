package com.smallbiz.reconciliation.aireview.ai;

import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-review")
public class AiReviewController {
    private final AiReviewService service;
    public AiReviewController(AiReviewService service) { this.service = service; }
    @GetMapping("/capabilities")
    public Map<String,Object> capabilities() { return service.capabilities(); }
    @PostMapping("/reviews")
    public AiReviewResponse review(@Valid @RequestBody ReviewRequest request) { return service.review(request.date()); }
    @ExceptionHandler(AiReviewException.class)
    public ResponseEntity<Map<String,String>> error(AiReviewException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("code", ex.code(), "message", ex.getMessage()));
    }
    public record ReviewRequest(@NotBlank String date) { }
}
