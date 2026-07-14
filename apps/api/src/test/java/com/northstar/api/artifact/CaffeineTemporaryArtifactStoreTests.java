package com.northstar.api.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import com.northstar.core.artifact.TemporaryArtifact;
import com.northstar.core.artifact.TemporaryArtifactMetadata;
import com.northstar.core.artifact.TemporaryArtifactScope;
import com.northstar.core.artifact.TemporaryArtifactWrite;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CaffeineTemporaryArtifactStoreTests {

    private static final TemporaryArtifactScope IMAGE =
            new TemporaryArtifactScope("owner", "session", "image");

    @Test
    void putFindAndPeekStayInsideTheExactScope() {
        CaffeineTemporaryArtifactStore store = store(defaultProperties(), new AtomicLong());
        TemporaryArtifactMetadata stored = store.put(IMAGE, write(4));

        assertThat(store.find(IMAGE, stored.id())).contains(stored);
        assertThat(store.peek(IMAGE, stored.id()).orElseThrow().data()).hasSize(4);
        assertThat(store.peek(new TemporaryArtifactScope("other", "session", "image"), stored.id()))
                .isEmpty();
        assertThat(store.peek(new TemporaryArtifactScope("owner", "session", "audio"), stored.id()))
                .isEmpty();
    }

    @Test
    void expirationUsesTheConfiguredTicker() {
        AtomicLong nanos = new AtomicLong();
        CaffeineTemporaryArtifactStore store = store(defaultProperties(), nanos);
        TemporaryArtifactMetadata stored = store.put(IMAGE, write(4));

        nanos.addAndGet(Duration.ofMinutes(31).toNanos());

        assertThat(store.peek(IMAGE, stored.id())).isEmpty();
        assertThat(store.stats().currentEntries()).isZero();
    }

    @Test
    void rejectsAnArtifactOverThePerItemLimit() {
        TemporaryArtifactProperties properties = new TemporaryArtifactProperties(
                Duration.ofMinutes(30), 3, 10L, 2);
        CaffeineTemporaryArtifactStore store = store(properties, new AtomicLong());

        assertThatThrownBy(() -> store.put(IMAGE, write(4)))
                .hasMessageContaining("exceeds 3 bytes");
    }

    @Test
    void weightedCapacityAlsoBoundsTinyEntryCount() {
        TemporaryArtifactProperties properties = new TemporaryArtifactProperties(
                Duration.ofMinutes(30), 10, 10L, 2);
        CaffeineTemporaryArtifactStore store = store(properties, new AtomicLong());

        List<TemporaryArtifactMetadata> values = List.of(
                store.put(IMAGE, write(1)), store.put(IMAGE, write(1)), store.put(IMAGE, write(1)));

        assertThat(store.stats().currentEntries()).isLessThanOrEqualTo(2);
        assertThat(values.stream().filter(value -> store.find(IMAGE, value.id()).isPresent()).count())
                .isLessThanOrEqualTo(2);
        assertThat(store.stats().evictions()).isPositive();
    }

    @Test
    void effectiveEntryBoundAllowsTheConfiguredCountBeforeEviction() {
        TemporaryArtifactProperties properties = new TemporaryArtifactProperties(
                Duration.ofMinutes(30), 10, 10L, 3);
        CaffeineTemporaryArtifactStore store = store(properties, new AtomicLong());

        List<TemporaryArtifactMetadata> retained = List.of(
                store.put(IMAGE, write(1)), store.put(IMAGE, write(1)), store.put(IMAGE, write(1)));

        assertThat(store.stats().currentEntries()).isEqualTo(3);
        assertThat(retained).allMatch(value -> store.find(IMAGE, value.id()).isPresent());

        store.put(IMAGE, write(1));

        assertThat(store.stats().currentEntries()).isLessThanOrEqualTo(3);
    }

    @Test
    void consumeIsAtomicAcrossConcurrentCallers() throws Exception {
        CaffeineTemporaryArtifactStore store = store(defaultProperties(), new AtomicLong());
        TemporaryArtifactMetadata stored = store.put(IMAGE, write(4));
        Callable<TemporaryArtifact> consume = () -> store.consume(IMAGE, stored.id()).orElse(null);

        try (var executor = Executors.newFixedThreadPool(8)) {
            List<TemporaryArtifact> results = executor.invokeAll(java.util.Collections.nCopies(20, consume))
                    .stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
            assertThat(results).filteredOn(value -> value != null).hasSize(1);
        }
    }

    @Test
    void deletingASessionRemovesEveryCategoryButNoOtherSession() {
        CaffeineTemporaryArtifactStore store = store(defaultProperties(), new AtomicLong());
        TemporaryArtifactScope audio = new TemporaryArtifactScope("owner", "session", "audio");
        TemporaryArtifactScope other = new TemporaryArtifactScope("owner", "other", "image");
        TemporaryArtifactMetadata image = store.put(IMAGE, write(2));
        TemporaryArtifactMetadata sound = store.put(audio, write(2));
        TemporaryArtifactMetadata retained = store.put(other, write(2));

        assertThat(store.deleteSession("owner", "session")).isEqualTo(2);
        assertThat(store.find(IMAGE, image.id())).isEmpty();
        assertThat(store.find(audio, sound.id())).isEmpty();
        assertThat(store.find(other, retained.id())).isPresent();
    }

    private static CaffeineTemporaryArtifactStore store(
            TemporaryArtifactProperties properties, AtomicLong nanos) {
        Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
        Ticker ticker = nanos::get;
        return new CaffeineTemporaryArtifactStore(properties, clock, ticker);
    }

    private static TemporaryArtifactProperties defaultProperties() {
        return new TemporaryArtifactProperties(Duration.ofMinutes(30), 16, 64L, 8);
    }

    private static TemporaryArtifactWrite write(int size) {
        return new TemporaryArtifactWrite(UUID.randomUUID() + ".bin",
                "application/octet-stream", new byte[size]);
    }
}
