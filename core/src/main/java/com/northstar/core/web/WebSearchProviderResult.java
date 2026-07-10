package com.northstar.core.web;

import java.util.List;

public record WebSearchProviderResult(String answer, List<WebSource> sources) {

    public WebSearchProviderResult {
        answer = answer == null ? "" : answer.strip();
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
