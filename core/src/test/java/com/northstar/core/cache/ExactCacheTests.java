package com.northstar.core.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

class ExactCacheTests {

    @Test
    void typedFacadeReadsWritesAndInvalidates() {
        CacheManager manager = new NorthstarCacheConfiguration().northstarCacheManager(
                List.of(new ExactCacheSpec("test", Duration.ofMinutes(1), 10)));
        ExactCache<String, Integer> cache = ExactCache.from(manager, "test");

        assertThat(cache.find("answer")).isEmpty();
        cache.put("answer", 42);
        assertThat(cache.find("answer")).contains(42);

        cache.evict("answer");
        assertThat(cache.find("answer")).isEmpty();
    }

    @Test
    void caffeineProviderAppliesBoundsExpiryAndStatistics() {
        CacheManager manager = new NorthstarCacheConfiguration().northstarCacheManager(
                List.of(new ExactCacheSpec("test", Duration.ofSeconds(30), 7)));
        CaffeineCache cache = (CaffeineCache) manager.getCache("test");

        assertThat(cache).isNotNull();
        assertThat(cache.getNativeCache().policy().eviction().orElseThrow().getMaximum())
                .isEqualTo(7);
        assertThat(cache.getNativeCache().policy().expireAfterWrite().orElseThrow()
                .getExpiresAfter()).isEqualTo(Duration.ofSeconds(30));
        cache.get("missing");
        assertThat(cache.getNativeCache().stats().missCount()).isOne();
    }

    @Test
    void unknownAndDuplicateCachesFailFast() {
        CacheManager manager = new NorthstarCacheConfiguration().northstarCacheManager(
                List.of(new ExactCacheSpec("known", Duration.ofMinutes(1), 10)));

        assertThatThrownBy(() -> ExactCache.from(manager, "unknown"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown");
        assertThatThrownBy(() -> new NorthstarCacheConfiguration().northstarCacheManager(List.of(
                new ExactCacheSpec("same", Duration.ofMinutes(1), 10),
                new ExactCacheSpec("same", Duration.ofMinutes(2), 20))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }
}
