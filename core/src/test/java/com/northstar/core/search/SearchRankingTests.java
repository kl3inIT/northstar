package com.northstar.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SearchRankingTests {

    @Test
    void rrfUsesOneBasedRanksWithStandardConstant() {
        assertThat(SearchRanking.rrf(0)).isEqualTo(1.0 / 61.0);
        assertThat(SearchRanking.rrf(9)).isEqualTo(1.0 / 70.0);
        assertThat(SearchRanking.rrf(0)).isGreaterThan(SearchRanking.rrf(1));
    }

    @Test
    void rrfRejectsInvalidRanks() {
        assertThatThrownBy(() -> SearchRanking.rrf(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rank");
    }
}
