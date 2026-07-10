package com.northstar.core.web;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "web_research_setting")
class WebResearchSetting extends BaseEntity {

    static final UUID SINGLETON_ID = UUID.fromString("7c63f8fc-3137-4f69-9310-49645ef5c343");

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "search_provider_id", nullable = false, length = 64)
    private String searchProviderId;

    @Column(name = "page_reader_id", nullable = false, length = 64)
    private String pageReaderId;

    @Column(name = "fallback_enabled", nullable = false)
    private boolean fallbackEnabled;

    protected WebResearchSetting() {
        // for JPA
    }

    WebResearchSetting(boolean enabled, String searchProviderId, String pageReaderId,
            boolean fallbackEnabled) {
        super(SINGLETON_ID);
        apply(enabled, searchProviderId, pageReaderId, fallbackEnabled);
    }

    void apply(boolean enabled, String searchProviderId, String pageReaderId,
            boolean fallbackEnabled) {
        this.enabled = enabled;
        this.searchProviderId = searchProviderId;
        this.pageReaderId = pageReaderId;
        this.fallbackEnabled = fallbackEnabled;
    }

    boolean enabled() {
        return enabled;
    }

    String searchProviderId() {
        return searchProviderId;
    }

    String pageReaderId() {
        return pageReaderId;
    }

    boolean fallbackEnabled() {
        return fallbackEnabled;
    }
}
