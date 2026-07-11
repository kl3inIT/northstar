package com.northstar.core.brief;

import java.util.List;
import java.util.Map;

/** Items plus small, non-secret run metrics from one source adapter. */
public record BriefSourceResult(List<BriefCandidate> items, Map<String, Object> metrics) {

    public BriefSourceResult {
        items = items == null ? List.of() : List.copyOf(items);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }

    public static BriefSourceResult of(List<BriefCandidate> items) {
        return new BriefSourceResult(items, Map.of());
    }
}
