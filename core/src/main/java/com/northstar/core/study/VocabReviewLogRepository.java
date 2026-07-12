package com.northstar.core.study;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link VocabReviewLog}. Append-only; nothing ever updates a row. */
interface VocabReviewLogRepository extends JpaRepository<VocabReviewLog, UUID> {

    long countByReviewedAtGreaterThanEqual(Instant since);

    /** Review counts per card in one query — the card list joins these in memory. */
    @Query("select l.cardId as cardId, l.direction as direction, count(l) as reviews "
            + "from VocabReviewLog l where l.cardId in :cardIds "
            + "group by l.cardId, l.direction")
    List<CardReviewCount> countByCard(@Param("cardIds") List<UUID> cardIds);

    interface CardReviewCount {
        UUID getCardId();

        VocabReviewDirection getDirection();

        long getReviews();
    }
}
