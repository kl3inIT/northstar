package com.northstar.core.cache;

import java.util.List;

public record SemanticCacheDecision(boolean eligible, List<SemanticCacheRejection> rejections) {

    public SemanticCacheDecision {
        rejections = rejections == null ? List.of() : List.copyOf(rejections);
        if (eligible == !rejections.isEmpty()) {
            throw new IllegalArgumentException("Eligibility must match rejection state");
        }
    }
}
