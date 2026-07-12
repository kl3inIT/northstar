package com.northstar.core.study;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface VocabSchedulingCardRepository extends JpaRepository<VocabSchedulingCard, UUID> {

    List<VocabSchedulingCard> findByVocabCardIdIn(Collection<UUID> vocabCardIds);

    List<VocabSchedulingCard> findByVocabCardId(UUID vocabCardId);

    Optional<VocabSchedulingCard> findByVocabCardIdAndDirection(
            UUID vocabCardId, VocabReviewDirection direction);
}

