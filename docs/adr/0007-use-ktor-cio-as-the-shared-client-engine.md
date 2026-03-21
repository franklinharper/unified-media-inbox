# ADR 0007: Use Ktor CIO As The Shared Client Engine

## Status

Accepted

## Context

ADR 0006 established Ktor as the networking library for shared client code.

That still left an engine choice open. Ktor supports multiple engines, including platform-native options such as Darwin on iOS and Js in browsers, and coroutine-native options such as CIO.

The project goal is to support client platforms as equally as possible and avoid behavior drifting across platforms because different engines use different native stacks.

## Decision

The project will keep Ktor CIO as the shared HTTP client engine.

The engine will be configured once in shared code and reused by shared network clients.

## Discussion

Using a single engine is expected to reduce behavior differences across supported client platforms because:

- connection pooling behavior is shared
- timeout behavior is shared
- request and response handling is shared
- networking bugs and fixes stay in one transport stack instead of being split across several engines

This does not guarantee perfectly identical behavior on every platform. TLS, DNS, socket behavior, browser restrictions, and runtime limitations can still vary by platform.

Even with those limits, the project prefers one shared transport stack over mixing per-platform native engines because consistency is the higher priority.

## Consequences

- shared clients continue to use a single Ktor engine implementation
- platform-specific engine wiring is intentionally deferred
- if a specific target later proves incompatible or unreliable with CIO, that will require a new decision record rather than an ad hoc engine split
