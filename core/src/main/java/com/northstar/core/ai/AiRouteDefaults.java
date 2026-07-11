package com.northstar.core.ai;

import java.util.Map;

public record AiRouteDefaults(Map<AiTask, AiRoute> routes) {

    public AiRouteDefaults {
        routes = Map.copyOf(routes);
    }

    public AiRoute route(AiTask task) {
        AiRoute route = routes.get(task);
        if (route == null) {
            throw new IllegalStateException("No default AI route configured for " + task);
        }
        return route;
    }
}
