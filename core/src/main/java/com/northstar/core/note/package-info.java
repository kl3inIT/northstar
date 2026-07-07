/**
 * Note / Knowledge Base module.
 *
 * <p>Owns notes (Markdown body with {@code [[wiki links]]}), link resolution and
 * backlinks. PostgreSQL is the source of truth; Markdown is the portable note
 * body. See {@code docs/specs/domains/knowledge-base.md}.
 */
package com.northstar.core.note;
