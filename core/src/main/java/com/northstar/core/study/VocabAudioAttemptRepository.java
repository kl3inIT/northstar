package com.northstar.core.study;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface VocabAudioAttemptRepository extends JpaRepository<VocabAudioAttempt, UUID> {

    List<VocabAudioAttempt> findByVocabCardIdOrderByCreatedAtDesc(UUID vocabCardId);

    List<VocabAudioAttempt> findByVocabCardIdAndModeOrderByCreatedAtDesc(
            UUID vocabCardId, VocabAudioPracticeMode mode);
}
