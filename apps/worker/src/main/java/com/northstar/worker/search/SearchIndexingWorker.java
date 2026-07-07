package com.northstar.worker.search;

import com.northstar.core.search.SearchService;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The worker's job: keep the vector index in step with the corpus, off the api.
 *
 * <p>Embedding, Tika extraction and vision captioning are LLM/CPU-heavy, so per
 * CLAUDE.md they must not run on api request threads. Modulith's event registry
 * delivers in-process only (a note saved in the api never reaches this process),
 * so instead of consuming events the worker polls: {@link SearchService#reindexStale()}
 * is hash-idempotent — it embeds only notes/files whose content hash changed
 * (new saves, uploads, or an {@code INDEX_VERSION} bump) and drops orphaned
 * vectors, so an unchanged corpus costs a handful of indexed SQL lookups. A
 * freshly saved note becomes semantically searchable within one interval; its
 * keyword (tsvector) hits are already instant from the api.
 */
@NullMarked
@Component
class SearchIndexingWorker {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexingWorker.class);

    private final SearchService search;

    SearchIndexingWorker(SearchService search) {
        this.search = search;
    }

    @Scheduled(initialDelay = 15_000, fixedDelay = 20_000)
    void reindex() {
        try {
            search.reindexStale();
        } catch (Exception e) {
            log.warn("Scheduled search reindex failed — next tick retries", e);
        }
    }
}
