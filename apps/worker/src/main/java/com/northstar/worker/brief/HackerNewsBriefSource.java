package com.northstar.worker.brief;

import static com.northstar.worker.brief.BriefSourceSupport.integer;
import static com.northstar.worker.brief.BriefSourceSupport.map;
import static com.northstar.worker.brief.BriefSourceSupport.matchesTopics;
import static com.northstar.worker.brief.BriefSourceSupport.string;

import com.northstar.core.brief.BriefCandidate;
import com.northstar.core.brief.BriefCollectionRequest;
import com.northstar.core.brief.BriefKind;
import com.northstar.core.brief.BriefSourceProvider;
import com.northstar.core.brief.BriefSourceResult;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class HackerNewsBriefSource implements BriefSourceProvider {

    private static final String API = "https://hacker-news.firebaseio.com/v0";

    private final BriefHttpClient http;
    private final ObjectMapper json;

    HackerNewsBriefSource(BriefHttpClient http, ObjectMapper json) {
        this.http = http;
        this.json = json;
    }

    @Override
    public String id() {
        return "hacker-news";
    }

    @Override
    public String displayName() {
        return "Hacker News";
    }

    @Override
    public BriefSourceResult collect(BriefCollectionRequest request) {
        try {
            Set<Long> ids = new LinkedHashSet<>();
            ids.addAll(ids("topstories"));
            ids.addAll(ids("beststories"));
            List<Long> shortlist = ids.stream().limit(40).toList();
            List<BriefCandidate> items = collectItems(shortlist, request);
            return new BriefSourceResult(items.stream().limit(request.maxItems() * 2L).toList(),
                    Map.of("storiesChecked", shortlist.size()));
        } catch (Exception exception) {
            throw new IllegalStateException("Hacker News collection failed", exception);
        }
    }

    private List<Long> ids(String feed) throws Exception {
        String body = http.get(URI.create(API + "/" + feed + ".json"), "application/json", Map.of());
        List<Long> ids = new ArrayList<>();
        for (Object value : json.readValue(body, List.class)) {
            if (value instanceof Number number) ids.add(number.longValue());
            if (ids.size() == 25) break;
        }
        return ids;
    }

    private List<BriefCandidate> collectItems(List<Long> ids, BriefCollectionRequest request)
            throws InterruptedException {
        if (ids.isEmpty()) return List.of();
        try (ExecutorService executor = Executors.newFixedThreadPool(8, Thread.ofVirtual().factory())) {
            List<Callable<BriefCandidate>> calls = ids.stream()
                    .<Callable<BriefCandidate>>map(id -> () -> item(id, request)).toList();
            return executor.invokeAll(calls).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    return null;
                }
            }).filter(java.util.Objects::nonNull).toList();
        }
    }

    private BriefCandidate item(long id, BriefCollectionRequest request) {
        try {
            String body = http.get(URI.create(API + "/item/" + id + ".json"), "application/json", Map.of());
            Map<?, ?> story = map(json.readValue(body, Map.class));
            if (!"story".equals(story.get("type")) || Boolean.TRUE.equals(story.get("deleted"))
                    || Boolean.TRUE.equals(story.get("dead"))) return null;
            String title = string(story.get("title"));
            String url = string(story.get("url"));
            if (url.isBlank()) url = "https://news.ycombinator.com/item?id=" + id;
            Instant publishedAt = story.get("time") instanceof Number number
                    ? Instant.ofEpochSecond(number.longValue()) : null;
            if (publishedAt != null && publishedAt.isBefore(request.since())) return null;
            if (!matchesTopics(title + " " + url, request.topics())) return null;
            int score = integer(story.get("score"));
            int comments = integer(story.get("descendants"));
            String summary = score + " points · " + comments + " comments on Hacker News.";
            return new BriefCandidate(BriefKind.COMMUNITY, title, url, summary,
                    "Hacker News", string(story.get("by")), publishedAt, score + comments);
        } catch (Exception exception) {
            return null;
        }
    }
}
