package com.northstar.core.cache;

import java.util.Objects;
import java.util.Optional;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** A typed facade over Spring Cache that centralizes the unavoidable cast. */
public final class ExactCache<K, V> {

    private final Cache delegate;

    private ExactCache(Cache delegate) {
        this.delegate = delegate;
    }

    public static <K, V> ExactCache<K, V> from(CacheManager manager, String name) {
        Objects.requireNonNull(manager, "manager");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Cache name is required");
        }
        Cache cache = manager.getCache(name);
        if (cache == null) {
            throw new IllegalStateException("Cache is not configured: " + name);
        }
        return new ExactCache<>(cache);
    }

    public Optional<V> find(K key) {
        Objects.requireNonNull(key, "key");
        Cache.ValueWrapper wrapper = delegate.get(key);
        if (wrapper == null) {
            return Optional.empty();
        }
        Object value = wrapper.get();
        if (value == null) {
            delegate.evict(key);
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        V typed = (V) value;
        return Optional.of(typed);
    }

    public void put(K key, V value) {
        delegate.put(Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(value, "value"));
    }

    public void evict(K key) {
        delegate.evict(Objects.requireNonNull(key, "key"));
    }

    public void clear() {
        delegate.clear();
    }

    public String name() {
        return delegate.getName();
    }
}
