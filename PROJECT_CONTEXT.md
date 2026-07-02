# Northstar Project Context

Northstar is a personal growth operating system for the user and optionally their partner.

Core idea:
- Personal OS, not only scholarship tracking.
- Obsidian-lite knowledge base + AI capture + project management + study/life/finance/habit tracking.
- AI-native memory layer for Claude Code, Codex, and other coding agents through MCP.

Main modules:
- Today dashboard
- AI Capture Inbox
- Notes / Knowledge Base
- Projects
- Study: IELTS + HSK AI tutor
- Scholarships / university research
- Finance management
- Habit tracking
- Life/task management
- Couple/shared workspace
- Tools hub for daily workflows

Architecture direction:
- Monorepo, web first, mobile later.
- Backend: Spring Boot, Spring AI, Spring Modulith, Gradle Kotlin DSL.
- Possible apps/modules: api, mcp, worker, shared.
- Database-first notes with Markdown content for portability.
- PostgreSQL + pgvector for structured data and semantic memory.
- Object storage for files/audio/PDFs.
- Markdown export for Obsidian-like backup and portability.
- MCP server should expose tools for agents to capture work sessions, learnings, decisions, tasks, errors, and project context.

Important product decision:
- DB is source of truth.
- Notes are stored as Markdown in DB.
- Raw capture is preserved; AI extracts structured entities like Task, Learning, Decision, Expense, StudyLog, HabitLog, ProjectMemory.
- Default workspace can be shared for the user and partner, with optional Private visibility.
- Notes should link to each other like Obsidian using wiki links such as `[[IELTS Speaking]]` and `[[dth-crm Gradle recovery]]`.
- Backlinks and graph data should be derived into relational tables, while keeping Markdown as the portable note body.
- Search should be hybrid: exact/full-text search, tag/project filters, and semantic vector search.
- Session continuity should be project-local and explicit through `AGENTS.md`, `PROJECT_CONTEXT.md`, and future MCP capture, not by editing Codex internal state.

First feature recommendation:
- AI Capture Inbox + Today Dashboard.
- User writes one natural sentence; AI parses it into notes, tasks, expenses, study logs, habit logs.

Potential name:
- Northstar means Sao Bac Dau / kim chi nam.
- It represents a guiding goal for personal development.
