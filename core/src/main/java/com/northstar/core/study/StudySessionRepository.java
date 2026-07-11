package com.northstar.core.study;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository for {@link StudySession}. Other modules go through {@link StudyService}. */
interface StudySessionRepository extends JpaRepository<StudySession, UUID> {

    /** A date window's log, newest first (ties broken by entry time). */
    List<StudySession> findByOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
            LocalDate from, LocalDate to);

    /** Scored mocks oldest-first — the trend line reads straight off this. */
    List<StudySession> findByKindOrderByOccurredOnAscCreatedAtAsc(StudyKind kind);

    /** Every skill value the log has actually used. */
    @Query("select distinct s.skill from StudySession s")
    List<String> distinctSkills();
}
