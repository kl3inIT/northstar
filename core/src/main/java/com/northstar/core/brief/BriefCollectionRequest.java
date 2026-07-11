package com.northstar.core.brief;

import java.time.Instant;
import java.util.List;

/** Bounded, secret-free inputs shared by all Morning Brief source adapters. */
public record BriefCollectionRequest(
        Instant since,
        int maxItems,
        List<String> topics,
        List<String> queries,
        List<String> blockedDomains,
        List<String> githubRepositories,
        List<String> feedUrls,
        List<String> blueskyHandles,
        int firecrawlCreditBudget) {

    public BriefCollectionRequest {
        topics = safe(topics);
        queries = safe(queries);
        blockedDomains = safe(blockedDomains);
        githubRepositories = safe(githubRepositories);
        feedUrls = safe(feedUrls);
        blueskyHandles = safe(blueskyHandles);
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
