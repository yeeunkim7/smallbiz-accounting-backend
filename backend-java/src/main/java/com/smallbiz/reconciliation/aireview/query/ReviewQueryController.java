package com.smallbiz.reconciliation.aireview.query;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smallbiz.reconciliation.reconciliation.BankOriginalListResponse;
import com.smallbiz.reconciliation.reconciliation.BusinessOriginalListResponse;
import com.smallbiz.reconciliation.upload.UploadFileListResponse;

@RestController
@RequestMapping("/api/v1/ai-review")
public class ReviewQueryController {
    private final ReadOnlyReviewQueries queries;
    public ReviewQueryController(ReadOnlyReviewQueries queries) { this.queries = queries; }

    @GetMapping("/summary")
    public DailySummaryResponse summary(@RequestParam String date) { return queries.summary(date); }

    @GetMapping("/originals/business")
    public BusinessOriginalListResponse business(@RequestParam String from, @RequestParam String to,
            @RequestParam(required = false) String direction, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.business(from, to, direction, page, size);
    }

    @GetMapping("/originals/bank")
    public BankOriginalListResponse bank(@RequestParam String from, @RequestParam String to,
            @RequestParam(required = false) String direction, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.bank(from, to, direction, page, size);
    }

    @GetMapping("/uploads")
    public UploadFileListResponse uploads(@RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return queries.uploads(from, to, page, size);
    }
}
