# Non-Web Bidirectional Sync Design

## Goal

Enable Android, iOS, JVM desktop, and other non-web clients to keep their local SQLDelight database synchronized with the server database while still supporting offline local edits.

The first version should:

- preserve local-first behavior on non-web clients
- propagate local source and seen-state changes to the server
- pull server-side changes back into the local database
- resolve conflicts with last-write-wins
- run on explicit sync triggers rather than continuous background sync

## Problem

The project currently has two separate data modes:

- web uses server-backed remote repositories
- non-web clients use local SQLDelight repositories

That split means non-web clients do not share account state, configured sources, or seen-item state with the server-backed web experience. A user who signs in on the web and adds sources there will not automatically see those changes on Android or iOS, and local non-web edits do not propagate back to the server.

## Non-Goals

- Do not sync raw feed cache contents in v1.
- Do not add continuous background sync daemons in v1.
- Do not implement complex merge UI or manual conflict resolution in v1.
- Do not introduce CRDT-style multi-master replication.
- Do not make the local database optional for non-web clients.

## Approach

Use server-authoritative bidirectional sync with a local mutation queue.

The local database remains the non-web runtime store. Local edits are written immediately to SQLDelight so the app continues to work offline. Those edits are also recorded as pending sync mutations. On sync, the client:

1. pushes queued local mutations to the server
2. pulls authoritative remote state or remote changes back down
3. reconciles the local database to the resolved server view
4. clears or updates local pending mutation records

Conflict resolution is last-write-wins using server-managed timestamps or versions.

This keeps the offline story practical without making the local database a separate long-term source of truth.

## Sync Scope For V1

Sync these domains in the first version:

- configured sources
- seen or unseen state
- account session state where it affects signed-in server usage
- source errors if they affect user-visible state

Do not sync:

- raw feed cache contents
- feed item bodies as a replicated store
- ephemeral loading state

Feed cache should continue to be rebuilt from normal server-backed feed refreshes rather than treated as replicated application state.

## Authority Model

The server is authoritative after reconciliation.

Non-web clients may make local changes first, but those changes become durable shared state only after sync succeeds. The client should optimistically apply local edits to the SQLDelight store for offline usability, then reconcile to the server result once a sync completes.

If a local change loses a conflict, the local database should be rewritten to match the resolved server state.

## Conflict Resolution

Use last-write-wins in v1.

Each syncable server entity should carry server-managed change metadata:

- a monotonically increasing version, or
- an updated-at timestamp with sufficient precision and server ownership

Recommended rule:

- local mutation is sent with its client-known base version or last-known server version
- server compares incoming mutation against current entity version
- server resolves with last-write-wins and returns the resolved record and its latest server version

This should be applied consistently for:

- add or remove source
- mark item seen or unseen
- session replacement or invalidation
- source error updates where retained

## Client Architecture

Add a shared sync engine in `shared`.

Its responsibilities:

- store pending local mutations
- run push and pull orchestration
- maintain sync cursors or last-sync metadata
- reconcile server responses into SQLDelight repositories
- expose sync status and failures to app code

This sync engine should sit above the existing repository layer rather than rewriting repository internals into network-aware hybrids.

Recommended components:

- `SyncMutationStore`
  - local durable queue of pending mutations
- `SyncCoordinator`
  - orchestrates push, pull, and reconciliation
- `RemoteSyncApi`
  - wraps new server sync endpoints
- `SyncStateRepository`
  - stores last successful sync markers and sync diagnostics

## Local Mutation Model

Represent local changes as explicit commands rather than full snapshots.

Examples:

- `AddConfiguredSource`
- `RemoveConfiguredSource`
- `MarkItemSeen`
- `MarkItemUnseen`
- `ClearSession`
- `UpdateSession`

Each mutation record should include:

- mutation id
- entity type
- entity key
- payload
- local created-at timestamp
- last-known server version if available

This keeps queued local intent explicit and easier to replay or deduplicate than whole-table snapshots.

## Server API Design

Add dedicated sync endpoints rather than forcing clients to infer sync through existing CRUD APIs.

Recommended server API shape:

- `POST /api/sync/push`
  - accepts a batch of local mutations
  - resolves conflicts server-side
  - returns accepted mutations, rejected mutations, and resolved entity state
- `GET /api/sync/pull?since=<cursor>`
  - returns remote changes since the client’s last successful pull
- `GET /api/sync/bootstrap`
  - returns full current syncable state for first-time device hydration

The server should return:

- latest entity state
- current server version or timestamp
- next sync cursor

This avoids making the client reconstruct sync semantics from multiple unrelated endpoints.

## Local Database Changes

Add dedicated sync tables to SQLDelight.

Recommended tables:

- `pending_sync_mutations`
- `sync_state`
- `sync_failures`
- optionally `sync_conflicts` if conflict diagnostics later need separate storage

Existing app tables should continue to store the resolved application state, not a separate “remote mirror.”

## Trigger Model

Run sync on explicit triggers in v1:

- after sign-in
- on app start when a server-backed session exists
- after local source mutations
- after local seen-state mutations
- before or after feed refresh

Recommendation:

- trigger immediately after local mutations, but coalesce aggressively if multiple changes happen close together
- also run a pull before feed refresh if the user is signed in
- when the user signs in and the local database is empty, bootstrap from the server before normal app usage proceeds

This gives most of the value of shared state without background workers or perpetual polling.

## Error Handling

Sync failures should not block basic offline local usage.

Rules:

- local mutation write succeeds first
- failed sync leaves the mutation queued
- transient network errors should not discard local intent
- authentication failures should pause sync and surface a signed-out or reauthenticate-needed state
- unrecoverable mutation rejections should be recorded and the local state reconciled to server truth

UI does not need a full sync dashboard in v1, but app code should have enough signal to show:

- sync in progress
- sync failed
- authentication expired

## Session Behavior

Session state is sync-relevant only where it determines whether the client should use server-backed features.

For v1:

- a signed-in non-web client should sync against the signed-in account’s server state
- sign-out should clear or invalidate queued account-scoped mutations
- session expiry should halt push and pull until reauthentication

If there is any ambiguity between local-only session artifacts and server account session state, the server session should win.

## Testing

Testing should be layered.

Shared tests:

- mutation queue behavior
- last-write-wins reconciliation
- retry and replay behavior
- bootstrap and incremental pull behavior

Server tests:

- sync push conflict resolution
- sync pull cursor behavior
- bootstrap responses

Non-web integration tests:

- local mutation while offline, then later push to server
- remote mutation on another client, then pull into local DB
- sign-in followed by local database hydration from server

Eventually, Android instrumented tests can validate that the real Android app syncs with the server-backed account state, but the core sync logic should be proven in shared tests first.

## Rollout Plan

Suggested implementation order:

1. Add server-side sync metadata and sync endpoints
2. Add client-side sync tables and mutation queue
3. Add shared `SyncCoordinator`
4. Sync configured sources first
5. Sync seen-state second
6. Add source-error sync third
7. Add sign-in bootstrap hydration
8. Integrate trigger-based sync into non-web app flows

## Fixed Decisions

- `GET /api/sync/bootstrap` is required in v1
- `sync_failures` must be durable local state
- source-error state is included in v1 sync scope
- sign-in with an empty local database should bootstrap from server state before normal app usage begins

## Open Questions

- Whether pull should be pure incremental from the start or bootstrap with occasional full snapshots
- Whether non-empty local databases should always perform a full reconciliation at sign-in or only use incremental sync
