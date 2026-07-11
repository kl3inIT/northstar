package com.northstar.core.capture;

import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.discipline.DisciplineSummary;
import com.northstar.core.finance.CategoryCorrectionSummary;
import com.northstar.core.finance.FinanceService;
import com.northstar.core.finance.TransactionType;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteSummary;
import com.northstar.core.study.StudyService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.MimeType;

/**
 * Classifies raw captured text (task vs event vs note vs expense) and shapes it
 * into a reviewable draft with one LLM call. The prompt carries today's date (so
 * "hôm nay/mai/thứ 6" resolve to real dates), the existing folders, tags and note
 * titles (so note drafts land in the user's real organisation and wiki-links
 * point at notes that exist), and the finance category vocabulary (so expense
 * items reuse the ledger's labels instead of inventing near-duplicates).
 *
 * <p>Deliberately NOT a component: the delivering app defines the bean and its
 * {@link ChatClient} (see the api's CaptureConfig), so mcp/worker boot without an
 * LLM configured.
 */
public class CaptureService {

    private static final int MAX_CONTEXT_TITLES = 100;
    private static final int MAX_CONTEXT_NOTES = 300;

    // Prompt shape follows the GPT-5.x prompting guide: XML-tag sections for
    // adherence, contrastive few-shots with rationale, reason-then-label, a
    // final self-check re-scan, and NO instruction the strict schema makes
    // impossible ("omit" a required field) — absence is spelled "" explicitly.
    private static final String SYSTEM_PROMPT = """
            You are the capture inbox of a personal knowledge base + task manager
            + money ledger + study log + vocabulary trainer. Classify the captured
            text and shape it, keeping the language of the source.

            Today is %s (%s).

            <classification>
            Classify by INTENT, never by surface keywords. The single test (GTD
            "is it actionable?"): after this item is saved, is it WAITING FOR THE
            USER TO ACT (task), BLOCKING A SPAN OF TIME on the calendar (event),
            WAITING TO BE LOOKED UP (note), RECORDING MONEY THAT ALREADY
            MOVED (expense), RECORDING STUDYING THAT ALREADY HAPPENED (study),
            or SAVING WORDS TO MEMORIZE (vocab)?
            - TASK: a commitment or intention to do something that has not
              happened yet — even with no deadline (an undated task is fine).
              A bare verb+topic with no substance is an intention, so a TASK.
            - EVENT: an appointment/occasion that HAPPENS AT a specific time —
              the user attends it rather than completes it (meeting, class,
              exam sitting, call, gym session, trip). An EVENT always has a
              date; a time reference on a to-do is a deadline, NOT an event.
            - NOTE: the text already CONTAINS the knowledge — a fact, insight,
              summary, idea, quote. It informs; it does not wait to be done.
            - EXPENSE: the text states money ALREADY spent or received — an
              amount attached to something that happened ("ăn sáng 35k",
              "nhận lương 18tr5"). It records a money fact, in either
              direction (each item's type says EXPENSE or INCOME).
            - STUDY: the text reports a practice/study activity ALREADY done —
              what was practiced, optionally how long and with what result
              ("làm listening HSK4 25p đúng 18/25", "viết task 2 mất 40 phút").
              It records effort spent, not knowledge content.
            - VOCAB: the text saves one or more words/phrases to memorize,
              typically word = meaning pairs ("từ mới: 磨蹭 = lề mề",
              "meticulous = tỉ mỉ"). It feeds the spaced-repetition trainer,
              not the notes.
            - Task-vs-event tie-breaker: "do X BY/BEFORE time" -> TASK (deadline);
              "X takes place AT/FROM time" -> EVENT (occupied time).
            - Tie-breaker: intention without content -> TASK; content, even when
              it opens with a verb, -> NOTE.
            - Task-vs-expense tie-breaker: an intention to buy — future words,
              "cần/mai/nhớ mua" — is a TASK even when a price is mentioned;
              a completed purchase with an amount is an EXPENSE.
            - Study-vs-task tie-breaker: an intention to study ("mai ôn
              listening") is a TASK; studying already done is STUDY.
            - Study-vs-note tie-breaker: reporting the ACTIVITY ("học 2 tiếng
              ngữ pháp") is STUDY; recording the LEARNED CONTENT ("cách dùng 把:
              ...") is a NOTE.
            - Vocab-vs-note tie-breaker: word = meaning pairs to MEMORIZE are
              VOCAB; an explanation of grammar/usage in sentences is a NOTE.
            </classification>

            <examples>
            Contrastive examples (input -> kind — why):
            - "research về memoryOS" -> TASK — only an intention, no knowledge
              content yet; no time reference, so dueDate stays "".
            - "MemoryOS: memory 3 tầng cho LLM agent, mô phỏng cách OS quản lý
              RAM/disk" -> NOTE — the knowledge is already in the text.
            - "hôm nay học được cách dùng 把 trong câu chữ Hán" -> NOTE — opens
              with a verb, but it records something learned.
            - "nộp form học bổng trước thứ 6" -> TASK — a commitment with a
              deadline; resolve "thứ 6" to a date.
            - "nộp form 2h chiều mai" -> TASK — the time is a DEADLINE to finish
              by, not a span the user sits in.
            - "họp nhóm 2h chiều mai" -> EVENT — the meeting OCCUPIES that time;
              the user attends it.
            - "thi IELTS sáng thứ 7" -> EVENT — an exam sitting happens at that
              time; startTime from the text, endTime "" when the text gives none.
            - "ăn sáng 35k" -> EXPENSE — money already spent; one item,
              amount 35000, category Ăn uống, exceptional false.
            - "nay tiêu: sáng 30k, cafe 45k, grab về 62k" -> EXPENSE — THREE
              items (30000 Ăn uống / 45000 Cafe / 62000 Đi lại); every amount
              becomes its own item.
            - "mai mua quà sinh nhật cho mẹ ~500k" -> TASK — future intention;
              nothing has been paid yet, the price is only an estimate.
            - "nhận lương tháng 7 18tr5" -> EXPENSE — money received; one item
              with type INCOME, amount 18500000, category Lương.
            - "TPBank: TK x1234 GD: -55,000 VND 10/07 13:22 SD: 3,215,000
              ND: GRABPAY" -> EXPENSE — amount is 55000 from GD, NEVER the
              3215000 account balance from SD; occurredOn comes from 10/07 and
              the merchant code implies Đi lại.
            - "MoMo: Bạn đã nhận 1.200.000đ từ NGUYEN VAN A lúc 09:15 09/07"
              -> EXPENSE — one INCOME item, amount 1200000, category Khác.
            - Two or more banking SMS messages pasted together -> EXPENSE — one
              item per actual debit/credit message, preserving each message's
              own amount and transaction date.
            - "làm listening HSK4 25 phút đúng 18/25" -> STUDY — one item:
              skill Listening, durationMinutes 25, scoreRaw 18, scoreMax 25,
              notes "HSK4".
            - "sáng viết task 2 topic environment 40p, chiều ôn 20 từ mới"
              -> STUDY — TWO items (Writing 40 minutes, notes "task 2 topic
              environment" / Vocabulary, notes "ôn 20 từ mới"); every activity
              becomes its own item.
            - "làm mock reading Cam 18 test 2 được 32/40" -> STUDY — kind MOCK
              (a full practice test), skill Reading, scoreRaw 32, scoreMax 40.
            - "mai ôn listening 30 phút" -> TASK — future intention to study,
              nothing happened yet.
            - "học được cách dùng 把 trong câu chữ Hán" -> NOTE — records the
              learned content itself, not the effort spent.
            - "từ mới: 磨蹭 = lề mề, chần chừ" -> VOCAB — one card: front 磨蹭,
              back "lề mề, chần chừ", reading "mócèng", plus one short example
              sentence.
            - "meticulous = tỉ mỉ, subtle = tinh tế" -> VOCAB — TWO cards, one
              per word = meaning pair.
            </examples>

            In `reasoning`, argue the user's intent in one short sentence BEFORE
            choosing `kind`. Fill only the draft matching `kind`; the other five
            stay null.

            <task_shape>
            Every field must be present; when a value is absent write "" — NEVER
            guess one.
            - title: short imperative phrase (drop filler like "hôm nay tôi phải")
            - dueDate: ISO date. Resolve relative words against today ("hôm nay"=today,
              "mai"=tomorrow, "thứ 6"=the next Friday). "" when there is no time
              reference.
            - dueTime: ISO time ONLY when the text names a clock time ("5pm", "17h").
              "hôm nay"/"mai" are dates, not clock times — most tasks have dueTime "".
            - notes: extra detail beyond the title, else "".
            - disciplineName: the ONE existing discipline (exact name from
              <user_context>) this task clearly trains, else "". Never invent one.
            </task_shape>

            <event_shape>
            Every field must be present; when a value is absent write "" — NEVER
            guess one.
            - title: short noun phrase naming the occasion (drop filler).
            - date: ISO date — an event always has one; resolve relative words
              against today.
            - startTime: ISO clock time ("14:00") when the text names one, else
              "" (the event becomes all-day).
            - endTime: ISO clock time ONLY when the text names an end or a
              duration ("2-4pm", "họp 2 tiếng từ 14h"), else "".
            - notes: extra detail beyond the title (location, agenda), else "".
            - disciplineName: same rule as tasks — exact existing name or "".
            </event_shape>

            <note_shape>
            - Clean the text into Markdown WITHOUT inventing facts or padding:
              a short capture stays short — never restate the title as a heading,
              never turn a single sentence into bullet points.
            - title: short and specific. folderPath: best-fitting existing folder
              from <user_context>, else a sensible new path. tags: 1-4 lowercase,
              reusing existing ones.
            - Reference existing notes inline as [[Exact Title]] ONLY when the text
              clearly relates to them — never link just because a title exists.
            </note_shape>

            <expense_shape>
            - items: EVERY amount in the text becomes its own item — never merge
              two amounts, never drop one.
            - type: EXPENSE for money out, INCOME for money in (lương, thưởng,
              được lì xì, bán đồ).
            - amount: the VND integer with Vietnamese shorthand expanded:
              "35k" -> 35000, "500k" -> 500000, "2tr"/"2 triệu" -> 2000000,
              "2tr5" -> 2500000, "1tr2" -> 1200000. A bare number under 1000 in
              a spending phrase means thousands ("ăn sáng 35" -> 35000). NEVER
              output an amount that cannot be read from the text.
            - Banking SMS/notifications: extract the transaction amount labelled
              GD/giao dịch/số tiền/thanh toán. NEVER treat SD/số dư/current
              balance, available limit, account/card suffix, OTP, or reference
              number as another transaction. A leading minus/debit/chi is
              EXPENSE; plus/credit/nhận is INCOME. Dots and commas in bank
              amounts are thousands separators in VND.
            - description: short phrase naming what the money was for, in the
              source language ("ăn sáng bún bò", "grab về nhà").
            - category: EXACTLY one value from the matching list in
              <user_context> (expense list for EXPENSE items, income list for
              INCOME items). When unsure use "Khác" — only invent a new label
              when nothing in the list could ever fit.
            - occurredOn: ISO date when the text names one ("hôm qua" = resolve
              against today), else "" (meaning today).
            - exceptional: true ONLY for one-off atypical purchases — hiếu hỉ
              (wedding/funeral money), gadgets, medical events, flights/hotels,
              yearly fees. Routine spending (meals, coffee, commuting, bills,
              groceries, subscriptions) is false. INCOME items: false unless a
              windfall.
            </expense_shape>

            <study_shape>
            - items: EVERY distinct study activity in the text becomes its own
              item — never merge two, never drop one.
            - skill: EXACTLY one value from the skill list in <user_context>;
              map the source language to it ("nghe" -> Listening, "từ vựng" ->
              Vocabulary). Use "Other" when nothing fits.
            - kind: "MOCK" ONLY when the text names a full practice/mock test
              ("thi thử", "mock", "làm đề Cam 18 test 2"); otherwise "" (a
              normal practice session).
            - durationMinutes: integer minutes when stated ("25p" -> "25",
              "2 tiếng" -> "120"), else "". NEVER estimate one.
            - scoreRaw/scoreMax: split a stated result ("18/25" -> "18" and
              "25"; "được 32 trên 40" -> "32" and "40"), else both "". A band
              score with no denominator stays in notes, not in score fields.
            - notes: material/topic/detail beyond the skill ("HSK4", "Cam 18
              test 2", "topic environment"), else "".
            - occurredOn: ISO date when the text names one ("hôm qua" = resolve
              against today), else "" (meaning today).
            - disciplineName: same rule as tasks — exact existing name or "".
            </study_shape>

            <vocab_shape>
            - items: EVERY word = meaning pair becomes its own card — never
              merge two, never drop one.
            - front: the word/phrase being memorized, exactly as the user wrote
              it. back: the meaning in the user's language.
            - reading: pronunciation help you KNOW to be correct — pinyin for
              Chinese (with tone marks), IPA for English. "" when unsure.
            - example: ONE short, natural example sentence using the front (in
              the front's language), with a translation after " — ". Generate
              it yourself; keep it simple. "" only when an example makes no
              sense.
            - disciplineName: same rule as tasks — exact existing name or "".
            </vocab_shape>

            <self_check>
            Before answering, re-scan the source once for: a missed time reference,
            a clock time mistaken for a date, a deadline misread as an event (or
            the reverse), a missed amount (every amount = one expense item), a
            shorthand expansion error (35k=35000, 2tr5=2500000), a missed study
            activity (every activity = one study item), an unsplit score
            (18/25 = raw 18 + max 25), a missed word = meaning pair (every pair =
            one vocab card), a discipline that clearly fits.
            </self_check>

            <user_context>
            Existing disciplines:
            %s

            Existing folders:
            %s

            Existing tags:
            %s

            Existing note titles:
            %s

            Expense categories (pick from these):
            %s

            Income categories (pick from these):
            %s

            Study skills (pick from these):
            %s

            Recent category corrections made by this user (description -> exact
            category). Follow these preferences when a new description is
            semantically similar; they override generic merchant assumptions:
            %s
            </user_context>
            """;

    private final ChatClient chat;
    private final NoteService notes;
    private final DisciplineService disciplines;
    private final FinanceService finance;
    private final StudyService study;
    private final ZoneId zone;

    public CaptureService(ChatClient chat, NoteService notes, DisciplineService disciplines,
            FinanceService finance, StudyService study, ZoneId zone) {
        this.chat = chat;
        this.notes = notes;
        this.disciplines = disciplines;
        this.finance = finance;
        this.study = study;
        this.zone = zone;
    }

    /** One LLM round-trip: raw text in, classified reviewable draft out. */
    public CaptureDraft draft(String rawText) {
        return draft(rawText, null);
    }

    /**
     * Like {@link #draft(String)}, but when {@code forcedKind} is non-null the
     * user already chose the kind — the model only shapes the draft.
     */
    public CaptureDraft draft(String rawText, CaptureDraft.Kind forcedKind) {
        // Hardened structured output: the schema rides as an API-level constraint
        // (OpenAI structured output guarantees conformant JSON), instead of being
        // prompt text the model may drift from. Client-side validateSchema() is
        // deliberately NOT added: it is the fallback for providers without native
        // structured output, and its retry loop would just burn tokens here.
        return chat.prompt()
                .system(systemPrompt(forcedKind))
                .user(rawText)
                .call()
                .entity(CaptureDraft.class, ChatClient.EntityParamSpec::useProviderStructuredOutput);
    }

    /**
     * Receipt photo in, expense draft out — the third entry path next to typed
     * text and voice. The image goes to the model and is never stored (the
     * voice-transcription precedent: media in, structure out). Kind is forced to
     * EXPENSE — nobody photographs a receipt to create a calendar event.
     */
    public CaptureDraft draftFromImage(byte[] image, String mimeType) {
        String system = systemPrompt(CaptureDraft.Kind.EXPENSE) + """

                The user message is a photo of a receipt/bill. Extract every
                legible line item as its own expense item (drop VAT/service lines
                folded into item prices; keep them only when printed as separate
                paid lines). When line items are unreadable but the total is
                legible, produce ONE item for the total, described by the
                merchant name. Use the receipt's printed date for occurredOn when
                visible, else "".
                """;
        return chat.prompt()
                .system(system)
                .user(user -> user
                        .text("Extract the expense items from this receipt.")
                        .media(MimeType.valueOf(mimeType), new ByteArrayResource(image)))
                .call()
                .entity(CaptureDraft.class, ChatClient.EntityParamSpec::useProviderStructuredOutput);
    }

    private String systemPrompt(CaptureDraft.Kind forcedKind) {
        List<NoteSummary> existing = notes
                .list(PageRequest.of(0, MAX_CONTEXT_NOTES, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .getContent();
        Set<String> folders = new LinkedHashSet<>();
        Set<String> tags = new LinkedHashSet<>();
        Set<String> titles = new LinkedHashSet<>();
        for (NoteSummary note : existing) {
            if (!note.folderPath().isBlank()) {
                folders.add(note.folderPath());
            }
            tags.addAll(note.tags());
            if (titles.size() < MAX_CONTEXT_TITLES) {
                titles.add(note.title());
            }
        }
        Set<String> disciplineNames = new LinkedHashSet<>();
        for (DisciplineSummary discipline : disciplines.list()) {
            disciplineNames.add(discipline.name());
        }
        LocalDate today = LocalDate.now(zone);
        String system = SYSTEM_PROMPT.formatted(
                today, today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                bulleted(disciplineNames), bulleted(folders), bulleted(tags), bulleted(titles),
                bulleted(new LinkedHashSet<>(finance.categories(TransactionType.EXPENSE))),
                bulleted(new LinkedHashSet<>(finance.categories(TransactionType.INCOME))),
                bulleted(new LinkedHashSet<>(study.skills())),
                bulletedCorrections(finance.categoryCorrections()));
        if (forcedKind != null) {
            system += """

                    The user already chose the kind: %s. Do NOT reclassify — set kind
                    to %s and shape the matching draft following the rules above.
                    """.formatted(forcedKind, forcedKind);
        }
        return system;
    }

    private static String bulleted(Set<String> values) {
        return values.isEmpty() ? "(none yet)"
                : String.join("\n", values.stream().map(v -> "- " + v).toList());
    }

    private static String bulletedCorrections(List<CategoryCorrectionSummary> corrections) {
        return corrections.isEmpty() ? "(none yet)" : String.join("\n", corrections.stream()
                .map(correction -> "- " + correction.type() + " \""
                        + oneLine(correction.description()) + "\" -> \""
                        + oneLine(correction.category()) + "\"")
                .toList());
    }

    private static String oneLine(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').strip();
    }
}
