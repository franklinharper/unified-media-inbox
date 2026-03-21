# ADR 0004: Use SQLDelight For Local Application Storage

## Status

Accepted

## Context

The app needs local persistence for normalized feed items, configured sources, seen-item state, account sessions, and sync metadata across JVM and future GUI targets.

The project is Kotlin Multiplatform, so the storage approach needs to fit shared business logic and support multiple runtimes.

## Decision

Use SQLDelight as the default local application database layer.

The local database stores normalized application data such as:

- feed items
- feed sources
- configured sources
- seen items
- account sessions
- sync state
- application-level error logs

## Consequences

- The schema and queries are typed and shared across targets.
- Shared repository logic can work against one normalized local model.
- The CLI can use the same schema and storage approach as the GUI.
- Raw API payload caching is deferred and is not the primary app data model.
