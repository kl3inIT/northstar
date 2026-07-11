package com.northstar.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiRouteSettingsServiceTests {

    private final AiRouteSettingRepository repository = mock(AiRouteSettingRepository.class);
    private final AiRoute defaultRoute = new AiRoute("openai", "default-model");
    private final AiRouteSettingsService service = new AiRouteSettingsService(
            repository, new AiRouteDefaults(defaults()));

    @Test
    void defaultIsUsedUntilAnOverrideExists() {
        when(repository.findById(AiTask.ASSISTANT)).thenReturn(Optional.empty());

        assertEquals(defaultRoute, service.current(AiTask.ASSISTANT));
    }

    @Test
    void updateValidatesAndPersistsTheSelection() {
        AiClientRouter router = mock(AiClientRouter.class);
        AiRoute selected = new AiRoute("nine-router", "research-combo");
        when(repository.findById(AiTask.ASSISTANT)).thenReturn(Optional.empty());

        var result = service.update(AiTask.ASSISTANT, selected, router);

        verify(router).validate(AiTask.ASSISTANT, selected);
        verify(repository).save(org.mockito.ArgumentMatchers.any(AiRouteSetting.class));
        assertEquals(selected, result.route());
        assertTrue(result.overridden());
    }

    @Test
    void resetDeletesOnlyTheRequestedTask() {
        var result = service.reset(AiTask.CAPTURE);

        verify(repository).deleteById(AiTask.CAPTURE);
        assertEquals(defaultRoute, result.route());
        assertFalse(result.overridden());
    }

    private Map<AiTask, AiRoute> defaults() {
        Map<AiTask, AiRoute> routes = new EnumMap<>(AiTask.class);
        for (AiTask task : AiTask.values()) routes.put(task, defaultRoute);
        return routes;
    }
}
