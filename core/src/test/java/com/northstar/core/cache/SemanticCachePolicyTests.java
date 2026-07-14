package com.northstar.core.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SemanticCachePolicyTests {

    private final SemanticCachePolicy policy = new SemanticCachePolicy();

    @Test
    void onlyAnExplicitlySafeStatelessReadIsEligible() {
        SemanticCacheContext safe = new SemanticCacheContext(
                true, true, true, true, true, true, true, true);

        assertThat(policy.evaluate(safe).eligible()).isTrue();
        assertThat(policy.evaluate(safe).rejections()).isEmpty();
    }

    @Test
    void everyUnsafeDimensionRejectsCaching() {
        List<Case> cases = List.of(
                new Case(new SemanticCacheContext(false, true, true, true, true, true, true, true),
                        SemanticCacheRejection.DISABLED),
                new Case(new SemanticCacheContext(true, false, true, true, true, true, true, true),
                        SemanticCacheRejection.NOT_READ_ONLY),
                new Case(new SemanticCacheContext(true, true, false, true, true, true, true, true),
                        SemanticCacheRejection.INCOMPLETE_CONTEXT),
                new Case(new SemanticCacheContext(true, true, true, false, true, true, true, true),
                        SemanticCacheRejection.USES_TOOLS),
                new Case(new SemanticCacheContext(true, true, true, true, false, true, true, true),
                        SemanticCacheRejection.USES_MEMORY),
                new Case(new SemanticCacheContext(true, true, true, true, true, false, true, true),
                        SemanticCacheRejection.HAS_ATTACHMENTS),
                new Case(new SemanticCacheContext(true, true, true, true, true, true, false, true),
                        SemanticCacheRejection.DEPENDS_ON_LIVE_DATA),
                new Case(new SemanticCacheContext(true, true, true, true, true, true, true, false),
                        SemanticCacheRejection.EVIDENCE_SENSITIVE));

        cases.forEach(testCase -> assertThat(policy.evaluate(testCase.context()))
                .satisfies(decision -> {
                    assertThat(decision.eligible()).isFalse();
                    assertThat(decision.rejections()).contains(testCase.rejection());
                }));
        assertThat(policy.evaluate(null).rejections())
                .containsExactly(SemanticCacheRejection.INCOMPLETE_CONTEXT);
    }

    @Test
    void disabledProviderAlwaysMissesButValidatesWrites() {
        SemanticResponseCache cache = new DisabledSemanticResponseCache();
        SemanticCacheLookup lookup = new SemanticCacheLookup(
                "owner:default", "stable-help", "gateway/model", "instructions-v1",
                "context-none", "Explain spaced repetition", 0.9);
        SemanticCacheValue value = new SemanticCacheValue(
                "A retrieval practice schedule.", Instant.parse("2026-07-14T00:00:00Z"), Map.of());

        assertThat(cache.find(lookup)).isEmpty();
        cache.put(lookup, value, Duration.ofHours(1));
        assertThat(cache.find(lookup)).isEmpty();
    }

    private record Case(SemanticCacheContext context, SemanticCacheRejection rejection) {
    }
}
