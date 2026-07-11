package com.northstar.core.brief;

import com.northstar.core.automation.AutomationExecutionContext;
import com.northstar.core.automation.AutomationHandler;
import com.northstar.core.automation.AutomationHandlerResult;
import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteStatus;
import com.northstar.core.web.WebRecency;
import com.northstar.core.web.WebResearchService;
import com.northstar.core.web.WebSearchRequest;
import com.northstar.core.web.WebSearchResult;
import com.northstar.core.web.WebSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

@Component
public class MorningBriefHandler implements AutomationHandler<MorningBriefConfig> {

    public static final String TYPE = "morning-brief.v1";
    private static final int MAX_QUERIES = 6;
    private static final Set<String> TRACKING_PARAMETERS = Set.of(
            "fbclid", "gclid", "mc_cid", "mc_eid", "ref", "source");

    private final WebResearchService research;
    private final NoteService notes;

    MorningBriefHandler(WebResearchService research, NoteService notes) {
        this.research = research;
        this.notes = notes;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String displayName() {
        return "Morning Brief";
    }

    @Override
    public String description() {
        return "A concise sourced briefing for the topics you follow.";
    }

    @Override
    public int configVersion() {
        return 1;
    }

    @Override
    public Class<MorningBriefConfig> configType() {
        return MorningBriefConfig.class;
    }

    @Override
    public MorningBriefConfig defaultConfig() {
        return MorningBriefConfig.defaults();
    }

    @Override
    public void validate(MorningBriefConfig config) {
        if (config == null) throw new IllegalArgumentException("Morning Brief config is required");
        if (config.language().isBlank() || config.language().length() > 16) {
            throw new IllegalArgumentException("language must contain 1 to 16 characters");
        }
        if (config.lookbackHours() < 1 || config.lookbackHours() > 168) {
            throw new IllegalArgumentException("lookbackHours must be between 1 and 168");
        }
        if (config.maxItems() < 1 || config.maxItems() > 10) {
            throw new IllegalArgumentException("maxItems must be between 1 and 10");
        }
        if (config.topics().size() > 12) throw new IllegalArgumentException("At most 12 topics are allowed");
        if (config.queries().size() > MAX_QUERIES) {
            throw new IllegalArgumentException("At most " + MAX_QUERIES + " queries are allowed");
        }
        if (config.topics().isEmpty() && config.queries().isEmpty()) {
            throw new IllegalArgumentException("Add at least one topic or search query");
        }
        if (config.blockedDomains().size() > 20) {
            throw new IllegalArgumentException("At most 20 blocked domains are allowed");
        }
    }

    @Override
    public AutomationHandlerResult execute(AutomationExecutionContext context, MorningBriefConfig config) {
        validate(config);
        List<String> queries = effectiveQueries(config);
        List<QueryOutcome> outcomes = search(queries, config);
        long successful = outcomes.stream().filter(QueryOutcome::successful).count();
        if (successful == 0) {
            RuntimeException failure = outcomes.stream().map(QueryOutcome::failure)
                    .filter(java.util.Objects::nonNull).findFirst()
                    .orElseGet(() -> new IllegalStateException("Morning Brief returned no search results"));
            throw failure;
        }

        List<BriefSource> sources = deduplicateSources(outcomes, config.maxItems());
        String markdown = render(context, config, outcomes, sources);
        UUID outputId = null;
        if (config.saveAsNote()) outputId = upsertNote(context, markdown).id();

        return new AutomationHandlerResult(config.saveAsNote() ? "NOTE" : null, outputId, Map.of(
                "queries", queries.size(),
                "successfulQueries", successful,
                "failedQueries", queries.size() - successful,
                "sources", sources.size()));
    }

    private List<QueryOutcome> search(List<String> queries, MorningBriefConfig config) {
        int concurrency = Math.min(3, queries.size());
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency, Thread.ofVirtual().factory())) {
            List<Callable<QueryOutcome>> calls = queries.stream()
                    .<Callable<QueryOutcome>>map(query -> () -> searchOne(query, config)).toList();
            return executor.invokeAll(calls).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    return new QueryOutcome("Search", null, new IllegalStateException("Search task failed", exception));
                }
            }).toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Morning Brief search was interrupted", exception);
        }
    }

    private QueryOutcome searchOne(String query, MorningBriefConfig config) {
        try {
            WebSearchResult result = research.search(new WebSearchRequest(
                    query + "\nReturn the important changes and why they matter. Answer in " + config.language() + ".",
                    recency(config.lookbackHours()), config.maxItems(), List.of(), config.blockedDomains()));
            return new QueryOutcome(query, result, null);
        } catch (RuntimeException exception) {
            return new QueryOutcome(query, null, exception);
        }
    }

    private static List<String> effectiveQueries(MorningBriefConfig config) {
        if (!config.queries().isEmpty()) return config.queries();
        return config.topics().stream().limit(MAX_QUERIES)
                .map(topic -> "Important recent developments in " + topic).toList();
    }

    private static WebRecency recency(int hours) {
        if (hours <= 24) return WebRecency.DAY;
        if (hours <= 24 * 7) return WebRecency.WEEK;
        return WebRecency.MONTH;
    }

    private static List<BriefSource> deduplicateSources(List<QueryOutcome> outcomes, int maxItems) {
        Map<String, BriefSource> unique = new LinkedHashMap<>();
        for (QueryOutcome outcome : outcomes) {
            if (!outcome.successful()) continue;
            for (WebSource source : outcome.result().sources()) {
                String canonical = canonicalUrl(source.url());
                if (!canonical.isBlank()) {
                    unique.putIfAbsent(canonical, new BriefSource(source.title(), source.url(), canonical));
                }
            }
        }
        return unique.values().stream().limit(maxItems).toList();
    }

    private static String render(AutomationExecutionContext context, MorningBriefConfig config,
            List<QueryOutcome> outcomes, List<BriefSource> sources) {
        LocalDate day = context.scheduledFor().atZone(context.zone()).toLocalDate();
        StringBuilder markdown = new StringBuilder("# Morning Brief — ")
                .append(day.format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n\n")
                .append("_Prepared for ").append(context.automationName()).append(" · ")
                .append(context.zone()).append("_\n\n");

        for (QueryOutcome outcome : outcomes) {
            if (!outcome.successful()) continue;
            markdown.append("## ").append(outcome.query()).append("\n\n");
            String answer = outcome.result().answer();
            markdown.append(answer == null || answer.isBlank() ? "No meaningful update found." : answer.strip())
                    .append("\n\n");
        }

        if (sources.isEmpty()) {
            markdown.append("## Sources\n\nNo verifiable sources were returned for this run.\n");
        } else {
            markdown.append("## Sources\n\n");
            for (BriefSource source : sources) {
                markdown.append("- [").append(escaped(source.title())).append("](")
                        .append(source.originalUrl()).append(")\n");
            }
        }

        long failed = outcomes.stream().filter(outcome -> !outcome.successful()).count();
        if (failed > 0) {
            markdown.append("\n---\n\n_").append(failed)
                    .append(" configured search ").append(failed == 1 ? "query" : "queries")
                    .append(" could not be completed._\n");
        }
        return markdown.toString();
    }

    private NoteDetail upsertNote(AutomationExecutionContext context, String markdown) {
        String title = noteTitle(context);
        return notes.findByTitle(title)
                .map(existing -> notes.update(existing.id(), title, "Briefs", markdown,
                        List.of("morning-brief", "research"), existing.version()))
                .orElseGet(() -> notes.create(title, "Briefs", markdown,
                        List.of("morning-brief", "research"), NoteStatus.STAGING));
    }

    private static String noteTitle(AutomationExecutionContext context) {
        LocalDate day = context.scheduledFor().atZone(context.zone()).toLocalDate();
        return "Morning Brief - " + context.automationName() + " - " + day;
    }

    private static String canonicalUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null) return "";
            String query = uri.getRawQuery();
            if (query != null) {
                query = java.util.Arrays.stream(query.split("&"))
                        .filter(part -> {
                            String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                            return !key.startsWith("utm_") && !TRACKING_PARAMETERS.contains(key);
                        }).collect(java.util.stream.Collectors.joining("&"));
                if (query.isBlank()) query = null;
            }
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return new URI(uri.getScheme().toLowerCase(Locale.ROOT), null,
                    uri.getHost().toLowerCase(Locale.ROOT), uri.getPort(), path, query, null).toString();
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return "";
        }
    }

    private static String escaped(String value) {
        return value.replace("[", "\\[").replace("]", "\\]");
    }

    private record QueryOutcome(String query, WebSearchResult result, RuntimeException failure) {
        boolean successful() {
            return result != null;
        }
    }

    private record BriefSource(String title, String originalUrl, String canonicalUrl) {
    }
}
