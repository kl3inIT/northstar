package com.northstar.core.study;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vocabulary memory — Anki's mechanics (per-card memory model, graded
 * reviews, an append-only revlog) rebuilt AI-native: cards arrive from
 * natural-language capture, reviews happen inside chat where the assistant
 * asks and grades free-text answers, and scheduling is Ebisu recall
 * probability instead of due dates, so {@link #atRisk} answers "what should
 * we quiz right now" at any moment and a lapse never builds a backlog.
 */
@Service
public class VocabService {

    /** New cards start balanced (α=β=2) with a one-day half-life. */
    static final double INITIAL_ALPHA_BETA = 2.0;
    static final double INITIAL_HALFLIFE_HOURS = 24.0;
    /** Introducing more than this per day dilutes retention — guidance, not a hard wall. */
    public static final int NEW_CARDS_PER_DAY_GUIDANCE = 10;

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final double MIN_ELAPSED_HOURS = 0.01;

    private final VocabCardRepository cards;
    private final VocabReviewLogRepository reviews;

    VocabService(VocabCardRepository cards, VocabReviewLogRepository reviews) {
        this.cards = cards;
        this.reviews = reviews;
    }

    /**
     * Create a batch of cards. A front that already exists (accent- and
     * case-insensitive) is NOT duplicated — the existing card is returned in
     * place, so re-capturing a word is harmless.
     */
    @Transactional
    public List<VocabCardSummary> createAll(List<NewVocabCard> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must contain at least one card");
        }
        Instant now = Instant.now();
        List<VocabCard> existing = new ArrayList<>(cards.findByOrderByCreatedAtDesc());
        Map<String, VocabCard> byFront = new HashMap<>();
        for (VocabCard card : existing) {
            byFront.putIfAbsent(key(card.getLanguage(), card.getFront()), card);
        }
        List<VocabCard> result = new ArrayList<>();
        for (NewVocabCard item : items) {
            String front = requireText(item.front(), "front", 255);
            VocabLanguage language = Objects.requireNonNullElseGet(item.language(),
                    () -> VocabLanguage.detect(front));
            String identity = key(language, front);
            VocabCard already = byFront.get(identity);
            if (already != null) {
                result.add(already);
                continue;
            }
            VocabCard card = new VocabCard(UUID.randomUUID(), front,
                    requireText(item.back(), "back", 1000),
                    trimToNull(item.metadata(), 4000), language,
                    canonicalDeck(item.deck(), language, existing), item.disciplineId(),
                    INITIAL_ALPHA_BETA, INITIAL_ALPHA_BETA, INITIAL_HALFLIFE_HOURS, now);
            cards.save(card);
            existing.add(card);
            byFront.put(identity, card);
            result.add(card);
        }
        return summarize(result, now);
    }

    /** The N cards most likely forgotten right now — the quiz and brief read this. */
    @Transactional(readOnly = true)
    public List<VocabCardSummary> atRisk(int limit, Instant now) {
        Instant at = Objects.requireNonNullElseGet(now, Instant::now);
        List<VocabCard> active = cards.findBySuspendedFalse();
        return summarize(active, at).stream()
                .sorted(Comparator.comparingDouble(VocabCardSummary::recallProbability))
                .limit(Math.clamp(limit, 1, 50))
                .toList();
    }

    /** The independent review queue for one language and optional deck scope. */
    @Transactional(readOnly = true)
    public List<VocabCardSummary> atRisk(VocabLanguage language, String deck,
            int limit, Instant now) {
        VocabLanguage requiredLanguage = Objects.requireNonNull(language, "language is required");
        String requestedDeck = normalizeDeckFilter(deck);
        Instant at = Objects.requireNonNullElseGet(now, Instant::now);
        return summarize(cards.findBySuspendedFalse(), at).stream()
                .filter(card -> card.language() == requiredLanguage)
                .filter(card -> deckMatches(card.deck(), requestedDeck))
                .sorted(Comparator.comparingDouble(VocabCardSummary::recallProbability))
                .limit(Math.clamp(limit, 1, 50))
                .toList();
    }

    /** Every card, most recently added first, with recall computed for now. */
    @Transactional(readOnly = true)
    public List<VocabCardSummary> cards() {
        return summarize(cards.findByOrderByCreatedAtDesc(), Instant.now());
    }

    /** One language library; null retains the all-card internal/tool view. */
    @Transactional(readOnly = true)
    public List<VocabCardSummary> cards(VocabLanguage language) {
        return cards().stream().filter(card -> language == null || card.language() == language)
                .toList();
    }

    /**
     * Fold one graded review into the card's memory. {@code success} is the
     * grade in [0,1] (the model updates on >= 0.5); {@code rating} is the
     * Anki-style label kept for the log and future algorithm migration.
     */
    @Transactional
    public VocabCardSummary recordReview(UUID cardId, double success,
            VocabReviewLog.Rating rating, VocabReviewLog.ReviewSource source) {
        if (success < 0 || success > 1) {
            throw new IllegalArgumentException("success must be between 0 and 1");
        }
        VocabCard card = get(cardId);
        Instant now = Instant.now();
        double elapsedHours = Math.max(MIN_ELAPSED_HOURS,
                Duration.between(card.getLastReviewedAt(), now).toMillis() / 3_600_000.0);
        Ebisu.Model before = model(card);
        Ebisu.Model after = Ebisu.updateRecall(before, success >= 0.5 ? 1 : 0, 1, elapsedHours);
        double halflifeAfter = Ebisu.modelToPercentileDecay(after);
        reviews.save(new VocabReviewLog(UUID.randomUUID(), card.getId(), now, success, rating,
                elapsedHours, Objects.requireNonNull(source, "source is required"),
                before.alpha(), before.beta(), card.getHalflifeHours(),
                after.alpha(), after.beta(), halflifeAfter));
        card.reviewed(after.alpha(), after.beta(), halflifeAfter, now);
        return summarize(List.of(card), now).getFirst();
    }

    /** Edit the content sides; the memory model only moves through reviews. */
    @Transactional
    public VocabCardSummary update(UUID id, String front, String back, String metadata,
            VocabLanguage language, String deck, UUID disciplineId, boolean suspended) {
        VocabCard card = get(id);
        String requiredFront = requireText(front, "front", 255);
        VocabLanguage requiredLanguage = Objects.requireNonNullElseGet(language,
                () -> VocabLanguage.detect(requiredFront));
        card.edit(requireText(front, "front", 255), requireText(back, "back", 1000),
                trimToNull(metadata, 4000), requiredLanguage,
                canonicalDeck(deck, requiredLanguage, cards.findByOrderByCreatedAtDesc()),
                disciplineId, suspended);
        return summarize(List.of(card), Instant.now()).getFirst();
    }

    @Transactional
    public void delete(UUID id) {
        cards.delete(get(id));
    }

    @Transactional(readOnly = true)
    public VocabCardSummary find(UUID id) {
        return summarize(List.of(get(id)), Instant.now()).getFirst();
    }

    /** Cards whose front or back contains the query — resolving "cái từ 磨蹭 ấy". */
    @Transactional(readOnly = true)
    public List<VocabCardSummary> search(String query) {
        String needle = Objects.requireNonNull(query, "query is required").strip();
        if (needle.isEmpty()) {
            throw new IllegalArgumentException("query is required");
        }
        return summarize(cards
                .findTop20ByFrontContainingIgnoreCaseOrBackContainingIgnoreCase(needle, needle),
                Instant.now());
    }

    /** Cards introduced since the local day started — the new-cards/day guidance. */
    @Transactional(readOnly = true)
    public long newCardsToday(ZoneId zone) {
        Instant dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        return cards.countByCreatedAtGreaterThanEqual(dayStart);
    }

    /** Reviews recorded in the trailing window — the page's activity stat. */
    @Transactional(readOnly = true)
    public long reviewsSince(Instant since) {
        return reviews.countByReviewedAtGreaterThanEqual(
                Objects.requireNonNull(since, "since is required"));
    }

    private VocabCard get(UUID id) {
        return cards.findById(id).orElseThrow(() -> new VocabCardNotFoundException(id));
    }

    private List<VocabCardSummary> summarize(List<VocabCard> list, Instant now) {
        Map<UUID, Long> counts = new HashMap<>();
        List<UUID> ids = list.stream().map(VocabCard::getId).toList();
        if (!ids.isEmpty()) {
            for (VocabReviewLogRepository.CardReviewCount count : reviews.countByCard(ids)) {
                counts.put(count.getCardId(), count.getReviews());
            }
        }
        return list.stream().map(card -> {
            double elapsedHours = Math.max(MIN_ELAPSED_HOURS,
                    Duration.between(card.getLastReviewedAt(), now).toMillis() / 3_600_000.0);
            double recall = Ebisu.predictRecall(model(card), elapsedHours);
            return new VocabCardSummary(card.getId(), card.getFront(), card.getBack(),
                    card.getMetadata(), card.getLanguage(), card.getDeck(),
                    card.getDisciplineId(), recall, card.getHalflifeHours(),
                    card.getLastReviewedAt(), counts.getOrDefault(card.getId(), 0L).intValue(),
                    card.isSuspended(), card.getCreatedAt(), card.getVersion());
        }).toList();
    }

    private static Ebisu.Model model(VocabCard card) {
        return new Ebisu.Model(card.getHalflifeHours(), card.getAlpha(), card.getBeta());
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String stripped = value.strip();
        if (stripped.codePointCount(0, stripped.length()) > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength
                    + " characters");
        }
        return stripped;
    }

    private static String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.codePointCount(0, stripped.length()) > maxLength) {
            throw new IllegalArgumentException("metadata must be at most " + maxLength
                    + " characters");
        }
        return stripped;
    }

    static String canonicalDeck(String value, VocabLanguage language, List<VocabCard> existing) {
        String normalized = normalizeStoredDeck(value);
        if (normalized == null) return null;
        return existing.stream()
                .filter(card -> card.getLanguage() == language && card.getDeck() != null)
                .map(VocabCard::getDeck)
                .filter(deck -> deck.equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(normalized);
    }

    static boolean deckMatches(String stored, String requested) {
        if (requested == null) return true;
        if (requested.equalsIgnoreCase("General")) return stored == null;
        return stored != null && stored.equalsIgnoreCase(requested);
    }

    private static String normalizeStoredDeck(String value) {
        if (value == null || value.isBlank()) return null;
        String deck = requireText(value, "deck", 80);
        return deck.equalsIgnoreCase("General") ? null : deck;
    }

    private static String normalizeDeckFilter(String value) {
        if (value == null || value.isBlank()) return null;
        return requireText(value, "deck", 80);
    }

    private static String key(VocabLanguage language, String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return language.name() + ':'
                + COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
