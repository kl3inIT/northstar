package com.northstar.core.study;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One logged unit of studying. {@code occurredOn} is when the studying happened
 * (the user often logs "hôm qua làm listening" a day late), distinct from the
 * audit {@code createdAt}. Duration and score are both optional — a bare
 * "đọc reading 1 passage" still counts; a scored entry carries raw/max so the
 * page can render 18/25 and the mock trend can normalize. Nothing here is
 * exam-specific: IELTS vs HSK is expressed through {@code disciplineId} and the
 * free-but-canonicalized {@code skill} vocabulary.
 */
@Entity
@Table(name = "study_session")
public class StudySession extends BaseEntity {

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @NotBlank
    @Column(nullable = false, length = 64)
    private String skill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StudyKind kind;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "score_raw")
    private Integer scoreRaw;

    @Column(name = "score_max")
    private Integer scoreMax;

    @Column(length = 2000)
    private String notes;

    @Column(name = "discipline_id")
    private UUID disciplineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StudySource source;

    protected StudySession() {
        // for JPA
    }

    public StudySession(UUID id, LocalDate occurredOn, String skill, StudyKind kind,
            Integer durationMinutes, Integer scoreRaw, Integer scoreMax, String notes,
            UUID disciplineId, StudySource source) {
        super(id);
        this.occurredOn = occurredOn;
        this.skill = skill;
        this.kind = kind;
        this.durationMinutes = durationMinutes;
        this.scoreRaw = scoreRaw;
        this.scoreMax = scoreMax;
        this.notes = notes;
        this.disciplineId = disciplineId;
        this.source = source;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public String getSkill() {
        return skill;
    }

    public StudyKind getKind() {
        return kind;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public Integer getScoreRaw() {
        return scoreRaw;
    }

    public Integer getScoreMax() {
        return scoreMax;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getDisciplineId() {
        return disciplineId;
    }

    public StudySource getSource() {
        return source;
    }

    /** Full edit of the user-facing fields; {@code source} never changes. */
    public void edit(LocalDate occurredOn, String skill, StudyKind kind, Integer durationMinutes,
            Integer scoreRaw, Integer scoreMax, String notes, UUID disciplineId) {
        this.occurredOn = occurredOn;
        this.skill = skill;
        this.kind = kind;
        this.durationMinutes = durationMinutes;
        this.scoreRaw = scoreRaw;
        this.scoreMax = scoreMax;
        this.notes = notes;
        this.disciplineId = disciplineId;
    }
}
