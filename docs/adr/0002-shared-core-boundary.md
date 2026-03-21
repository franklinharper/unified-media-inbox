# ADR 0002: Put The Core Application Boundary In `shared`

## Status

Accepted

## Context

The app targets multiple platforms and already has separate `shared`, `composeApp`, and `server` modules. The project needs a stable place for domain models, repository contracts, and client abstractions that can be reused by both CLI and GUI code.

## Decision

The `shared` module is the core application boundary.

It owns:

- shared domain models
- client interfaces and registry
- repository interfaces and orchestration logic
- persistence abstractions and adapters where practical
- cross-platform business rules such as seen filtering and feed merging

The CLI and GUI should depend on shared repositories and interfaces rather than directly on concrete client implementations.

## Consequences

- The CLI and GUI can share the same feed-loading and persistence logic.
- Platform client implementations stay behind common interfaces.
- The architecture stays simpler than introducing a separate use-case layer immediately.
- Concrete clients should not own app-level concerns like multi-source merging or seen filtering.
