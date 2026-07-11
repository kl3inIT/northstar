package com.northstar.core.study;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpeakingService {

    private final SpeakingFeedbackRepository feedbacks;

    SpeakingService(SpeakingFeedbackRepository feedbacks) {
        this.feedbacks = feedbacks;
    }

    public List<SpeakingFeedbackSummary> list() {
        return feedbacks.findByOrderBySubmittedAtDesc().stream()
                .map(SpeakingFeedbackSummary::of).toList();
    }

    public SpeakingFeedbackSummary find(UUID id) {
        return feedbacks.findById(id).map(SpeakingFeedbackSummary::of)
                .orElseThrow(() -> new SpeakingFeedbackNotFoundException(id));
    }

    @Transactional
    public void delete(UUID id) {
        if (!feedbacks.existsById(id)) throw new SpeakingFeedbackNotFoundException(id);
        feedbacks.deleteById(id);
    }

    List<SpeakingFeedback> recentForCorpus() {
        return feedbacks.findTop10ByOrderBySubmittedAtDesc();
    }

    @Transactional
    SpeakingFeedbackSummary save(SpeakingFeedback feedback) {
        return SpeakingFeedbackSummary.of(feedbacks.save(feedback));
    }
}
