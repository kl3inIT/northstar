package com.northstar.core.finance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BalanceCheckInRepository extends JpaRepository<BalanceCheckIn, UUID> {

    Optional<BalanceCheckIn> findTopByOrderByCheckedOnDescCreatedAtDesc();

    List<BalanceCheckIn> findTop12ByOrderByCheckedOnDescCreatedAtDesc();
}
