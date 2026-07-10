package com.northstar.core.web;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WebResearchSettingRepository extends JpaRepository<WebResearchSetting, UUID> {
}
