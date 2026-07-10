package com.northstar.core.finance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link Transaction}. Other modules go through {@link FinanceService}. */
interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** A month's ledger, newest first (ties broken by entry time). */
    List<Transaction> findByOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
            LocalDate from, LocalDate to);

    /** Description search for the assistant's find_transactions — small, recent-first. */
    List<Transaction> findTop20ByDescriptionContainingIgnoreCaseOrderByOccurredOnDesc(String query);

    /** One aggregate per type over a window — previous-month comparisons without loading rows. */
    @Query("select coalesce(sum(t.amount), 0) from Transaction t "
            + "where t.type = :type and t.occurredOn between :from and :to")
    long sumAmount(@Param("type") TransactionType type,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Every category value the ledger has actually used for one type. */
    @Query("select distinct t.category from Transaction t where t.type = :type")
    List<String> distinctCategories(@Param("type") TransactionType type);
}
