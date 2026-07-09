package com.northstar.core.search;

/**
 * Rank-fusion primitives for hybrid retrieval. RRF intentionally uses ranks,
 * not raw scores, because BM25/ts_rank and vector similarities live on unrelated
 * scales.
 */
final class SearchRanking {

    /** Standard RRF dampening constant from Cormack et al.; also the common engine default. */
    private static final int RRF_K = 60;

    private SearchRanking() {
    }

    static double rrf(int zeroBasedRank) {
        if (zeroBasedRank < 0) {
            throw new IllegalArgumentException("rank must be zero or greater");
        }
        return 1.0 / (RRF_K + zeroBasedRank + 1);
    }
}
