package com.northstar.core.finance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryCorrectionRepository extends JpaRepository<CategoryCorrection, UUID> {

    Optional<CategoryCorrection> findByTypeAndDescriptionKey(
            TransactionType type, String descriptionKey);

    List<CategoryCorrection> findTop12ByOrderByUpdatedAtDescCreatedAtDesc();
}
