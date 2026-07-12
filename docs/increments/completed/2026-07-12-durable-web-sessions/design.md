# Durable Web Sessions

## Problem

The servlet-container default expired an inactive browser session after 30
minutes, and its in-memory session store lost every login on API restart.
Northstar is a personal application expected to stay signed in for long-lived
daily use.

## Design

- Store web `HttpSession` state in PostgreSQL with Spring Session JDBC.
- Keep Flyway as the only schema owner.
- Default both the idle timeout and persistent cookie lifetime to 30 days via
  `NORTHSTAR_WEB_SESSION_TIMEOUT`.
- Preserve HttpOnly, SameSite=Lax, and production Secure cookie controls.
- Keep explicit logout immediate and leave mobile token lifetimes unchanged.
