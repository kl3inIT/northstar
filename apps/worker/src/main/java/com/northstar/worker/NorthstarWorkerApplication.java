package com.northstar.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background worker. Its job today is search indexing (SearchIndexingWorker):
 * embedding notes/files and captioning images — LLM/Tika-heavy work kept off the
 * api's request threads. It polls on a schedule rather than consuming events,
 * because Modulith's event registry delivers in-process only (a write in the api
 * never reaches this process); {@code SearchService.reindexStale()} is
 * hash-idempotent so polling is cheap. Later: deadline reminders, SRS recompute,
 * streaks, memory compaction. Reuses {@code :core}; does not run migrations.
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.northstar.worker", "com.northstar.core", "com.northstar.integration"})
@EntityScan("com.northstar.core")
@EnableJpaRepositories("com.northstar.core")
public class NorthstarWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NorthstarWorkerApplication.class, args);
    }
}
