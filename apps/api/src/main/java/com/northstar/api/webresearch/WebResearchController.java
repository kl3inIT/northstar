package com.northstar.api.webresearch;

import com.northstar.core.web.WebProviderDescriptor;
import com.northstar.core.web.WebProviderRegistry;
import com.northstar.core.web.WebResearchSettings;
import com.northstar.core.web.WebResearchSettingsService;
import com.northstar.core.web.WebProviderRoute;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/web-research")
class WebResearchController {

    private final WebResearchSettingsService settings;
    private final WebProviderRegistry providers;

    WebResearchController(WebResearchSettingsService settings, WebProviderRegistry providers) {
        this.settings = settings;
        this.providers = providers;
    }

    @GetMapping
    @Operation(operationId = "getWebResearchSettings")
    SettingsResponse get() {
        return response(settings.current());
    }

    @PutMapping
    @Operation(operationId = "updateWebResearchSettings")
    SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        return response(settings.update(request.enabled(), request.searchProviderId(), request.searchRoute(),
                request.pageReaderId(), request.pageReaderRoute(), request.fallbackEnabled()));
    }

    @DeleteMapping("/override")
    @Operation(operationId = "resetWebResearchSettings")
    SettingsResponse reset() {
        return response(settings.reset());
    }

    @GetMapping("/providers")
    @Operation(operationId = "listWebResearchProviders")
    List<WebProviderDescriptor> providers() {
        return providers.descriptors();
    }

    private SettingsResponse response(WebResearchSettings current) {
        return new SettingsResponse(current.enabled(), current.searchProviderId(), current.searchRoute(),
                current.pageReaderId(), current.pageReaderRoute(), current.fallbackEnabled(), current.overridden());
    }

    record SettingsRequest(
            boolean enabled,
            @NotBlank String searchProviderId,
            WebProviderRoute searchRoute,
            @NotBlank String pageReaderId,
            WebProviderRoute pageReaderRoute,
            boolean fallbackEnabled) {
    }

    record SettingsResponse(
            boolean enabled,
            String searchProviderId,
            WebProviderRoute searchRoute,
            String pageReaderId,
            WebProviderRoute pageReaderRoute,
            boolean fallbackEnabled,
            boolean overridden) {
    }
}
