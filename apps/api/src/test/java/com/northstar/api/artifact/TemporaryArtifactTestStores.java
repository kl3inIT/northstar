package com.northstar.api.artifact;

import com.github.benmanes.caffeine.cache.Ticker;
import com.northstar.core.artifact.TemporaryArtifactStore;
import java.time.Clock;
import java.time.Duration;

/** Test-only factory that keeps the production provider implementation package-private. */
public final class TemporaryArtifactTestStores {

    private TemporaryArtifactTestStores() {
    }

    public static TemporaryArtifactStore create() {
        return new CaffeineTemporaryArtifactStore(new TemporaryArtifactProperties(
                Duration.ofMinutes(30), 16 * 1024 * 1024, 64L * 1024 * 1024, 100),
                Clock.systemUTC(), Ticker.systemTicker());
    }
}
