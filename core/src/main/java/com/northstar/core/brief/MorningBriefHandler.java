package com.northstar.core.brief;

import com.northstar.core.automation.AutomationExecutionContext;
import com.northstar.core.automation.AutomationHandler;
import com.northstar.core.automation.AutomationHandlerResult;
import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteStatus;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class MorningBriefHandler implements AutomationHandler<MorningBriefConfig> {

    public static final String TYPE = "morning-brief.v1";
    private static final int MAX_QUERIES = 6;
    private static final int MAX_PROVIDER_CONCURRENCY = 4;
    private static final Set<String> KNOWN_SOURCE_IDS = Set.of(
            "github", "rss", "hacker-news", "bluesky", "firecrawl");
    private static final Set<String> TRACKING_PARAMETERS = Set.of(
            "fbclid", "gclid", "mc_cid", "mc_eid", "ref", "source");
    private static final Comparator<BriefCandidate> RANKING = Comparator
            .comparingInt((BriefCandidate item) -> kindRank(item.kind()))
            .thenComparing(BriefCandidate::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(BriefCandidate::score, Comparator.reverseOrder())
            .thenComparing(BriefCandidate::title, String.CASE_INSENSITIVE_ORDER);

    private final Map<String, BriefSourceProvider> providers;
    private final NoteService notes;

    MorningBriefHandler(List<BriefSourceProvider> providers, NoteService notes) {
        Map<String, BriefSourceProvider> indexed = new LinkedHashMap<>();
        for (BriefSourceProvider provider : providers) {
            String id = normalized(provider.id());
            if (id.isBlank()) throw new IllegalStateException("A Morning Brief source has a blank id");
            if (indexed.putIfAbsent(id, provider) != null) {
                throw new IllegalStateException("Duplicate Morning Brief source: " + id);
            }
        }
        this.providers = Map.copyOf(indexed);
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
        return "A concise technology briefing from public releases, people, and communities.";
    }

    @Override
    public int configVersion() {
        return 2;
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
        if (config.maxItems() < 1 || config.maxItems() > 20) {
            throw new IllegalArgumentException("maxItems must be between 1 and 20");
        }
        if (config.topics().size() > 16) throw new IllegalArgumentException("At most 16 topics are allowed");
        if (config.queries().size() > MAX_QUERIES) {
            throw new IllegalArgumentException("At most " + MAX_QUERIES + " queries are allowed");
        }
        if (config.sourceIds().isEmpty()) throw new IllegalArgumentException("Enable at least one source");
        List<String> unknown = config.sourceIds().stream().filter(Predicate.not(KNOWN_SOURCE_IDS::contains)).toList();
        if (!unknown.isEmpty()) throw new IllegalArgumentException("Unsupported Morning Brief sources: " + unknown);
        if (config.githubRepositories().size() > 20) {
            throw new IllegalArgumentException("At most 20 GitHub repositories are allowed");
        }
        if (config.feedUrls().size() > 20) throw new IllegalArgumentException("At most 20 feeds are allowed");
        if (config.blueskyHandles().size() > 20) {
            throw new IllegalArgumentException("At most 20 Bluesky handles are allowed");
        }
        if (config.blockedDomains().size() > 20) {
            throw new IllegalArgumentException("At most 20 blocked domains are allowed");
        }
        if (config.firecrawlCreditBudget() < 5 || config.firecrawlCreditBudget() > 50) {
            throw new IllegalArgumentException("firecrawlCreditBudget must be between 5 and 50");
        }
    }

    @Override
    public AutomationHandlerResult execute(AutomationExecutionContext context, MorningBriefConfig config) {
        validate(config);
        BriefCollectionRequest request = request(context, config);
        List<SourceOutcome> outcomes = collect(config.sourceIds(), request);
        long successful = outcomes.stream().filter(SourceOutcome::successful).count();
        if (successful == 0) {
            RuntimeException failure = outcomes.stream().map(SourceOutcome::failure)
                    .filter(java.util.Objects::nonNull).findFirst()
                    .orElseGet(() -> new IllegalStateException("Morning Brief has no available sources"));
            throw failure;
        }

        List<BriefCandidate> items = deduplicateAndRank(outcomes, config.maxItems(), config.blockedDomains());
        String markdown = render(context, config, outcomes, items);
        UUID outputId = null;
        if (config.saveAsNote()) outputId = upsertNote(context, markdown).id();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("providers", outcomes.size());
        metrics.put("successfulProviders", successful);
        metrics.put("failedProviders", outcomes.size() - successful);
        metrics.put("sources", items.size());
        metrics.put("firecrawlCredits", metricTotal(outcomes, "creditsUsed"));
        return new AutomationHandlerResult(config.saveAsNote() ? "NOTE" : null, outputId, metrics);
    }

    private List<SourceOutcome> collect(List<String> sourceIds, BriefCollectionRequest request) {
        int concurrency = Math.min(MAX_PROVIDER_CONCURRENCY, sourceIds.size());
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency, Thread.ofVirtual().factory())) {
            List<Callable<SourceOutcome>> calls = sourceIds.stream()
                    .<Callable<SourceOutcome>>map(id -> () -> collectOne(id, request)).toList();
            return executor.invokeAll(calls).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    return new SourceOutcome("unknown", "Unknown", null,
                            new IllegalStateException("Brief source task failed", exception));
                }
            }).toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Morning Brief collection was interrupted", exception);
        }
    }

    private SourceOutcome collectOne(String sourceId, BriefCollectionRequest request) {
        BriefSourceProvider provider = providers.get(sourceId);
        if (provider == null) {
            return new SourceOutcome(sourceId, sourceId, null,
                    new IllegalStateException("Morning Brief source is not installed: " + sourceId));
        }
        if (!provider.configured()) {
            return new SourceOutcome(sourceId, provider.displayName(), null,
                    new IllegalStateException(provider.displayName() + " is not configured"));
        }
        try {
            return new SourceOutcome(sourceId, provider.displayName(), provider.collect(request), null);
        } catch (RuntimeException exception) {
            return new SourceOutcome(sourceId, provider.displayName(), null, exception);
        }
    }

    private static BriefCollectionRequest request(AutomationExecutionContext context, MorningBriefConfig config) {
        Instant since = context.scheduledFor().minusSeconds(config.lookbackHours() * 3_600L);
        return new BriefCollectionRequest(since, config.maxItems(), config.topics(), config.queries(),
                config.blockedDomains(), config.githubRepositories(), config.feedUrls(),
                config.blueskyHandles(), config.firecrawlCreditBudget());
    }

    private static List<BriefCandidate> deduplicateAndRank(List<SourceOutcome> outcomes, int maxItems,
            List<String> blockedDomains) {
        Map<String, BriefCandidate> unique = new LinkedHashMap<>();
        for (SourceOutcome outcome : outcomes) {
            if (!outcome.successful()) continue;
            for (BriefCandidate item : outcome.result().items()) {
                String canonical = canonicalUrl(item.url());
                if (item.title().isBlank() || canonical.isBlank() || blocked(canonical, blockedDomains)) continue;
                BriefCandidate existing = unique.get(canonical);
                if (existing == null || preferred(item, existing)) unique.put(canonical, item);
            }
        }
        List<BriefCandidate> ranked = unique.values().stream().sorted(RANKING).toList();
        return selectSourceFair(ranked, maxItems);
    }

    private static List<BriefCandidate> selectSourceFair(List<BriefCandidate> ranked, int maxItems) {
        List<BriefCandidate> selected = new ArrayList<>();
        for (BriefKind kind : BriefKind.values()) {
            Map<String, List<BriefCandidate>> bySource = new LinkedHashMap<>();
            ranked.stream().filter(item -> item.kind() == kind).forEach(item -> bySource
                    .computeIfAbsent(normalized(item.source()), ignored -> new ArrayList<>()).add(item));

            boolean madeProgress = true;
            while (selected.size() < maxItems && madeProgress) {
                madeProgress = false;
                for (List<BriefCandidate> sourceItems : bySource.values()) {
                    if (sourceItems.isEmpty()) continue;
                    selected.add(sourceItems.removeFirst());
                    madeProgress = true;
                    if (selected.size() == maxItems) break;
                }
            }
            if (selected.size() == maxItems) break;
        }
        return List.copyOf(selected);
    }

    private static boolean preferred(BriefCandidate candidate, BriefCandidate existing) {
        int comparison = RANKING.compare(candidate, existing);
        return comparison < 0 || (comparison == 0 && candidate.summary().length() > existing.summary().length());
    }

    private static String render(AutomationExecutionContext context, MorningBriefConfig config,
            List<SourceOutcome> outcomes,
            List<BriefCandidate> items) {
        boolean vietnamese = "vi".equalsIgnoreCase(config.language());
        LocalDate day = localDay(context);
        StringBuilder markdown = new StringBuilder(vietnamese ? "# Bản tin sáng — " : "# Morning Brief — ")
                .append(day.format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n\n")
                .append(vietnamese ? "_Điểm tin công nghệ hằng ngày · " : "_Daily technology brief · ")
                .append(context.zone()).append("_\n\n");

        appendSection(markdown, vietnamese ? "Phát hành chính thức" : "Official releases",
                items, BriefKind.OFFICIAL, context.zone(), vietnamese);
        appendSection(markdown, vietnamese ? "Tác giả và nhà xuất bản" : "People & publishers",
                items, BriefKind.PEOPLE, context.zone(), vietnamese);
        appendSection(markdown, vietnamese ? "Tín hiệu cộng đồng" : "Community radar",
                items, BriefKind.COMMUNITY, context.zone(), vietnamese);
        if (items.isEmpty()) {
            markdown.append(vietnamese
                    ? "Không tìm thấy cập nhật phù hợp trong khoảng thời gian này.\n\n"
                    : "No relevant updates were found in this window.\n\n");
        }

        markdown.append(vietnamese ? "## Trạng thái nguồn\n\n" : "## Source status\n\n");
        for (SourceOutcome outcome : outcomes) {
            if (outcome.successful()) {
                markdown.append("- **").append(escaped(outcome.displayName())).append("** — ")
                        .append(outcome.result().items().size())
                        .append(vietnamese ? " mục tìm được" : " candidates");
                Object credits = outcome.result().metrics().get("creditsUsed");
                if (credits != null) markdown.append(" · ").append(credits)
                        .append(vietnamese ? " credit" : " credits");
                markdown.append('\n');
            } else {
                markdown.append("- **").append(escaped(outcome.displayName()))
                        .append(vietnamese ? "** — không khả dụng trong lần chạy này\n"
                                : "** — unavailable for this run\n");
            }
        }
        markdown.append(vietnamese
                ? "\n---\n\n_Nội dung cộng đồng chỉ là tín hiệu. Hãy mở nguồn gốc trước khi sử dụng thông tin._\n"
                : "\n---\n\n_Community items are signals. Open the original source before acting on a claim._\n");
        return markdown.toString();
    }

    private static void appendSection(StringBuilder markdown, String heading, List<BriefCandidate> items,
            BriefKind kind, ZoneId zone, boolean vietnamese) {
        List<BriefCandidate> section = items.stream().filter(item -> item.kind() == kind).toList();
        if (section.isEmpty()) return;
        markdown.append("## ").append(heading).append("\n\n");
        for (BriefCandidate item : section) {
            markdown.append("### [").append(escaped(item.title())).append("](")
                    .append(canonicalUrl(item.url())).append(")\n\n");
            List<String> metadata = new ArrayList<>();
            if (!item.source().isBlank()) metadata.add(item.source());
            if (!item.author().isBlank() && !item.author().equalsIgnoreCase(item.source())) metadata.add(item.author());
            if (item.publishedAt() != null) {
                metadata.add(DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.ENGLISH)
                        .withZone(zone).format(item.publishedAt()));
            }
            if (item.score() > 0) metadata.add(item.score() + (vietnamese ? " tín hiệu" : " signals"));
            if (!metadata.isEmpty()) markdown.append('_').append(String.join(" · ", metadata)).append("_\n\n");
            if (!item.summary().isBlank()) markdown.append(compact(item.summary())).append("\n\n");
        }
    }

    private NoteDetail upsertNote(AutomationExecutionContext context, String markdown) {
        String title = noteTitle(context);
        return notes.findByTitle(title)
                .map(existing -> notes.update(existing.id(), title, "Briefs", markdown,
                        List.of("morning-brief", "technology", "research"), existing.version()))
                .orElseGet(() -> notes.create(title, "Briefs", markdown,
                        List.of("morning-brief", "technology", "research"), NoteStatus.STAGING));
    }

    private static String noteTitle(AutomationExecutionContext context) {
        return "Morning Brief - " + context.automationName() + " - " + localDay(context);
    }

    private static LocalDate localDay(AutomationExecutionContext context) {
        return context.scheduledFor().atZone(context.zone()).toLocalDate();
    }

    private static long metricTotal(List<SourceOutcome> outcomes, String key) {
        return outcomes.stream().filter(SourceOutcome::successful)
                .map(outcome -> outcome.result().metrics().get(key))
                .filter(Number.class::isInstance).map(Number.class::cast).mapToLong(Number::longValue).sum();
    }

    private static int kindRank(BriefKind kind) {
        return switch (kind) {
            case OFFICIAL -> 0;
            case PEOPLE -> 1;
            case COMMUNITY -> 2;
        };
    }

    private static boolean blocked(String url, List<String> domains) {
        String host = URI.create(url).getHost();
        if (host == null) return true;
        String normalized = host.toLowerCase(Locale.ROOT);
        return domains.stream().anyMatch(domain -> normalized.equals(domain) || normalized.endsWith("." + domain));
    }

    private static String canonicalUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || !("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) return "";
            String query = uri.getRawQuery();
            if (query != null) {
                query = java.util.Arrays.stream(query.split("&"))
                        .filter(part -> {
                            String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                            return !key.startsWith("utm_") && !TRACKING_PARAMETERS.contains(key);
                        }).collect(java.util.stream.Collectors.joining("&"));
                if (query.isBlank()) query = null;
            }
            // Rebuild from raw components: the multi-arg URI constructor treats its
            // String args as decoded and re-percent-encodes them, which would turn
            // an already-encoded query (%20) into %2520 and collapse encoded path
            // slashes. Assembling the string keeps the encoded octets verbatim.
            String rawPath = uri.getRawPath();
            String path = rawPath == null || rawPath.isBlank() ? "/" : rawPath;
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            StringBuilder canonical = new StringBuilder()
                    .append(uri.getScheme().toLowerCase(Locale.ROOT)).append("://")
                    .append(uri.getHost().toLowerCase(Locale.ROOT));
            if (uri.getPort() >= 0) canonical.append(':').append(uri.getPort());
            canonical.append(path);
            if (query != null) canonical.append('?').append(query);
            return canonical.toString();
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static String compact(String value) {
        String clean = value.replaceAll("\\s+", " ").strip();
        return clean.length() <= 600 ? clean : clean.substring(0, 597).stripTrailing() + "...";
    }

    private static String escaped(String value) {
        return value.replace("[", "\\[").replace("]", "\\]");
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private record SourceOutcome(String id, String displayName, BriefSourceResult result, RuntimeException failure) {
        boolean successful() {
            return result != null;
        }
    }
}
