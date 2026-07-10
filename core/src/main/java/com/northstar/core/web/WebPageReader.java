package com.northstar.core.web;

import java.net.URI;

public interface WebPageReader {

    String id();

    String displayName();

    boolean configured();

    boolean supports(URI url);

    WebPageProviderResult read(WebPageRequest request);
}
