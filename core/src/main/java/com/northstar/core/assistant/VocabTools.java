package com.northstar.core.assistant;

import static com.northstar.core.assistant.ToolSupport.disciplineIdByName;

import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.study.NewVocabCard;
import com.northstar.core.study.VocabCardSummary;
import com.northstar.core.study.VocabReviewLog;
import com.northstar.core.study.VocabService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Vocabulary-trainer tools — Anki's card mechanics delivered through chat:
 * the assistant runs the quiz session (ask, grade the free-text answer,
 * record) instead of a flashcard UI.
 */
@Component
class VocabTools implements NorthstarTool {

    private static final String SAVE_CARDS = """
            Save words/phrases the user wants to memorize as spaced-repetition \
            cards ("từ mới: 磨蹭 = lề mề"). Asking for the meaning, translation, \
            or explanation of one specific foreign-language word/phrase also \
            counts as intent to learn and save it unless the user explicitly \
            says not to save. Do not infer a card from an unresolved pronoun or \
            save incidental words from prose/research. Accepts a LIST — one card per \
            word = meaning pair. ENRICH each card yourself: reading = \
            pronunciation you know to be correct (tone-marked pinyin for \
            Chinese, IPA for English; "" when unsure), example = one short \
            natural sentence using the word with a translation after " — ". \
            A front that already exists is returned as-is, never duplicated. \
            Pace introductions: more than ~10 new cards in one day dilutes \
            retention, and semantically similar words (near-synonyms) learned \
            together interfere — suggest spreading them across days instead of \
            refusing. After the call, echo each card (front · back · reading) \
            back in one line each.""";

    private static final String QUIZ = """
            The N cards most likely forgotten RIGHT NOW (lowest predicted \
            recall first) — there are no due dates, so this is always callable \
            and never backlogged. Each entry carries front, back, reading, \
            example, and recallProbability. QUIZ PROTOCOL when the user wants \
            to review ("ôn từ đi"): ask ONE front at a time and wait for the \
            answer; NEVER show the back before the user answers; grade \
            meaning-equivalence generously (paraphrases and synonyms count, \
            the exact wording does not); after each answer give the verdict \
            with the correct back + example sentence, call record_vocab_review \
            with the fitting rating, then ask the next card; end with a short \
            tally. Also useful read-only for the morning brief's "words \
            slipping away" list.""";

    private static final String RECORD_REVIEW = """
            Record ONE graded quiz answer and update the card's memory model. \
            Call once per card, right after grading the user's answer in a \
            quiz. Ratings (Anki convention): AGAIN = failed to recall, HARD = \
            recalled with real difficulty or only partially, GOOD = recalled \
            correctly, EASY = instant and confident. AGAIN shrinks the card's \
            half-life; the others grow it. Card ids come from quiz_vocab or \
            find_vocab_cards.""";

    private static final String FIND_CARDS = """
            Find cards whose front or back contains the query, \
            case-insensitive, max 20. Use to resolve which card the user means \
            before update_vocab_card or delete_vocab_card — results carry the \
            ids those tools need.""";

    private static final String UPDATE_CARD = """
            Fix one card's content by UUID (ids come from find_vocab_cards): \
            front, back, reading/example, or suspended (true pauses it out of \
            quizzes and the brief without losing history — prefer suspending \
            over deleting a learned word). Pass EVERY field at its intended \
            final value — this is a full replace. The memory model is not \
            touched; only reviews move it.""";

    private static final String DELETE_CARD = """
            Permanently delete one card AND its review history by UUID (from \
            find_vocab_cards). No undo — only on explicit user intent; for "từ \
            này dễ quá khỏi ôn" prefer update_vocab_card with suspended=true.""";

    /** One entry of a save_vocab_cards call; "" fields mean "none". */
    record VocabItem(String front, String back, String reading, String example,
            String disciplineName) {
    }

    private final VocabService vocab;
    private final DisciplineService disciplines;

    VocabTools(VocabService vocab, DisciplineService disciplines) {
        this.vocab = vocab;
        this.disciplines = disciplines;
    }

    @Tool(name = "save_vocab_cards", description = SAVE_CARDS)
    @McpTool(name = "save_vocab_cards", description = SAVE_CARDS,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    List<VocabCardSummary> saveVocabCards(
            @ToolParam(description = "The cards to save — one per word = meaning pair")
            @McpToolParam(description = "The cards to save — one per word = meaning pair",
                    required = true) List<VocabItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must contain at least one card");
        }
        List<NewVocabCard> resolved = items.stream()
                .map(item -> new NewVocabCard(item.front(), item.back(),
                        ToolSupport.vocabMetadata(item.reading(), item.example()),
                        disciplineIdByName(disciplines, item.disciplineName())))
                .toList();
        return vocab.createAll(resolved);
    }

    @Tool(name = "quiz_vocab", description = QUIZ)
    @McpTool(name = "quiz_vocab", description = QUIZ,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<VocabCardSummary> quizVocab(
            @ToolParam(description = "How many cards, 1-50; defaults to 5", required = false)
            @McpToolParam(description = "How many cards, 1-50; defaults to 5",
                    required = false) Integer count) {
        return vocab.atRisk(count == null ? 5 : count, null);
    }

    @Tool(name = "record_vocab_review", description = RECORD_REVIEW)
    @McpTool(name = "record_vocab_review", description = RECORD_REVIEW,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    VocabCardSummary recordVocabReview(
            @ToolParam(description = "The card's UUID")
            @McpToolParam(description = "The card's UUID", required = true) String cardId,
            @ToolParam(description = "AGAIN, HARD, GOOD, or EASY")
            @McpToolParam(description = "AGAIN, HARD, GOOD, or EASY",
                    required = true) String rating) {
        VocabReviewLog.Rating parsed = parseRating(rating);
        double success = switch (parsed) {
            case AGAIN -> 0.0;
            case HARD -> 0.6;
            case GOOD -> 0.9;
            case EASY -> 1.0;
        };
        return vocab.recordReview(UUID.fromString(cardId), success, parsed,
                VocabReviewLog.ReviewSource.CHAT);
    }

    @Tool(name = "find_vocab_cards", description = FIND_CARDS)
    @McpTool(name = "find_vocab_cards", description = FIND_CARDS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<VocabCardSummary> findVocabCards(
            @ToolParam(description = "Part of the word or its meaning, e.g. '磨蹭' or 'lề mề'")
            @McpToolParam(description = "Part of the word or its meaning, e.g. '磨蹭' or 'lề mề'",
                    required = true) String query) {
        return vocab.search(query);
    }

    @Tool(name = "update_vocab_card", description = UPDATE_CARD)
    @McpTool(name = "update_vocab_card", description = UPDATE_CARD,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    VocabCardSummary updateVocabCard(
            @ToolParam(description = "The card's UUID")
            @McpToolParam(description = "The card's UUID", required = true) String cardId,
            @ToolParam(description = "The word/phrase side")
            @McpToolParam(description = "The word/phrase side", required = true) String front,
            @ToolParam(description = "The meaning side")
            @McpToolParam(description = "The meaning side", required = true) String back,
            @ToolParam(description = "Pronunciation (pinyin/IPA); pass '' for none", required = false)
            @McpToolParam(description = "Pronunciation (pinyin/IPA); pass '' for none",
                    required = false) String reading,
            @ToolParam(description = "One example sentence with translation; pass '' for none", required = false)
            @McpToolParam(description = "One example sentence with translation; pass '' for none",
                    required = false) String example,
            @ToolParam(description = "true pauses the card out of quizzes; false resumes it")
            @McpToolParam(description = "true pauses the card out of quizzes; false resumes it",
                    required = true) boolean suspended) {
        UUID id = UUID.fromString(cardId);
        VocabCardSummary current = vocab.find(id);
        return vocab.update(id, front, back, ToolSupport.vocabMetadata(reading, example),
                current.disciplineId(), suspended);
    }

    @Tool(name = "delete_vocab_card", description = DELETE_CARD)
    @McpTool(name = "delete_vocab_card", description = DELETE_CARD,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    String deleteVocabCard(
            @ToolParam(description = "The card's UUID")
            @McpToolParam(description = "The card's UUID", required = true) String cardId) {
        UUID id = UUID.fromString(cardId);
        VocabCardSummary victim = vocab.find(id);
        vocab.delete(id);
        return "Deleted vocab card \"" + victim.front() + "\"";
    }

    private static VocabReviewLog.Rating parseRating(String rating) {
        if (rating == null || rating.isBlank()) {
            throw new IllegalArgumentException("rating must be AGAIN, HARD, GOOD, or EASY");
        }
        try {
            return VocabReviewLog.Rating.valueOf(rating.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "rating must be AGAIN, HARD, GOOD, or EASY — got '" + rating + "'");
        }
    }
}
