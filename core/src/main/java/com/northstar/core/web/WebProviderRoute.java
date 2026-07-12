package com.northstar.core.web;

public record WebProviderRoute(String gatewayId, String targetId) {

    public WebProviderRoute {
        gatewayId = normalize(gatewayId);
        targetId = normalize(targetId);
    }

    public static WebProviderRoute none() {
        return new WebProviderRoute("", "");
    }

    public boolean complete() {
        return !gatewayId.isBlank() && !targetId.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
