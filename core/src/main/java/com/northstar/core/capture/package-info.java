/**
 * AI Capture: the single AI write-path for humans (web, later Telegram) and agents
 * (MCP). Today it turns raw pasted text into a structured note draft (title,
 * folder, tags, cleaned Markdown with wiki-links) that the user reviews before the
 * {@code note} module persists it; later it also parses captures into entities
 * (Task, Expense, StudyLog, HabitLog, ...). The ChatClient bean is provided by the
 * delivering app (api), so this module stays provider-agnostic and apps without an
 * LLM never instantiate it.
 */
package com.northstar.core.capture;
