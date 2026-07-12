package com.northstar.core.study;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface VocabDeckPreferenceRepository extends JpaRepository<VocabDeckPreference, UUID> {

    Optional<VocabDeckPreference> findByLanguageAndDeckIgnoreCase(VocabLanguage language, String deck);
}

