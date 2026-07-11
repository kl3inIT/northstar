package com.northstar.core.study;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "speaking_feedback")
public class SpeakingFeedback extends BaseEntity {

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @NotBlank
    @Column(nullable = false, length = 1000)
    private String question;

    @NotBlank
    @Column(nullable = false, length = 8000)
    private String transcript;

    @Column
    private Double pronunciation;

    @Column
    private Double fluency;

    @Column
    private Double prosody;

    @NotBlank
    @Column(name = "content_scores", nullable = false, length = 1000)
    private String contentScores;

    @NotBlank
    @Column(name = "top_errors", nullable = false, length = 4000)
    private String topErrors;

    @NotBlank
    @Column(nullable = false, length = 4000)
    private String summary;

    @NotBlank
    @Column(name = "grader_model", nullable = false, length = 128)
    private String graderModel;

    @NotBlank
    @Column(name = "delivery_provider", nullable = false, length = 64)
    private String deliveryProvider;

    @NotBlank
    @Column(name = "provider_revision", nullable = false, length = 128)
    private String providerRevision;

    protected SpeakingFeedback() {
        // for JPA
    }

    public SpeakingFeedback(UUID id, Instant submittedAt, String question, String transcript,
            Double pronunciation, Double fluency, Double prosody, String contentScores,
            String topErrors, String summary, String graderModel, String deliveryProvider,
            String providerRevision) {
        super(id);
        this.submittedAt = submittedAt;
        this.question = question;
        this.transcript = transcript;
        this.pronunciation = pronunciation;
        this.fluency = fluency;
        this.prosody = prosody;
        this.contentScores = contentScores;
        this.topErrors = topErrors;
        this.summary = summary;
        this.graderModel = graderModel;
        this.deliveryProvider = deliveryProvider;
        this.providerRevision = providerRevision;
    }

    public Instant getSubmittedAt() { return submittedAt; }
    public String getQuestion() { return question; }
    public String getTranscript() { return transcript; }
    public Double getPronunciation() { return pronunciation; }
    public Double getFluency() { return fluency; }
    public Double getProsody() { return prosody; }
    public String getContentScores() { return contentScores; }
    public String getTopErrors() { return topErrors; }
    public String getSummary() { return summary; }
    public String getGraderModel() { return graderModel; }
    public String getDeliveryProvider() { return deliveryProvider; }
    public String getProviderRevision() { return providerRevision; }
}
