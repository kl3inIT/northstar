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

Flutter/mobile authentication is a separate concern. Its short-lived access
tokens and refresh rotation are specified by
[0014 - Mobile Uses Rotating Token Auth](0014-mobile-uses-rotating-token-auth.md),
without changing this browser decision.

## Consequences

The web app gets HttpOnly session cookies, CSRF protection, and simple
deployment operations for a personal system. API tests can disable auth by
default and keep focused domain coverage, while auth behavior is covered in its
own integration test.

Mobile credentials never enter the browser session or browser storage model.
