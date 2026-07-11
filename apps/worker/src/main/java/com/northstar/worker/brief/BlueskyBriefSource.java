package com.northstar.worker.brief;

import static com.northstar.worker.brief.BriefSourceSupport.instant;
import static com.northstar.worker.brief.BriefSourceSupport.integer;
import static com.northstar.worker.brief.BriefSourceSupport.list;
import static com.northstar.worker.brief.BriefSourceSupport.map;
import static com.northstar.worker.brief.BriefSourceSupport.matchesTopics;
import static com.northstar.worker.brief.BriefSourceSupport.plainText;
import static com.northstar.worker.brief.BriefSourceSupport.recent;
import static com.northstar.worker.brief.BriefSourceSupport.string;
import static com.northstar.worker.brief.BriefSourceSupport.uri;

import com.northstar.core.brief.BriefCandidate;
import com.northstar.core.brief.BriefCollectionRequest;
import com.northstar.core.brief.BriefKind;
import com.northstar.core.brief.BriefSourceProvider;
import com.northstar.core.brief.BriefSourceResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class BlueskyBriefSource implements BriefSourceProvider {

    private static final String API = "https://public.api.bsky.app";

    private final BriefHttpClient http;
    private final ObjectMapper json;

    BlueskyBriefSource(BriefHttpClient http, ObjectMapper json) {
        this.http = http;
        this.json = json;
    }

    @Override
    public String id() {
        return "bluesky";
    }

    @Override
    public String displayName() {
        return "Bluesky people";
    }

    @Override
    public BriefSourceResult collect(BriefCollectionRequest request) {
        if (request.blueskyHandles().isEmpty()) return BriefSourceResult.of(List.of());
        int concurrency = Math.min(4, request.blueskyHandles().size());
        List<HandleOutcome> outcomes;
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency, Thread.ofVirtual().factory())) {
            List<Callable<HandleOutcome>> calls = request.blueskyHandles().stream()
                    .<Callable<HandleOutcome>>map(handle -> () -> collectHandle(handle, request)).toList();
            outcomes = executor.invokeAll(calls).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    return new HandleOutcome(List.of(), exception);
                }
            }).toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bluesky collection was interrupted", exception);
        }
        long failed = outcomes.stream().filter(outcome -> outcome.failure() != null).count();
        if (failed == outcomes.size()) throw new IllegalStateException("Every configured Bluesky handle failed");
        List<BriefCandidate> items = outcomes.stream().flatMap(outcome -> outcome.items().stream())
                .limit(request.maxItems() * 2L).toList();
        return new BriefSourceResult(items, Map.of(
                "handles", request.blueskyHandles().size(),
                "failedHandles", failed));
    }

    private HandleOutcome collectHandle(String handle, BriefCollectionRequest request) {
        try {
            String body = http.get(uri(API, "/xrpc/app.bsky.feed.getAuthorFeed", Map.of(
                    "actor", handle,
                    "limit", "15",
                    "filter", "posts_no_replies")), "application/json", Map.of());
            Map<?, ?> response = map(json.readValue(body, Map.class));
            List<BriefCandidate> items = new ArrayList<>();
            for (Object value : list(response.get("feed"))) {
                Map<?, ?> entry = map(value);
                Map<?, ?> post = map(entry.get("post"));
                Map<?, ?> record = map(post.get("record"));
                Map<?, ?> author = map(post.get("author"));
                String text = string(record.get("text"));
                Instant publishedAt = instant(record.get("createdAt"));
                if (text.isBlank() || !recent(publishedAt, request.since())
                        || !matchesTopics(text, request.topics())) continue;
                String postHandle = string(author.get("handle"));
                if (postHandle.isBlank()) postHandle = handle;
                String postUri = string(post.get("uri"));
                if (postUri.isBlank() || postUri.endsWith("/")) continue;
                String rkey = postUri.substring(postUri.lastIndexOf('/') + 1);
                String url = "https://bsky.app/profile/" + postHandle + "/post/" + rkey;
                String displayName = string(author.get("displayName"));
                if (displayName.isBlank()) displayName = postHandle;
                int engagement = integer(post.get("likeCount")) + integer(post.get("repostCount"))
                        + integer(post.get("replyCount"));
                items.add(new BriefCandidate(BriefKind.PEOPLE, plainText(text, 120), url,
                        plainText(text, 480), "Bluesky", displayName, publishedAt, engagement));
            }
            return new HandleOutcome(items, null);
        } catch (Exception exception) {
            return new HandleOutcome(List.of(), exception);
        }
    }

    private record HandleOutcome(List<BriefCandidate> items, Exception failure) {
    }
}
