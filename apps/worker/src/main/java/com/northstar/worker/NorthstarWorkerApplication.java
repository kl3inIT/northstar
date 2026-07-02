package com.northstar.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background worker. Runs scheduled jobs (deadline reminders, SRS recompute,
 * streaks, Obsidian export, alignment prompts) and consumes Modulith domain
 * events for heavier async work (embeddings, memory compaction, entity
 * extraction). Kept separate from the api so LLM-bound jobs never block request
 * threads. Reuses {@code :core}; does not run migrations.
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.northstar.worker", "com.northstar.core"})
@EntityScan("com.northstar.core")
@EnableJpaRepositories("com.northstar.core")
public class NorthstarWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NorthstarWorkerApplication.class, args);
    }
}
