# ADR 0001: Use A CLI-First Delivery Strategy

## Status

Accepted

## Context

The project starts from template-level scaffolding and needs a practical way to validate shared models, client integrations, and persistence before investing in GUI work.

Building the GUI first would make it harder to distinguish client integration problems from presentation problems and would slow down iteration on shared contracts.

## Decision

The first executable surface will be a JVM CLI that directly exercises shared repositories and platform clients.

The application scope remains read-only in the early phases.

## Consequences

- Client integrations can be validated earlier.
- Shared abstractions are shaped by a simpler surface before GUI work begins.
- Testing repository and client behavior stays easier than with a GUI-first rollout.
- GUI work is deferred until the shared domain and repository boundaries are stable.
