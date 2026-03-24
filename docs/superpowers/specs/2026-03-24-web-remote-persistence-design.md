# Web Remote Persistence Design

## Status

Approved for planning.

## Context

The current GUI work introduced local persistence-backed app containers for desktop, Android, iOS, JS web, and Wasm web. That model fits desktop and mobile well, but it makes the web target more complex than necessary and pushes browser-specific persistence and SQLDelight worker concerns into the first web release.

The project already separates platform bootstrap from the shared application boundary. `shared` owns the domain models, repository contracts, and app-level orchestration. `composeApp` should continue depending on repository interfaces rather than directly on local database details.

The server module is effectively empty today, so introducing server-backed persistence for the web target is both a product decision and a new backend scope.

## Goals

- Make the web client require authentication before showing user data
- Move web persistence and per-user state to the server
- Keep desktop, Android, and iOS on local persistence for now
- Preserve `shared` as the application boundary for feed loading, source management, and seen-state behavior
- Keep the existing GUI shell reusable across local-first and server-backed targets

## Non-Goals

- Converting desktop, Android, or iOS to server-backed persistence in this batch
- Designing multi-device sync for non-web targets
- Replacing shared repository contracts with server-specific UI logic
- Building a large general-purpose backend platform beyond what the web client needs
- Solving Twitter support as part of this change

## Product Direction

This change is web-only for now.

- Web becomes an authenticated client backed by server-side user data
- Desktop, Android, and iOS continue using local persistence-backed repositories
- The feed screen, add-source flow, filtering behavior, and item interactions remain functionally aligned across platforms where possible

If the project later wants cross-device sync for all platforms, that can build on this server model, but this spec does not require it.

## User Experience

### Web

The web client opens to a login screen instead of the feed.

After successful authentication:

- the app creates an authenticated remote app container
- the user enters the existing feed shell
- source management, feed loading, refresh, and seen-state changes operate against the server

If the session expires or the server returns unauthorized:

- the web app clears the active authenticated container
- the UI returns to the login screen
- the user sees a clear session-expired or sign-in-required message

The web client should assume server connectivity is required. Offline-first behavior is not part of this design.

### Desktop and Mobile

Desktop, Android, and iOS continue using local app containers and local persistence. Their existing GUI entry flow remains the feed shell rather than a login gate.

## Architecture

### Shared Boundary

`shared` remains the core application boundary.

It should continue to own:

- domain models
- repository contracts
- feed loading and seen-state policy
- source-management orchestration where it belongs across surfaces

This change should not move app logic into `composeApp` just because the web target is remote-backed.

### Platform Bootstrap

Platform bootstrap chooses the repository implementation:

- desktop, Android, iOS: local persistence-backed repositories
- web: authenticated remote repositories backed by HTTP APIs

The Compose UI should not need separate feed logic for local versus remote targets. The app container boundary should absorb that difference.

### Remote Repository Layer

The web target should use repository implementations that translate shared repository operations into authenticated HTTP calls.

That layer may live in `shared` or `composeApp` depending on platform constraints, but the preferred shape is:

- shared contracts remain unchanged where possible
- remote repository adapters implement those contracts
- DTO and transport concerns stay outside the core domain models

## Server Responsibilities

The server becomes the source of truth for all web-user data.

At minimum it must support:

- account identity and authentication
- session issuance and validation
- per-user configured sources
- per-user feed-item persistence or cache state
- per-user seen state
- feed refresh operations

For the first cut, synchronous refresh is acceptable. A later background-job model is optional and out of scope here unless latency proves unacceptable.

## Data Model Direction

The server should use one shared database schema with `user_id` ownership on persisted records, rather than a separate physical database per user.

This is the recommended first cut because it:

- keeps operations simpler
- avoids per-user database lifecycle management
- still gives each user isolated data through ownership constraints

The server-side schema should represent app behavior rather than mirror the current local SQLDelight tables mechanically. API design should stay one layer above raw table structure.

## Authentication Direction

The web client must authenticate before using the app.

This spec intentionally leaves the exact login mechanism open for planning, but it constrains the first implementation to a simple owned flow rather than a broad provider matrix. Suitable options include:

- email/password
- magic link

External OAuth providers may be added later, but they are not required for the first implementation plan.

## Required Server API Capabilities

The server API must cover the behavior the current GUI already expects.

Minimum capability set:

- sign in
- sign out
- restore current session
- list configured sources
- add source
- remove source
- load feed items
- refresh feed
- mark item seen

If the GUI needs small contract adjustments to fit remote execution cleanly, those should happen at the repository boundary, not as ad hoc UI branching.

## Web App State Model

The web app needs an outer application state in addition to the existing feed-shell state:

- unauthenticated
- authenticating
- authenticated
- session expired
- authentication error

Once authenticated, the existing feed and add-source screens should operate on the authenticated remote container.

## Error Handling

The design should distinguish between:

- bad credentials or login failure
- expired session
- temporary network/server failure
- application-level validation failure, such as an invalid RSS URL

Unauthorized responses should force a transition back to the login flow. Temporary server failures should remain in the authenticated app flow and surface retry-friendly errors.

## Testing Focus

The implementation plan should include:

- server API contract tests for authentication, source management, feed loading, and seen state
- remote repository tests covering translation between shared contracts and HTTP APIs
- web app tests for login success, login failure, session restore, and unauthorized-session reset
- regression tests ensuring desktop and mobile bootstrap remain local-persistence based

Routine iOS verification remains deferred during development unless the task is specifically about iOS.

## Rollout Constraints

- The existing desktop/mobile GUI should remain usable during this work
- The web target may temporarily be gated behind a login-only shell until the remote repository wiring is complete
- This work introduces meaningful server scope, so planning should treat backend API, auth, and web bootstrap as first-class tasks rather than incidental follow-ups

## Planning Decisions Captured

- Web is remote-persistence only and requires login
- Desktop, Android, and iOS remain local-persistence based for now
- The shared module remains the application boundary
- The server becomes the source of truth for web users
- The first implementation uses a shared database with per-row `user_id` ownership
- The first auth flow should be a simple owned login mechanism, not a large OAuth matrix
