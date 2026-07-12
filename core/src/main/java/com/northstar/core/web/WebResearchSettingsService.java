package com.northstar.core.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebResearchSettingsService {

    private final WebResearchSettingRepository settings;
    private final WebProviderRegistry providers;
    private final WebResearchDefaults defaults;

    WebResearchSettingsService(WebResearchSettingRepository settings, WebProviderRegistry providers,
            ObjectProvider<WebResearchDefaults> defaults) {
        this.settings = settings;
        this.providers = providers;
        this.defaults = defaults.getIfAvailable(WebResearchDefaults::disabled);
    }

    @Transactional(readOnly = true)
    public WebResearchSettings current() {
        return settings.findById(WebResearchSetting.SINGLETON_ID)
                .map(value -> new WebResearchSettings(
                        value.enabled(), value.searchProviderId(), effectiveRoute(value.searchProviderId(),
                                value.searchRoute(), defaults.searchProviderId(), defaults.searchRoute()),
                        value.pageReaderId(), effectiveRoute(value.pageReaderId(), value.pageReaderRoute(),
                                defaults.pageReaderId(), defaults.pageReaderRoute()),
                        value.fallbackEnabled(), true))
                .orElseGet(() -> new WebResearchSettings(
                        defaults.enabled(), defaults.searchProviderId(), defaults.searchRoute(),
                        defaults.pageReaderId(), defaults.pageReaderRoute(), defaults.fallbackEnabled(), false));
    }

    @Transactional
    public WebResearchSettings update(boolean enabled, String searchProviderId,
            WebProviderRoute searchRoute, String pageReaderId,
            WebProviderRoute pageReaderRoute, boolean fallbackEnabled) {
        String searchId = normalize(searchProviderId, "searchProviderId");
        String readerId = normalize(pageReaderId, "pageReaderId");
        WebProviderRoute normalizedSearchRoute = searchRoute == null ? WebProviderRoute.none() : searchRoute;
        WebProviderRoute normalizedPageRoute = pageReaderRoute == null ? WebProviderRoute.none() : pageReaderRoute;
        WebSearchProvider search = providers.searchProvider(searchId);
        WebPageReader reader = providers.pageReader(readerId);
        if (enabled && (!search.configured(normalizedSearchRoute)
                || !reader.configured(normalizedPageRoute))) {
            throw new WebResearchException(WebResearchFailureCode.NOT_CONFIGURED,
                    "Web research cannot be enabled until both selected providers are configured");
        }
        WebResearchSetting setting = settings.findById(WebResearchSetting.SINGLETON_ID)
                .orElseGet(() -> new WebResearchSetting(enabled, searchId, readerId, fallbackEnabled));
        setting.apply(enabled, searchId, normalizedSearchRoute, readerId,
                normalizedPageRoute, fallbackEnabled);
        settings.save(setting);
        return new WebResearchSettings(enabled, searchId, normalizedSearchRoute,
                readerId, normalizedPageRoute, fallbackEnabled, true);
    }

    @Transactional
    public WebResearchSettings update(boolean enabled, String searchProviderId,
            String pageReaderId, boolean fallbackEnabled) {
        return update(enabled, searchProviderId, WebProviderRoute.none(), pageReaderId,
                WebProviderRoute.none(), fallbackEnabled);
    }

    @Transactional
    public WebResearchSettings reset() {
        settings.deleteById(WebResearchSetting.SINGLETON_ID);
        return new WebResearchSettings(defaults.enabled(), defaults.searchProviderId(),
                defaults.searchRoute(), defaults.pageReaderId(), defaults.pageReaderRoute(),
                defaults.fallbackEnabled(), false);
    }

    public WebResearchDefaults defaults() {
        return defaults;
    }

    private static String normalize(String value, String field) {
        String normalized = value == null ? "" : value.strip().toLowerCase();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static WebProviderRoute effectiveRoute(String providerId, WebProviderRoute route,
            String defaultProviderId, WebProviderRoute defaultRoute) {
        return route.complete() || !providerId.equals(defaultProviderId) ? route : defaultRoute;
    }
}
