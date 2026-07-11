package com.northstar.worker.brief;

import static com.northstar.worker.brief.BriefSourceSupport.instant;
import static com.northstar.worker.brief.BriefSourceSupport.list;
import static com.northstar.worker.brief.BriefSourceSupport.map;
import static com.northstar.worker.brief.BriefSourceSupport.plainText;
import static com.northstar.worker.brief.BriefSourceSupport.recent;
import static com.northstar.worker.brief.BriefSourceSupport.string;

import com.northstar.core.brief.BriefCandidate;
import com.northstar.core.brief.BriefCollectionRequest;
import com.northstar.core.brief.BriefKind;
import com.northstar.core.brief.BriefSourceProvider;
import com.northstar.core.brief.BriefSourceResult;
import java.net.URI;
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
class GitHubBriefSource implements BriefSourceProvider {

    private final BriefHttpClient http;
    private final ObjectMapper json;
    private final GitHubBriefProperties properties;

    GitHubBriefSource(BriefHttpClient http, ObjectMapper json, GitHubBriefProperties properties) {
        this.http = http;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public String id() {
        return "github";
    }

    @Override
    public String displayName() {
        return "GitHub releases";
    }

    @Override
    public BriefSourceResult collect(BriefCollectionRequest request) {
        if (request.githubRepositories().isEmpty()) return BriefSourceResult.of(List.of());
        int concurrency = Math.min(3, request.githubRepositories().size());
        List<RepositoryOutcome> outcomes;
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency, Thread.ofVirtual().factory())) {
            List<Callable<RepositoryOutcome>> calls = request.githubRepositories().stream()
                    .<Callable<RepositoryOutcome>>map(repo -> () -> collectRepository(repo, request.since())).toList();
            outcomes = executor.invokeAll(calls).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    return new RepositoryOutcome(List.of(), exception);
                }
            }).toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub collection was interrupted", exception);
        }
        long failed = outcomes.stream().filter(outcome -> outcome.failure() != null).count();
        if (failed == outcomes.size()) throw new IllegalStateException("Every configured GitHub repository failed");
        List<BriefCandidate> items = outcomes.stream().flatMap(outcome -> outcome.items().stream())
                .limit(request.maxItems() * 2L).toList();
        return new BriefSourceResult(items, Map.of(
                "repositories", request.githubRepositories().size(),
                "failedRepositories", failed));
    }

    private RepositoryOutcome collectRepository(String repository, Instant since) {
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            return new RepositoryOutcome(List.of(), new IllegalArgumentException("Invalid GitHub repository: " + repository));
        }
        try {
            Map<String, String> headers = properties.token().isBlank()
                    ? Map.of("X-GitHub-Api-Version", "2026-03-10")
                    : Map.of("Authorization", "Bearer " + properties.token(),
                            "X-GitHub-Api-Version", "2026-03-10");
            String body = http.get(URI.create("https://api.github.com/repos/" + repository + "/releases?per_page=5"),
                    "application/vnd.github+json", headers);
            List<BriefCandidate> items = new ArrayList<>();
            for (Object value : json.readValue(body, List.class)) {
                Map<?, ?> release = map(value);
                if (Boolean.TRUE.equals(release.get("draft"))) continue;
                Instant publishedAt = instant(release.get("published_at"));
                if (!recent(publishedAt, since)) continue;
                String title = string(release.get("name"));
                if (title.isBlank()) title = string(release.get("tag_name"));
                String url = string(release.get("html_url"));
                Map<?, ?> author = map(release.get("author"));
                String summary = plainText(string(release.get("body")), 480);
                if (summary.isBlank()) summary = "Published release " + string(release.get("tag_name")) + ".";
                items.add(new BriefCandidate(BriefKind.OFFICIAL, title, url, summary,
                        "GitHub · " + repository, string(author.get("login")), publishedAt, 0));
            }
            return new RepositoryOutcome(items, null);
        } catch (Exception exception) {
            return new RepositoryOutcome(List.of(), exception);
        }
    }

    private record RepositoryOutcome(List<BriefCandidate> items, Exception failure) {
    }
}
