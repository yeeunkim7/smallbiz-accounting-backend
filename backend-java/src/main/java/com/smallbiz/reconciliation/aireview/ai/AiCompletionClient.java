package com.smallbiz.reconciliation.aireview.ai;

import java.util.Map;
import tools.jackson.databind.JsonNode;

public interface AiCompletionClient {
    JsonNode complete(Map<String, Object> request);
    boolean configured();
    String model();
}
