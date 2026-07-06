/**
 * Alignment: MFI-style check-ins, machine-drafted. The user does not sit down to
 * journal — the service assembles the day's/week's facts from the task, calendar
 * and note modules, has the LLM write a short honest review, and upserts it as a
 * plain note in the {@code Journal/} folder (so it lives in the KB: linkable,
 * searchable, editable). Like capture, the ChatClient bean is provided by the
 * delivering app (api); apps without an LLM never instantiate this module.
 */
package com.northstar.core.alignment;
