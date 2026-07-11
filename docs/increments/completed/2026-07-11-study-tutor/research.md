# Study Tutor — Research Input

Two verified research passes feed this increment: a deep-research run (23
sources, 112 extracted claims, 25 adversarially verified: 22 confirmed / 3
refuted) and an OSS scan (all links fetched and confirmed live, 2026-07-11).

## Learning science (verified)

- Of ten common techniques only **practice testing (retrieval)** and
  **distributed practice (spacing)** rate "high utility" across ages/contexts
  (Dunlosky et al. 2013, PSPI; replicated Donoghue & Hattie 2021: d=0.85 /
  0.74). Rereading and highlighting are low utility. Implication: the brief
  must embed retrieval questions, not re-exposure summaries.
  <https://journals.sagepub.com/doi/abs/10.1177/1529100612453266>
- L2-specific meta-analysis (48 experiments, N=3,411, Kim & Webb 2022):
  spacing has a medium-to-large effect; **longer absolute gaps beat shorter
  ones on delayed posttests** (exam-day retention); **equal and expanding
  schedules are statistically equivalent**. Implication: scheduler
  sophistication is low-stakes for one user — catch-up tolerance and long
  absolute gaps matter, algorithm shape does not.
  <https://onlinelibrary.wiley.com/doi/abs/10.1111/lang.12479>
- Semester-long AI-tutor study (N=51): active users gained ~18 percentile
  points (d≈0.7) — supportive but self-selected, vendor-affiliated.
  <https://arxiv.org/pdf/2309.13060>
- Per-card schedulers (SM-2/FSRS) are blind to **semantic interference**:
  near-synonyms learned together interfere (Tinkham 1993/97, Waring 1997;
  acknowledged in fsrs4anki issue #188). Implication: do not introduce
  semantically similar new words the same day.
  <https://arxiv.org/pdf/2508.03275>

## Market lessons (verified)

- **Duolingo gamification misuse** (ACM L@S 2022): XP-farming, streak anxiety,
  cliff-edge churn on streak loss. Validates no-streak, no-leaderboard.
  <https://arxiv.org/pdf/2203.16175>
- **Migii HSK**: what exam-prep sells = mock tests + graded exercise bank +
  time-boxed per-skill roadmap toward a chosen date. Adapt as mock-test
  tracking + target-date-anchored plan, AI-drafted and pull-based.
  <https://migii.net/en/hsk>
- Anki churn folklore (unverified but consistent): the mathematical review
  backlog after missed days is the canonical quit reason.

## LLM-as-grader for IELTS writing (verified)

- Aggregate-reliable, individually imperfect: on 55 officially graded Task 2
  essays GPT-4 matched the official mean exactly (6.027) but ICC 0.814 <
  official inter-rater 0.92; conclusion in both studies: human-in-the-loop.
  (Koraishi 2024; AP Chinese G-theory study arXiv 2507.19980)
- **Prompt scaffolding dominates**: zero-shot QWK ~0.22 → descriptors +
  calibrated exemplar essays + required per-criterion justification ~0.57-0.67
  → LoRA fine-tune 0.76. (Heliyon 2024 PMC11305227; Sci Reports 2026)
- Bias is systematic and model-specific: most models overweight lexical/
  grammar features vs content/coherence; reliability varies per model — pin
  the grader model, spot-check on upgrades. (Computers & Education: AI 2026)
- Refuted in verification (do not cite): per-dimension QWK figures for
  Conventions/Ideas/Organization; "both AES families strongly reliable";
  "composite human+AI beats either".

## Open questions research did not settle

FSRS-vs-SM-2 efficiency head-to-head; validated conversational-SRS precedent;
LLM speaking assessment reliability; evidence-based backlog catch-up
parameters. None block V1.

## OSS worth learning from (all links verified live)

### SRS engines

| Repo | License | Take |
| --- | --- | --- |
| [py-fsrs](https://github.com/open-spaced-repetition/py-fsrs) | MIT | Cleanest FSRS reference: Scheduler/Card(4 states)/ReviewLog + retrievability. Port spec if FSRS chosen. |
| [ts-fsrs](https://github.com/open-spaced-repetition/ts-fsrs) | MIT | `repeat()` previews all four rating outcomes — useful in chat review. |
| [Ebisu](https://github.com/fasiha/ebisu) | Unlicense | Bayesian recall-probability model, **no due-date queue**: `predictRecall` ranks at-risk words any moment; `updateRecall` accepts passive exposures. Official ebisu-java port. |
| [Anki](https://github.com/ankitects/anki) | AGPL | Schema lessons only: card state machine, rating enum, append-only revlog. |
| [Kotoba](https://github.com/mistval/kotoba) | MIT | In-chat quiz session state machine + fuzzy answer matching (readings/alt forms). |
| [obsidian-spaced-repetition](https://github.com/st3v3nmw/obsidian-spaced-repetition) | MIT | Markdown-embedded card syntax over Obsidian-style notes. |
| [Mnemosyne](https://github.com/mnemosyne-proj/mnemosyne) | AGPL | libmnemosyne = UI-agnostic SRS core; no-side-effect **cramming scheduler** for pre-mock review. |
| [Orbit](https://github.com/andymatuschak/orbit) | mixed | Reviews embedded in reading contexts — the brief-delivery concept. |

### HSK / Chinese data and tooling

| Repo | License | Take |
| --- | --- | --- |
| [complete-hsk-vocabulary](https://github.com/drkameleon/complete-hsk-vocabulary) | MIT | Best HSK 3.0 seed: per-level JSON, simplified/traditional, radical, frequency, POS, classifiers. |
| [hanzi-writer](https://github.com/chanind/hanzi-writer) | MIT | Stroke-order animation + write-the-hanzi quiz embeddable in chat cards; mistake-count feeds grading. |
| [makemeahanzi](https://github.com/skishore/makemeahanzi) | LGPL data | Decomposition + etymology per character — tutor explanation material. Keep attribution. |
| [pinyin-pro](https://github.com/zh-lx/pinyin-pro) | MIT | Tone-marked pinyin render + toneless/abbreviated pinyin matching for search. |
| [HanziGraph](https://github.com/mreichhoff/HanziGraph) | MIT | Sentence-mining pipeline (Tatoeba/OPUS, frequency-ranked per word); in-context SRS precedent. |
| [Tatoeba downloads](https://tatoeba.org/en/downloads) | CC BY / CC0 | zh-en sentence pairs + audio; brief quotes a real sentence per due word. |
| [HSK-3.0 (krmanik)](https://github.com/krmanik/HSK-3.0) | per-file | Unique per-level grammar-point JSON — tutor context for HSK sentence grading. |
| [zhongwen](https://github.com/cschiller/zhongwen) | GPL-2.0 | CC-CEDICT parsing + tone-color convention. Patterns only. |

### IELTS / grading

| Repo | License | Take |
| --- | --- | --- |
| [LanguageTool](https://github.com/languagetool-org/languagetool) | LGPL | Java; run as VPS sidecar so tutor cites deterministic rule-level grammar errors. |
| [prometheus-eval](https://github.com/prometheus-eval/prometheus-eval) | Apache-2.0 | Rubric prompt structure: one rubric per criterion with per-score descriptors; relative mode for revisions. |
| [LLM-AES](https://github.com/Xiaochr/LLM-AES) | none (paper LAK'25) | Two-pass grading (fast estimate, deep on demand); CSEE 13k-essay calibration dataset on HF. |
| [my-ielts](https://github.com/hefengxian/my-ielts) | none, no-commercial | Curated synonym/listening keyword datasets. Personal use only, never redistribute. |
| [kajweb/dict](https://github.com/kajweb/dict) | none (scraped) | Rich word schema (IPA, examples, exam questions, audio URLs). Schema inspiration; personal use only. |

### shadcn ecosystem

| Repo | License | Take |
| --- | --- | --- |
| [shadcn-admin](https://github.com/satnaing/shadcn-admin) | MIT | Same stack (Vite + TanStack Router): command palette, settings composition, list-page scaffolding. |
| [assistant-ui](https://github.com/assistant-ui/assistant-ui) | MIT | Tool-call → React component registry: interactive review cards / band breakdowns in the thread. |
| [data-table-filters](https://github.com/openstatusHQ/data-table-filters) | MIT | Faceted filters + URL filter state (nuqs) + NL filtering for study-log/mock tables. |
| [AI Elements](https://github.com/vercel/ai-elements) | MIT | Reasoning panel + inline citations into essay sentences. Read-and-reimplement (expects Next). |
| [Kibo UI](https://github.com/haydenbleasel/kibo) | MIT | Already adopted; check its inventory first to keep one component vocabulary. |
| [novel](https://github.com/steven-tey/novel) | Apache-2.0, dormant | Selection-to-AI-stream UX mechanics; re-express in CM6 (WYSIWYG rejected). |
| [Midday](https://github.com/midday-ai/midday) | AGPL | Assistant-over-own-data UX; zero code reuse. |
| [awesome-shadcn-ui](https://github.com/birobirobiro/awesome-shadcn-ui) | MIT | Ongoing sourcing index. |
