package com.northstar.core.finance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByMonthStartOrderByCreatedAtAsc(LocalDate monthStart);

    Optional<Budget> findByMonthStartAndCategoryIgnoreCase(LocalDate monthStart, String category);

    /** The most recent month before {@code monthStart} that has any budget — the carry-forward source. */
    Optional<Budget> findTopByMonthStartLessThanOrderByMonthStartDesc(LocalDate monthStart);
}
