package com.northstar.core.ai;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

public class AiRouteSettingsService {

    private final AiRouteSettingRepository settings;
    private final AiRouteDefaults defaults;

    public AiRouteSettingsService(AiRouteSettingRepository settings, AiRouteDefaults defaults) {
        this.settings = settings;
        this.defaults = defaults;
    }

    @Transactional(readOnly = true)
    public AiRoute current(AiTask task) {
        return settings.findById(task).map(AiRouteSetting::route).orElseGet(() -> defaults.route(task));
    }

    @Transactional(readOnly = true)
    public Map<AiTask, AiRouteSelection> all() {
        Map<AiTask, AiRouteSelection> result = new EnumMap<>(AiTask.class);
        for (AiTask task : AiTask.values()) {
            AiRoute defaultRoute = defaults.route(task);
            result.put(task, settings.findById(task)
                    .map(value -> new AiRouteSelection(value.route(), defaultRoute, true))
                    .orElseGet(() -> new AiRouteSelection(defaultRoute, defaultRoute, false)));
        }
        return Map.copyOf(result);
    }

    @Transactional
    public AiRouteSelection update(AiTask task, AiRoute route, AiClientRouter router) {
        router.validate(task, route);
        AiRouteSetting setting = settings.findById(task)
                .orElseGet(() -> new AiRouteSetting(task, route));
        setting.apply(route);
        settings.save(setting);
        return new AiRouteSelection(route, defaults.route(task), true);
    }

    @Transactional
    public AiRouteSelection reset(AiTask task) {
        settings.deleteById(task);
        AiRoute route = defaults.route(task);
        return new AiRouteSelection(route, route, false);
    }

    public record AiRouteSelection(AiRoute route, AiRoute defaultRoute, boolean overridden) {
    }
}
