package com.northstar.core.study;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link VocabCard}. Other modules go through {@link VocabService}. */
interface VocabCardRepository extends JpaRepository<VocabCard, UUID> {

    List<VocabCard> findByOrderByCreatedAtDesc();

    List<VocabCard> findBySuspendedFalse();

    List<VocabCard> findTop20ByFrontContainingIgnoreCaseOrBackContainingIgnoreCase(
            String front, String back);

    long countByCreatedAtGreaterThanEqual(Instant since);
}
