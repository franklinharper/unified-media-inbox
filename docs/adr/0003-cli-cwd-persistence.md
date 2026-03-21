# ADR 0003: Store CLI Persistence In The Current Working Directory

## Status

Accepted

## Context

The CLI is primarily a testing and development tool. It should be easy to run the app in different directories and get isolated configurations, sessions, caches, and feed state without affecting a shared global profile.

## Decision

CLI persistence will live in the current working directory.

The CLI should otherwise stay as close as practical to the GUI persistence model, schema, and storage abstractions.

## Consequences

- Different directories can act as independent test environments.
- CLI reset operations apply only to the current directory’s persisted app data.
- The CLI remains close to the GUI storage model while still being convenient for testing.
