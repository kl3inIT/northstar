/**
 * Shared kernel: base types, ids and domain-event helpers reused by every other
 * module. Declared as an OPEN module so other modules may depend on it without a
 * Modulith boundary violation.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.northstar.core.shared;
