package com.northstar.core.study;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpeakingFeedbackRepository extends JpaRepository<SpeakingFeedback, UUID> {

    List<SpeakingFeedback> findByOrderBySubmittedAtDesc();

    List<SpeakingFeedback> findTop10ByOrderBySubmittedAtDesc();
}
