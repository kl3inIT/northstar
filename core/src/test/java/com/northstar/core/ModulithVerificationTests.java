package com.northstar.core;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies that the Modulith module boundaries hold: no module reaches into
 * another module's internals; access happens only through public APIs and
 * events. Run with {@code ./gradlew :core:test}.
 */
class ModulithVerificationTests {

    private final ApplicationModules modules = ApplicationModules.of(NorthstarModules.class);

    @Test
    void modulesAreWellFormed() {
        modules.verify();
    }
}
