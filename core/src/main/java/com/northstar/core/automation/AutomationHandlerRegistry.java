package com.northstar.core.automation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AutomationHandlerRegistry {

    private final Map<String, AutomationHandler<?>> handlers;
    private final ObjectMapper mapper;

    AutomationHandlerRegistry(List<AutomationHandler<?>> handlers, ObjectProvider<ObjectMapper> mapper) {
        Map<String, AutomationHandler<?>> indexed = new LinkedHashMap<>();
        for (AutomationHandler<?> handler : handlers) {
            String type = normalized(handler.type());
            if (type.isBlank()) throw new IllegalStateException("An automation handler has a blank type");
            if (indexed.putIfAbsent(type, handler) != null) {
                throw new IllegalStateException("Duplicate automation handler type: " + type);
            }
        }
        this.handlers = Map.copyOf(indexed);
        this.mapper = mapper.getIfAvailable(ObjectMapper::new);
    }

    public List<AutomationTypeDescriptor> descriptors() {
        return handlers.values().stream()
                .map(handler -> new AutomationTypeDescriptor(handler.type(), handler.displayName(),
                        handler.description(), handler.configVersion(), toMap(handler.defaultConfig())))
                .sorted(java.util.Comparator.comparing(AutomationTypeDescriptor::displayName))
                .toList();
    }

    public int validate(String type, Map<String, Object> config) {
        AutomationHandler<?> handler = require(type);
        validateTyped(handler, config == null ? Map.of() : config);
        return handler.configVersion();
    }

    public AutomationHandlerResult execute(String type, Map<String, Object> config,
            AutomationExecutionContext context) {
        AutomationHandler<?> handler = require(type);
        return executeTyped(handler, config, context);
    }

    private AutomationHandler<?> require(String type) {
        AutomationHandler<?> handler = handlers.get(normalized(type));
        if (handler == null) throw new IllegalArgumentException("Unsupported automation type: " + type);
        return handler;
    }

    private <C> void validateTyped(AutomationHandler<C> handler, Map<String, Object> config) {
        C typed = mapper.convertValue(config, handler.configType());
        handler.validate(typed);
    }

    private <C> AutomationHandlerResult executeTyped(AutomationHandler<C> handler,
            Map<String, Object> config, AutomationExecutionContext context) {
        C typed = mapper.convertValue(config, handler.configType());
        handler.validate(typed);
        return handler.execute(context, typed);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        return mapper.convertValue(value, Map.class);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toLowerCase();
    }
}
