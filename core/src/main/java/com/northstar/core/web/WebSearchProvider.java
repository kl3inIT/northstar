package com.northstar.core.web;

public interface WebSearchProvider {

    String id();

    String displayName();

    boolean configured();

    WebSearchProviderResult search(WebSearchRequest request);
}
