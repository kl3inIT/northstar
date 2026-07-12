package com.northstar.core.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class WebResearchServiceTests {

    @Test
    void runtimeProviderSwitchAppliesToTheNextRequestWithoutRestart() {
        FakeSearch first = new FakeSearch("first", false);
        FakeSearch second = new FakeSearch("second", false);
        WebProviderRegistry registry = new WebProviderRegistry(List.of(first, second), List.of(new FakeReader()));
        WebResearchSettingRepository repository = repository();
        WebResearchSettingsService settings = settings(repository, registry, defaults(false, List.of()));
        WebResearchService service = new WebResearchService(settings, registry);

        assertThat(service.search(WebSearchRequest.of("before switch")).providerId()).isEqualTo("first");

        settings.update(true, "second", "direct", false);

        WebSearchResult after = service.search(WebSearchRequest.of("after switch"));
        assertThat(after.providerId()).isEqualTo("second");
        assertThat(after.answer()).isEqualTo("answer from second");
    }

    @Test
    void retryableFailureUsesConfiguredFallbackAndReportsTheOrigin() {
        FakeSearch first = new FakeSearch("first", true);
        FakeSearch second = new FakeSearch("second", false);
        WebProviderRegistry registry = new WebProviderRegistry(List.of(first, second), List.of(new FakeReader()));
        WebResearchSettingsService settings = settings(repository(), registry, defaults(true, List.of("second")));
        WebResearchService service = new WebResearchService(settings, registry);

        WebSearchResult result = service.search(WebSearchRequest.of("fallback probe"));

        assertThat(result.providerId()).isEqualTo("second");
        assertThat(result.fallbackFrom()).isEqualTo("first");
    }

    @Test
    void disablingFallbackDoesNotReuseACachedFallbackResult() {
        FakeSearch first = new FakeSearch("first", true);
        FakeSearch second = new FakeSearch("second", false);
        WebProviderRegistry registry = new WebProviderRegistry(List.of(first, second), List.of(new FakeReader()));
        WebResearchSettingsService settings = settings(repository(), registry, defaults(true, List.of("second")));
        WebResearchService service = new WebResearchService(settings, registry);
        WebSearchRequest request = WebSearchRequest.of("same query");

        assertThat(service.search(request).providerId()).isEqualTo("second");
        settings.update(true, "first", "direct", false);

        assertThatThrownBy(() -> service.search(request))
                .isInstanceOfSatisfying(WebResearchException.class,
                        exception -> assertThat(exception.code()).isEqualTo(WebResearchFailureCode.UNAVAILABLE));
    }

    private static WebResearchDefaults defaults(boolean fallback, List<String> fallbackOrder) {
        return new WebResearchDefaults(true, "first", WebProviderRoute.none(), "direct",
                WebProviderRoute.none(), fallback, fallbackOrder, List.of(),
                Duration.ofMinutes(5), 20);
    }

    private static WebResearchSettingsService settings(WebResearchSettingRepository repository,
            WebProviderRegistry registry, WebResearchDefaults defaults) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("defaults", defaults);
        return new WebResearchSettingsService(repository, registry, beans.getBeanProvider(WebResearchDefaults.class));
    }

    private static WebResearchSettingRepository repository() {
        WebResearchSettingRepository repository = mock(WebResearchSettingRepository.class);
        AtomicReference<WebResearchSetting> row = new AtomicReference<>();
        when(repository.findById(WebResearchSetting.SINGLETON_ID))
                .thenAnswer(_ -> Optional.ofNullable(row.get()));
        when(repository.save(any(WebResearchSetting.class))).thenAnswer(invocation -> {
            WebResearchSetting value = invocation.getArgument(0);
            row.set(value);
            return value;
        });
        doAnswer(_ -> {
            row.set(null);
            return null;
        }).when(repository).deleteById(WebResearchSetting.SINGLETON_ID);
        return repository;
    }

    private record FakeSearch(String id, boolean fail) implements WebSearchProvider {

        @Override
        public String displayName() {
            return id;
        }

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public WebSearchProviderResult search(WebSearchRequest request) {
            if (fail) {
                throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE, "temporary failure");
            }
            return new WebSearchProviderResult("answer from " + id, List.of());
        }
    }

    private static final class FakeReader implements WebPageReader {
        @Override
        public String id() {
            return "direct";
        }

        @Override
        public String displayName() {
            return "Direct";
        }

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public boolean supports(URI url) {
            return true;
        }

        @Override
        public WebPageProviderResult read(WebPageRequest request) {
            return new WebPageProviderResult(request.url(), "title", "content", "text/plain", false);
        }
    }
}
