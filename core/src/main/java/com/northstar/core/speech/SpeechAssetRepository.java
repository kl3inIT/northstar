package com.northstar.core.speech;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeechAssetRepository extends JpaRepository<SpeechAsset, UUID> {

    Optional<SpeechAsset> findByCacheKey(String cacheKey);
}
