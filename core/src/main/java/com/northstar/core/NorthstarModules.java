package com.northstar.core;

import org.springframework.modulith.Modulithic;

/**
 * Marker type whose package ({@code com.northstar.core}) is the base package for
 * Spring Modulith. Every direct sub-package (note, capture, task, study,
 * scholarship, discipline, finance, habit, calendar, search, shared) is an
 * application module.
 *
 * <p>Used by the Modulith verification test and reusable across the api, mcp and
 * worker applications, all of which bootstrap these same modules.
 */
@Modulithic(systemName = "Northstar")
public final class NorthstarModules {

    private NorthstarModules() {
    }
}
