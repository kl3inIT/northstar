package com.northstar.core.study;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

/**
 * One graded essay. Append-only by design (delete, never edit): a grading is
 * a record of what the model said about that essay at that time — the value
 * of the history is the trend and the recurring-error corpus, both of which
 * editing would corrupt. {@code criteria} and {@code topErrors} are JSON
 * strings (same convention as {@link VocabCard#getMetadata()}); the overall
 * band lives in {@code overallMin}/{@code overallMax} columns because it is
 * an estimate RANGE and the trend chart queries it.
 */
@Entity
@Table(name = "writing_feedback")
public class WritingFeedback extends BaseEntity {

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @NotBlank
    @Column(name = "task_label", nullable = false, length = 255)
    private String taskLabel;

    @NotBlank
    @Column(nullable = false, length = 64)
    private String rubric;

    @NotBlank
    @Column(name = "essay_markdown", nullable = false, length = 20000)
    private String essayMarkdown;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "overall_min", nullable = false)
    private double overallMin;

    @Column(name = "overall_max", nullable = false)
    private double overallMax;

    @NotBlank
    @Column(nullable = false, length = 8000)
    private String criteria;

    @NotBlank
    @Column(name = "top_errors", nullable = false, length = 4000)
    private String topErrors;

    @NotBlank
    @Column(nullable = false, length = 4000)
    private String summary;

    @NotBlank
    @Column(name = "grader_model", nullable = false, length = 64)
    private String graderModel;

    protected WritingFeedback() {
        // for JPA
    }

    public WritingFeedback(UUID id, Instant submittedAt, String taskLabel, String rubric,
            String essayMarkdown, int wordCount, double overallMin, double overallMax,
            String criteria, String topErrors, String summary, String graderModel) {
        super(id);
        this.submittedAt = submittedAt;
        this.taskLabel = taskLabel;
        this.rubric = rubric;
        this.essayMarkdown = essayMarkdown;
        this.wordCount = wordCount;
        this.overallMin = overallMin;
        this.overallMax = overallMax;
        this.criteria = criteria;
        this.topErrors = topErrors;
        this.summary = summary;
        this.graderModel = graderModel;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getTaskLabel() {
        return taskLabel;
    }

    public String getRubric() {
        return rubric;
    }

    public String getEssayMarkdown() {
        return essayMarkdown;
    }

    public int getWordCount() {
        return wordCount;
    }

    public double getOverallMin() {
        return overallMin;
    }

    public double getOverallMax() {
        return overallMax;
    }

    public String getCriteria() {
        return criteria;
    }

    public String getTopErrors() {
        return topErrors;
    }

    public String getSummary() {
        return summary;
    }

    public String getGraderModel() {
        return graderModel;
    }
}
