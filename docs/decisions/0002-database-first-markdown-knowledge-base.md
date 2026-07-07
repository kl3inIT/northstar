# 0002 - Database-First Markdown Knowledge Base

Status: accepted

## Context

Northstar needs Obsidian-like portability while also supporting structured
search, backlinks, AI capture, tasks, projects, and agent access. A filesystem
Markdown vault is portable but weak for relational workflows; a pure database
model is queryable but risks losing Markdown portability.

## Decision

PostgreSQL is the source of truth. Notes store their body as Markdown in the
database. Wiki links, backlinks, search indexes, and vector embeddings are
derived from persisted source records.

## Consequences

Markdown remains the portable representation, while the database can enforce
relationships and power search. Derived indexes must be rebuildable and should
not become the authoritative copy of user knowledge.
