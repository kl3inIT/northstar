package com.northstar.api.search;

import com.northstar.core.search.SearchService;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Heals the vector index on every api start: notes created before V14, saved
 * while embedding failed, or written through an app without a vector store all
 * get embedded here. Runs on a virtual thread so startup (and the health
 * endpoint the deploy smoke-checks) never waits on OpenAI.
 */
@NullMarked
@Component
class SearchIndexBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexBackfill.class);

    private final SearchService search;

    SearchIndexBackfill(SearchService search) {
        this.search = search;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("search-backfill").start(() -> {
            try {
                search.reindexStale();
            } catch (Exception e) {
                log.warn("Search index backfill failed — next restart retries", e);
            }
        });
    }
}
