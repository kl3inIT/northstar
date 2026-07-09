# 0006 - Web Uses Session Auth First

Status: accepted

## Context

Northstar is currently a same-origin, single-user web application. It stores
private notes, tasks, project context, files, and AI assistant history. The user
also plans a future Flutter client, but the current shipped surface is the web
SPA backed by `apps/api`.

Putting long-lived bearer tokens in browser storage would add unnecessary XSS
blast radius. Implementing a full OAuth/resource-server model now would add
more moving parts than the current single-user product needs.

## Decision

The web app authenticates with a server-side HTTP session managed by Spring
Security 7. Login and logout are JSON endpoints, not form pages. State-changing
same-origin requests use Spring Security's SPA CSRF support.

The single user is configured by environment:

- `NORTHSTAR_AUTH_USERNAME`
- `NORTHSTAR_AUTH_PASSWORD_HASH`

The repository never stores plaintext passwords.

Flutter/mobile authentication is deferred. When the mobile client starts, it
should get a separate token flow with short-lived access tokens and refresh
rotation instead of reusing browser storage patterns.

## Consequences

The web app gets HttpOnly session cookies, CSRF protection, and simple
deployment operations for a personal system. API tests can disable auth by
default and keep focused domain coverage, while auth behavior is covered in its
own integration test.

Mobile remains a future design decision rather than a half-built token system.
