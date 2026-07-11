package com.northstar.core.study;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WritingFeedbackRepository extends JpaRepository<WritingFeedback, UUID> {

    List<WritingFeedback> findByOrderBySubmittedAtDesc();

    List<WritingFeedback> findTop10ByOrderBySubmittedAtDesc();
}
