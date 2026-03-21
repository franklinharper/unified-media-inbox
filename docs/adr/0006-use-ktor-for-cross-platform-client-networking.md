# ADR 0006: Use Ktor For Cross-Platform Client Networking

## Status

Accepted

## Context

The project needs client implementations that work consistently across Android, iOS, Web, Desktop, and the shared code used by those targets.

The earlier client implementations used JVM-only networking APIs. That made those clients unavailable outside JVM targets and created an inconsistent implementation split across platforms.

## Decision

The project will use Ktor client for application networking in shared client code.

Rules:

- networked social clients should live in shared multiplatform code unless a platform limitation forces a narrower scope
- Ktor is the default HTTP transport for client implementations
- CLI-only code may remain JVM-specific
- parser or storage code may still use platform-specific implementations when there is a concrete need, but networking should stay on Ktor

## Consequences

- Twitter can be implemented once in shared code and reused across supported client platforms
- Bluesky and other networked clients can converge on the same transport stack
- the project reduces JVM-only networking dependencies in shared logic
- Ktor engine compatibility now matters for all supported client targets
