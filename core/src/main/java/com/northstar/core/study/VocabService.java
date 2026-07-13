package com.northstar.core.study;

import java.text.Normalizer;
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
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AI-native vocabulary content with independent FSRS recognition/production schedules. */
@Service
public class VocabService {

    public static final int NEW_CARDS_PER_DAY_GUIDANCE = 10;
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private final VocabCardRepository cards;
    private final VocabSchedulingCardRepository schedules;
    private final VocabReviewLogRepository reviews;
    private final VocabDeckService decks;

    VocabService(VocabCardRepository cards, VocabSchedulingCardRepository schedules,
            VocabReviewLogRepository reviews, VocabDeckService decks) {
        this.cards = cards;
        this.schedules = schedules;
        this.reviews = reviews;
        this.decks = decks;
    }

    @Transactional
    public List<VocabCardSummary> createAll(List<NewVocabCard> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must contain at least one card");
        }
        Instant now = Instant.now();
        List<VocabCard> existing = new ArrayList<>(cards.findByOrderByCreatedAtDesc());
        Map<String, VocabCard> byFront = new HashMap<>();
        existing.forEach(card -> byFront.putIfAbsent(key(card.getLanguage(), card.getFront()), card));
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
                    requireText(item.back(), "back", 1000), trimToNull(item.metadata(), 4000),
                    language, canonicalDeck(item.deck(), language, existing), item.disciplineId());
            boolean productionEnabled = item.productionEnabled() != null
                    ? item.productionEnabled()
                    : decks.productionDefault(language, card.getDeck());
            card.setProductionEnabled(productionEnabled);
            cards.save(card);
            schedules.save(new VocabSchedulingCard(UUID.randomUUID(), card.getId(),
                    VocabReviewDirection.RECOGNITION, now));
            if (productionEnabled) {
                schedules.save(new VocabSchedulingCard(UUID.randomUUID(), card.getId(),
                        VocabReviewDirection.PRODUCTION, now));
            }
            existing.add(card);
            byFront.put(identity, card);
            result.add(card);
        }
        // Fire auditing callbacks before building the required createdAt response field.
        schedules.flush();
        return summarize(result, now);
    }

    /** The N active notes with the weakest recognition retrievability. */
    @Transactional(readOnly = true)
    public List<VocabCardSummary> atRisk(int limit, Instant now) {
        Instant at = Objects.requireNonNullElseGet(now, Instant::now);
        return summarize(cards.findBySuspendedFalse(), at).stream()
                .sorted(Comparator.comparingDouble(VocabCardSummary::recallProbability))
                .limit(Math.clamp(limit, 1, 50))
                .toList();
    }

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

    /** Due-only queue with at most one enabled direction from each vocabulary item. */
    @Transactional(readOnly = true)
    public List<VocabReviewCardSummary> reviewQueue(VocabLanguage language, String deck,
            int limit, Instant now) {
        VocabLanguage requiredLanguage = Objects.requireNonNull(language, "language is required");
        String requestedDeck = normalizeDeckFilter(deck);
        Instant at = Objects.requireNonNullElseGet(now, Instant::now);
        List<VocabCard> scoped = cards.findBySuspendedFalse().stream()
                .filter(card -> card.getLanguage() == requiredLanguage)
                .filter(card -> deckMatches(card.getDeck(), requestedDeck))
                .toList();
        Map<UUID, List<VocabSchedulingCard>> byCard = schedulesByCard(scoped);
        Map<ReviewKey, Integer> counts = reviewCounts(scoped);
        Comparator<VocabSchedulingCard> ordering = scheduleOrdering(at);
        return scoped.stream()
                .map(card -> byCard.getOrDefault(card.getId(), List.of()).stream()
                        .filter(schedule -> enabled(card, schedule.getDirection()))
                        .filter(schedule -> schedule.isDue(at))
                        .min(ordering)
                        .map(schedule -> reviewSummary(card, schedule, at, counts))
                        .orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((VocabReviewCardSummary row) -> queuePriority(
                                row.schedulingState(), row.lastReviewedAt()))
                        .thenComparingDouble(VocabReviewCardSummary::recallProbability)
                        .thenComparing(VocabReviewCardSummary::dueAt))
                .limit(Math.clamp(limit, 1, 50))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VocabCardSummary> cards() {
        return summarize(cards.findByOrderByCreatedAtDesc(), Instant.now());
    }

    @Transactional(readOnly = true)
    public List<VocabCardSummary> cards(VocabLanguage language) {
        return cards().stream().filter(card -> language == null || card.language() == language)
                .toList();
    }

    /** Compatibility entry for chat/MCP, where there is no browser preview token. */
    @Transactional
    public VocabCardSummary recordReview(UUID cardId, double success,
            VocabReviewLog.Rating rating, VocabReviewLog.ReviewSource source) {
        return recordReview(cardId, VocabReviewDirection.RECOGNITION, success, rating, source);
    }

    /** Compatibility entry for chat/MCP; rating is authoritative, not the legacy success value. */
    @Transactional
    public VocabCardSummary recordReview(UUID cardId, VocabReviewDirection direction, double success,
            VocabReviewLog.Rating rating, VocabReviewLog.ReviewSource source) {
        if (success < 0 || success > 1) {
            throw new IllegalArgumentException("success must be between 0 and 1");
        }
        VocabSchedulingCard schedule = getSchedule(cardId, direction);
        Instant now = Instant.now();
        return recordReview(cardId, direction, rating, source, now, schedule.getVersion(),
                ZoneId.systemDefault());
    }

    /** Browser rating that must match the server preview's schedule version and timestamp. */
    @Transactional
    public VocabCardSummary recordReview(UUID cardId, VocabReviewDirection direction,
            VocabReviewLog.Rating rating, VocabReviewLog.ReviewSource source,
            Instant previewedAt, long expectedScheduleVersion, ZoneId zone) {
        VocabCard card = get(cardId);
        VocabReviewDirection requiredDirection = Objects.requireNonNull(direction,
                "direction is required");
        requireEnabled(card, requiredDirection);
        VocabSchedulingCard schedule = getSchedule(cardId, requiredDirection);
        if (schedule.getVersion() != expectedScheduleVersion) {
            throw new OptimisticLockingFailureException("Vocabulary schedule " + schedule.getId()
                    + " was modified concurrently");
        }
        Instant reviewedAt = Objects.requireNonNull(previewedAt, "previewedAt is required");
        VocabReviewLog.Rating requiredRating = Objects.requireNonNull(rating, "rating is required");
        boolean lapse = VocabScheduler.isLapse(schedule, requiredRating);
        VocabScheduler.Outcome outcome = VocabScheduler.schedule(schedule, requiredRating, reviewedAt);
        reviews.save(new VocabReviewLog(UUID.randomUUID(), schedule, reviewedAt, requiredRating,
                Objects.requireNonNull(source, "source is required"),
                VocabScheduler.elapsedDays(schedule, reviewedAt), lapse, outcome));
        schedule.apply(outcome, lapse);
        schedules.save(schedule);
        burySibling(card, requiredDirection, reviewedAt, Objects.requireNonNull(zone, "zone is required"));
        return summarize(List.of(card), reviewedAt).getFirst();
    }

    @Transactional
    public VocabCardSummary update(UUID id, String front, String back, String metadata,
            VocabLanguage language, String deck, UUID disciplineId, boolean suspended) {
        VocabCard card = get(id);
        return update(id, front, back, metadata, language, deck, disciplineId, suspended,
                card.isProductionEnabled());
    }

    @Transactional
    public VocabCardSummary update(UUID id, String front, String back, String metadata,
            VocabLanguage language, String deck, UUID disciplineId, boolean suspended,
            boolean productionEnabled) {
        VocabCard card = get(id);
        String requiredFront = requireText(front, "front", 255);
        VocabLanguage requiredLanguage = Objects.requireNonNullElseGet(language,
                () -> VocabLanguage.detect(requiredFront));
        boolean enableNewProduction = productionEnabled && !card.isProductionEnabled();
        card.edit(requiredFront, requireText(back, "back", 1000), trimToNull(metadata, 4000),
                requiredLanguage,
                canonicalDeck(deck, requiredLanguage, cards.findByOrderByCreatedAtDesc()),
                disciplineId, suspended, productionEnabled);
        if (enableNewProduction && schedules.findByVocabCardIdAndDirection(id,
                VocabReviewDirection.PRODUCTION).isEmpty()) {
            schedules.save(new VocabSchedulingCard(UUID.randomUUID(), id,
                    VocabReviewDirection.PRODUCTION, Instant.now()));
        }
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

    @Transactional(readOnly = true)
    public List<VocabCardSummary> search(String query) {
        String needle = Objects.requireNonNull(query, "query is required").strip();
        if (needle.isEmpty()) throw new IllegalArgumentException("query is required");
        return summarize(cards.findTop20ByFrontContainingIgnoreCaseOrBackContainingIgnoreCase(
                needle, needle), Instant.now());
    }

    @Transactional(readOnly = true)
    public long newCardsToday(ZoneId zone) {
        Instant dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        return cards.countByCreatedAtGreaterThanEqual(dayStart);
    }

    @Transactional(readOnly = true)
    public long reviewsSince(Instant since) {
        return reviews.countByReviewedAtGreaterThanEqual(
                Objects.requireNonNull(since, "since is required"));
    }

    private VocabCard get(UUID id) {
        return cards.findById(id).orElseThrow(() -> new VocabCardNotFoundException(id));
    }

    private VocabSchedulingCard getSchedule(UUID cardId, VocabReviewDirection direction) {
        return schedules.findByVocabCardIdAndDirection(cardId,
                        Objects.requireNonNull(direction, "direction is required"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No " + direction + " schedule exists for " + cardId));
    }

    private List<VocabCardSummary> summarize(List<VocabCard> list, Instant now) {
        Map<UUID, List<VocabSchedulingCard>> byCard = schedulesByCard(list);
        Map<ReviewKey, Integer> counts = reviewCounts(list);
        return list.stream().map(card -> {
            Map<VocabReviewDirection, VocabSchedulingCard> directions = byCard
                    .getOrDefault(card.getId(), List.of()).stream()
                    .collect(Collectors.toMap(VocabSchedulingCard::getDirection, Function.identity()));
            VocabSchedulingCard recognition = requireSchedule(card, directions,
                    VocabReviewDirection.RECOGNITION);
            VocabSchedulingCard production = directions.get(VocabReviewDirection.PRODUCTION);
            return new VocabCardSummary(card.getId(), card.getFront(), card.getBack(),
                    card.getMetadata(), card.getLanguage(), card.getDeck(), card.getDisciplineId(),
                    VocabScheduler.retrievability(recognition, now), recognition.getStabilityDays(),
                    recognition.getDueAt(), recognition.getBuriedUntil(),
                    recognition.getLastReviewedAt(), recognition.getState(),
                    recognition.getLapseCount(), recognition.isLeech(), counts.getOrDefault(
                            new ReviewKey(card.getId(), VocabReviewDirection.RECOGNITION), 0),
                    card.isSuspended(), card.getCreatedAt(), card.getVersion(),
                    card.isProductionEnabled(),
                    production == null ? null : VocabScheduler.retrievability(production, now),
                    production == null ? null : production.getStabilityDays(),
                    production == null ? null : production.getDueAt(),
                    production == null ? null : production.getBuriedUntil(),
                    production == null ? null : production.getState(),
                    production == null ? null : production.getLapseCount(),
                    production == null ? null : production.isLeech(),
                    production == null ? null : counts.getOrDefault(
                            new ReviewKey(card.getId(), VocabReviewDirection.PRODUCTION), 0));
        }).toList();
    }

    private VocabReviewCardSummary reviewSummary(VocabCard card, VocabSchedulingCard schedule,
            Instant previewedAt, Map<ReviewKey, Integer> counts) {
        return new VocabReviewCardSummary(card.getId(), schedule.getId(), schedule.getVersion(),
                schedule.getDirection(), card.getFront(), card.getBack(), card.getMetadata(),
                card.getLanguage(), card.getDeck(), card.getDisciplineId(),
                VocabScheduler.retrievability(schedule, previewedAt), schedule.getStabilityDays(),
                schedule.getDueAt(), schedule.getLastReviewedAt(), schedule.getState(),
                schedule.getLearningStep(), schedule.getLapseCount(), schedule.isLeech(),
                counts.getOrDefault(new ReviewKey(card.getId(), schedule.getDirection()), 0),
                card.isSuspended(), card.getCreatedAt(), card.getVersion(), previewedAt,
                VocabScheduler.previews(schedule, previewedAt));
    }

    private Map<UUID, List<VocabSchedulingCard>> schedulesByCard(List<VocabCard> list) {
        List<UUID> ids = list.stream().map(VocabCard::getId).toList();
        if (ids.isEmpty()) return Map.of();
        return schedules.findByVocabCardIdIn(ids).stream()
                .collect(Collectors.groupingBy(VocabSchedulingCard::getVocabCardId));
    }

    private Map<ReviewKey, Integer> reviewCounts(List<VocabCard> list) {
        Map<ReviewKey, Integer> counts = new HashMap<>();
        List<UUID> ids = list.stream().map(VocabCard::getId).toList();
        if (!ids.isEmpty()) {
            for (VocabReviewLogRepository.CardReviewCount count : reviews.countByCard(ids)) {
                counts.put(new ReviewKey(count.getCardId(), count.getDirection()),
                        Math.toIntExact(count.getReviews()));
            }
        }
        return counts;
    }

    private Comparator<VocabSchedulingCard> scheduleOrdering(Instant now) {
        return Comparator.comparingInt((VocabSchedulingCard schedule) -> queuePriority(
                        schedule.getState(), schedule.getLastReviewedAt()))
                .thenComparingDouble(schedule -> VocabScheduler.retrievability(schedule, now))
                .thenComparing(VocabSchedulingCard::getDueAt)
                .thenComparing(schedule -> schedule.getDirection().ordinal());
    }

    private static int queuePriority(VocabSchedulingState state, Instant lastReviewedAt) {
        if (state == VocabSchedulingState.RELEARNING) return 0;
        if (state == VocabSchedulingState.LEARNING && lastReviewedAt != null) return 0;
        if (state == VocabSchedulingState.REVIEW) return 1;
        return 2;
    }

    private void burySibling(VocabCard card, VocabReviewDirection reviewedDirection,
            Instant reviewedAt, ZoneId zone) {
        VocabReviewDirection siblingDirection = reviewedDirection == VocabReviewDirection.RECOGNITION
                ? VocabReviewDirection.PRODUCTION : VocabReviewDirection.RECOGNITION;
        if (!enabled(card, siblingDirection)) return;
        schedules.findByVocabCardIdAndDirection(card.getId(), siblingDirection)
                .ifPresent(sibling -> {
                    Instant nextDay = reviewedAt.atZone(zone).toLocalDate().plusDays(1)
                            .atStartOfDay(zone).toInstant();
                    sibling.buryUntil(nextDay);
                    schedules.save(sibling);
                });
    }

    private static VocabSchedulingCard requireSchedule(VocabCard card,
            Map<VocabReviewDirection, VocabSchedulingCard> directions,
            VocabReviewDirection direction) {
        VocabSchedulingCard schedule = directions.get(direction);
        if (schedule == null) {
            throw new IllegalStateException("Vocabulary card " + card.getId()
                    + " has no " + direction + " schedule");
        }
        return schedule;
    }

    private static boolean enabled(VocabCard card, VocabReviewDirection direction) {
        return direction == VocabReviewDirection.RECOGNITION || card.isProductionEnabled();
    }

    private static void requireEnabled(VocabCard card, VocabReviewDirection direction) {
        if (!enabled(card, direction)) {
            throw new IllegalArgumentException("Production review is not enabled for " + card.getId());
        }
    }

    private record ReviewKey(UUID cardId, VocabReviewDirection direction) {
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
        if (value == null || value.isBlank()) return null;
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
