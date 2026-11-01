package com.smallbiz.reconciliation.aireview.ai;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public record AiReviewResponse(
        String date, String model, Instant generatedAt, JsonNode summary,
        List<Statement> facts, List<Statement> possibleCauses, List<Statement> checks,
        List<Evidence> evidence, List<ToolTrace> toolTrace, List<String> limitations) {
    public record Statement(String text, List<String> evidenceIds) { }
    public record Evidence(String id, String kind, JsonNode data) { }
    public record ToolTrace(String tool, Map<String,Object> arguments, long returnedCount,
                            long totalElements, boolean truncated) { }
}
