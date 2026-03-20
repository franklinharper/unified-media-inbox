# Social Media Client Implementation Plan

## Current Project Shape

The repository currently contains a Kotlin Multiplatform project under `src/` with three main modules:

- `shared`: common Kotlin code shared across targets
- `composeApp`: Compose Multiplatform UI application
- `server`: Ktor server application

The existing code is still template-level scaffolding, so this is a good point to introduce a clean architecture for a multi-platform social media client.

## Delivery Strategy

Before implementing a GUI, the first executable surface should be a command line interface that directly exercises platform implementations.

This CLI-first approach will:

- validate client integrations earlier
- keep the shared abstraction practical
- make client testing easier than starting with a GUI
- support incremental development of RSS and social platform readers

For the time being, the application scope should remain read-only.

The CLI is primarily a testing tool. Its persisted data should live in the current working directory so different directories can hold different test configurations.

## Goals

- Support multiple social platforms through a unified application model
- Hide client-specific implementation details behind common interfaces
- Allow platforms with different capabilities to coexist cleanly
- Start with a CLI before building any GUI
- Keep both CLI and GUI code independent from client implementations
- Persist social sign-ins and configured feed sources across app runs
- Show feed items in chronological order, with the oldest items first
- Show only unseen feed items by default
- Allow an option to include seen feed items
- Leave room for backend-assisted auth or aggregation when needed later

## Architecture Overview

### 1. Put the core abstraction in `shared`

The `shared` module should contain:

- shared domain models
- client interfaces
- repositories for app-level orchestration
- seen-item tracking abstractions
- platform client implementations that can run in common code where practical

This module should become the core of the application.

### 2. Add a dedicated CLI module

Introduce a CLI module before GUI work begins.

Recommended module:

- `cli`: JVM command line application for exercising clients and repositories

The CLI should depend on `shared` and provide commands for:

- listing feed items for a given platform and user
- listing feed items for a given RSS feed URL
- filtering unseen items only by default
- optionally including already seen items
- signing in and signing out
- adding and removing users and RSS feeds
- clearing persisted data

For testing convenience, CLI persistence should live in the current directory rather than using a shared global application data location. It should otherwise stay as close as possible to the persistence model used by the GUI.

### 3. Keep `composeApp` for later GUI work

The `composeApp` module should remain available for future UI work, but it should not drive the first implementation phase.

When GUI work begins, it should contain:

- screens
- view models
- navigation
- presentation state

It should depend on shared interfaces and repositories, not directly on concrete client implementations such as `TwitterClient`, `BlueskyClient`, or `RssClient`.

### 4. Use `server` for backend-only concerns

The `server` module should be reserved for concerns that should not live purely in the client, such as:

- OAuth callback handling
- token exchange
- proxying requests that require protected credentials
- server-side feed aggregation if needed later

Not every client will need the server, but the module is useful for the platforms that do.

## Shared Domain Model

Define common models in `shared/src/commonMain` for concepts the app needs regardless of client:

- `PlatformId`
- `AccountSession`
- `SocialProfile`
- `FeedItem`
- `FeedSource`
- `ConfiguredSource`
- `FeedRequest`
- `FeedQuery`
- `Post`
- `MediaAttachment`
- `FeedPage`
- `FeedCursor`
- `SessionState`
- `SeenState`
- `FeedLoadResult`
- `FeedSourceStatus`
- `ClientError`

The shared model should also include persisted source configuration, so the app can load feed requests from saved users and RSS feeds instead of requiring the caller to provide a full list every time.

These models should be normalized enough for CLI, repository, and later UI logic to work across clients.

Avoid over-normalizing platform-specific features too early. If a client exposes data that does not fit the shared model yet, keep it in client-specific metadata rather than leaking raw API models through the app.

### Feed item requirements

Every `FeedItem` should include enough information to support the initial CLI use case:

- stable client-qualified item ID
- platform ID
- author identity or feed source
- published timestamp
- title, summary, or body text
- permalink if available
- seen or unseen state

Chronological ordering should be based on the normalized published timestamp.

### Feed query shape

`FeedQuery` should be a single-platform query. A repository can still coordinate multiple sources, but each `SocialPlatformClient` should receive only the portion of the request that belongs to its own platform.

Prefer modeling this with distinct query variants rather than one loose structure with nullable fields. For example, `FeedQuery` can be a sealed interface with separate shapes for social-user queries and RSS-feed queries.

For the first version, `FeedQuery` should support:

- one `PlatformId`
- zero or more social user identifiers
- zero or more RSS feed URLs
- optional pagination input such as a `FeedCursor`

Rules:

- a single `FeedQuery` should target exactly one platform
- social platforms use the user identifier list
- RSS uses the feed URL list
- a `FeedQuery` must not contain both social user identifiers and RSS feed URLs
- mixed RSS and social sources should be rejected as invalid input rather than producing undefined behavior
- the repository is responsible for splitting a higher-level request into per-client `FeedQuery` instances when needed
- unseen filtering happens after item normalization and chronological ordering

### Feed request shape

`FeedRequest` should be the higher-level aggregate request passed to the repository.

For the first version, `FeedRequest` should support:

- one or more configured source selections
- enough information for the repository to build one `FeedQuery` per client
- request-level options such as whether seen items should be included

`FeedRequest` should be buildable either:

- directly from explicit CLI or GUI input, or
- from persisted configured sources stored by the application

The CLI and GUI should both call the repository with `FeedRequest` so they share as much logic as possible.

### Exact shared schema

Use concrete shared model shapes early so repository and client contracts are stable.

Illustrative schema:

```kotlin
data class FeedRequest(
    val sources: List<ConfiguredSource>,
    val includeSeen: Boolean = false,
)

sealed interface FeedQuery {
    val platformId: PlatformId

    data class SocialUsers(
        override val platformId: PlatformId,
        val users: List<String>,
        val cursor: FeedCursor? = null,
    ) : FeedQuery

    data class RssFeeds(
        override val platformId: PlatformId = PlatformId.Rss,
        val urls: List<String>,
        val cursor: FeedCursor? = null,
    ) : FeedQuery
}

data class FeedItem(
    val itemId: String,
    val platformId: PlatformId,
    val source: FeedSource,
    val authorName: String?,
    val title: String?,
    val body: String?,
    val permalink: String?,
    val publishedAtEpochMillis: Long,
    val seenState: SeenState,
)

sealed interface ClientError {
    data class NetworkError(val message: String? = null) : ClientError
    data class AuthenticationError(val message: String? = null) : ClientError
    data class RateLimitError(val retryAfterMillis: Long? = null) : ClientError
    data class ParsingError(val message: String? = null) : ClientError
    data class TemporaryFailure(val message: String? = null) : ClientError
    data class PermanentFailure(val message: String? = null) : ClientError
}
```

These shapes can evolve, but the first implementation should stay close to them so the CLI, repository, persistence, and GUI all share the same core model.

### Remaining shared contracts

Define the remaining shared enums and interfaces explicitly early:

```kotlin
enum class PlatformId {
    Rss,
    Bluesky,
    Twitter,
}

data class FeedSource(
    val platformId: PlatformId,
    val sourceId: String,
    val displayName: String,
)

sealed interface ConfiguredSource {
    val platformId: PlatformId

    data class SocialUser(
        override val platformId: PlatformId,
        val user: String,
    ) : ConfiguredSource

    data class RssFeed(
        override val platformId: PlatformId = PlatformId.Rss,
        val url: String,
    ) : ConfiguredSource
}

data class FeedCursor(
    val value: String,
)

data class FeedPage(
    val items: List<FeedItem>,
    val nextCursor: FeedCursor? = null,
)

sealed interface SeenState {
    data object Seen : SeenState
    data object Unseen : SeenState
}

sealed interface SessionState {
    data object NotRequired : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: AccountSession) : SessionState
    data class Expired(val reason: String? = null) : SessionState
}
```

Repository interfaces should also be defined early:

```kotlin
interface FeedRepository {
    suspend fun loadFeedItems(request: FeedRequest): FeedLoadResult
}

interface SeenItemRepository {
    suspend fun markSeen(itemId: String)
    suspend fun markSeen(itemIds: List<String>)
    suspend fun isSeen(itemId: String): Boolean
    suspend fun clearAll()
}

interface ConfiguredSourceRepository {
    suspend fun listSources(): List<ConfiguredSource>
    suspend fun addSource(source: ConfiguredSource)
    suspend fun removeSource(source: ConfiguredSource)
    suspend fun clearAll()
}

interface SessionRepository {
    suspend fun getSessionState(platformId: PlatformId): SessionState
    suspend fun signOut(platformId: PlatformId)
    suspend fun clearAll()
}
```

## Client Interface Design

The initial implementation should prioritize a read-only client contract. Write actions can be introduced later.

### Base interface

```kotlin
interface SocialPlatformClient {
    val id: PlatformId
    val displayName: String

    suspend fun sessionState(): SessionState
    suspend fun loadProfile(accountId: String): SocialProfile
    suspend fun loadFeed(query: FeedQuery, cursor: FeedCursor? = null): FeedPage
}
```

This keeps the first phase focused on the exact requirement:

- fetch feed items for one or more users on a given platform
- fetch feed items for one or more RSS feeds
- normalize results into a common list

Optional capability interfaces such as publishing or reactions can be added later once the read-only workflow is stable.

Concrete implementations should follow this naming:

- `RssClient`
- `BlueskyClient`
- `TwitterClient`

Clients should be responsible only for platform integration:

- fetching data from one platform
- handling platform-specific auth or session rules
- mapping platform responses into shared models

They should not own cross-client application behavior such as merging, seen filtering, or global chronological ordering.

Pagination should be client-scoped rather than global in the first version. If pagination is added later, the repository should manage one cursor per source instead of trying to define a single global cursor across mixed clients.

## Suggested Package Structure

Within `shared/src/commonMain/kotlin/com/franklinharper/social/media/client/`:

- `domain/`
  Shared models and enums
- `client/`
  `SocialPlatformClient` and `ClientRegistry`
- `repository/`
  Aggregation and persistence-facing repositories
- `seen/`
  Seen-item tracking abstractions
- `client/rss/`
  `RssClient` implementation
- `client/bluesky/`
  `BlueskyClient` implementation
- `client/twitter/`
  `TwitterClient` implementation
- `storage/`
  Session and cache persistence abstractions
- `network/`
  Shared HTTP and serialization utilities

For the CLI:

- `cli/src/main/kotlin/...`
  Command parsing, output formatting, and JVM-specific persistence wiring

## Repository Layer

Add repositories in `shared` that coordinate clients and present app-level data.

Examples:

- `FeedRepository`
- `SessionRepository`
- `SeenItemRepository`
- `ConfiguredSourceRepository`

Recommended boundary:

- `SocialPlatformClient` returns `FeedPage`
- `FeedRepository` exposes `suspend fun loadFeedItems(request: FeedRequest): FeedLoadResult`

Responsibilities:

- load feed items from one or more selected clients
- combine feed items from multiple requested sources
- normalize and sort items in chronological order
- filter unseen versus seen content
- manage account sessions
- load and persist configured users and RSS feeds
- coordinate pagination if needed later

This is the main application boundary for the first version. It keeps the architecture simpler than introducing a separate use-case layer immediately.

The repository contract should make partial failure explicit by returning a result that includes:

- merged `FeedItem` values
- per-source success or error status
- optional source-scoped pagination state later

The CLI and GUI should both use this same repository API. The CLI can call suspend functions by running them inside a coroutine scope on the JVM.

### Persisted source configuration

Configured users and RSS feeds should be stored by the app so the user can request new items later without re-entering a large source list.

Recommended behavior:

- store configured social users per platform
- store configured RSS feed URLs
- allow the CLI and GUI to load feeds from saved configuration by default
- allow explicit ad hoc requests when needed

`ConfiguredSourceRepository` should provide the shared abstraction for:

- adding a configured user
- removing a configured user
- adding an RSS feed
- removing an RSS feed
- listing configured sources
- clearing configured sources

### Seen-item tracking

Seen state should be app-managed rather than client-managed.

Recommended approach:

- define a `SeenItemRepository` in shared code
- key items by a stable composite ID such as `platformId:itemId`
- default feed listing to unseen items only
- add an option to include seen items
- support automatic seen marking in the GUI when items scroll off screen

For the first version, an item should become seen only when:

- the CLI is run with `--mark-seen`, or
- the GUI dispatches `MarkSeen` when the item scrolls off screen during an active session, or
- the GUI dispatches `MarkSeen` or `MarkItemsSeen` through another explicit interaction

Items should not automatically become seen merely because they were fetched, initially displayed, or included in a refresh result.

Seen items do not need to disappear immediately from the current on-screen list. They simply should not be returned again as unseen content in a later app session.

For scroll-driven seen marking, the GUI should dispatch `MarkSeen` only after an item has been visible and then becomes fully invisible.

For the first version, a simple local file-backed store in the CLI module is enough. The abstraction should still live in `shared` so the GUI can reuse it later.
For the CLI specifically, persistence should use the same storage abstractions and schema as the GUI where practical, but store its files and database in the current working directory so separate directories can represent separate test environments.

### Why repositories first

For the current scope, repositories are enough to hide app-level orchestration details from the CLI:

- clients hide platform implementation details
- repositories hide multi-source merge, sorting, and seen filtering
- CLI commands only parse input and render output

If the app later grows more workflows, explicit services or use cases can be added on top of repositories without changing the client contracts.

## CLI Requirements

The CLI is the first user-facing interface and should support direct testing of client implementations.

### Core commands

Examples of desired commands:

- list feed items for a given platform and one or more users
- list feed items for one or more RSS feed URLs

Illustrative command shapes:

```text
social-cli list-new-items --platform bluesky --user alice.bsky.social --user bob.bsky.social
social-cli list-new-items --platform twitter --user someuser --user anotheruser
social-cli list-new-items --platform rss --url https://example.com/feed.xml --url https://example.org/other.xml
```

The command should accept:

- a platform identifier
- a list of user identifiers for social platforms
- a list of RSS feed URLs for RSS sources

The CLI should merge all returned feed items into one chronological result set.

CLI invocations are independent runs and do not participate in the 15-minute GUI app-session lifecycle.
CLI persistence should be scoped to the current directory so testers can switch configurations by changing directories, while still using the same persistence approach as closely as practical.

### Management commands

The CLI also needs commands for persistent configuration and sign-in state.

Required command groups:

- sign in to a social client
- sign out from a social client
- add a configured social user
- remove a configured social user
- add a configured RSS feed
- remove a configured RSS feed
- list configured sources
- clear persisted data

Feed-listing commands should support loading from saved configuration so the user can fetch new items without providing a full source list every time.

`Clear persisted data` should be a full reset intended for testing. It should delete all persisted application data, including:

- saved social sign-ins
- configured users
- configured RSS feeds
- seen-item state
- cached feed items
- sync metadata

For the CLI, this reset should apply to all persisted app data in the current working directory, including any database files and related metadata.

### Exact CLI command spec

Start with these concrete commands:

```text
social-cli list-new-items [--platform <platform>] [--user <user> ...] [--url <rss-url> ...] [--include-seen] [--mark-seen]
social-cli signin --platform <platform>
social-cli signout --platform <platform>
social-cli add-user --platform <platform> --user <user>
social-cli remove-user --platform <platform> --user <user>
social-cli add-feed --url <rss-url>
social-cli remove-feed --url <rss-url>
social-cli list-sources
social-cli clear-data
```

Command rules:

- `list-new-items` may use explicit `--user` or `--url` values, or fall back to persisted configured sources when none are supplied
- `list-new-items --platform rss` accepts only `--url`
- non-RSS `list-new-items` commands accept only `--user`
- `signin` and `signout` apply only to clients that support authentication
- `clear-data` performs the full testing reset described above

### Filtering behavior

Default behavior:

- show only unseen feed items
- order items in chronological order across all requested users and feeds

Optional behavior:

- `--include-seen` shows both unseen and seen items

Useful follow-up option:

- `--mark-seen` marks returned items as seen after display

### Output requirements

Each listed item should show at least:

- timestamp
- platform
- author or feed source
- title or content snippet
- canonical ID or URL when available

The initial output can be plain text. JSON output can be added later if needed for scripting.

When multiple users or feeds are queried at once, each row should make the source clear so the merged list remains understandable.

## Authentication Strategy

Authentication should be client-owned but app-coordinated.

- Each client should manage its own auth flow requirements
- Shared code should define session abstractions and storage contracts
- Sensitive exchange steps can be delegated to the `server` module when necessary

For the initial read-only CLI phase, start with clients that can be exercised without complex write flows. RSS is the first target, followed by read-only social timeline access where feasible.

### Session behavior in early phases

In the first phase, `SessionRepository` and `AccountSession` should exist as shared abstractions, but authenticated sessions are optional.

Recommended behavior:

- clients that do not need authentication return a `SessionState` such as `NotRequired`
- clients that support unauthenticated reads may still return `NotRequired` for public feed access
- `SessionRepository` can remain minimal until authenticated clients are added
- the CLI should fail clearly when a requested client requires authentication but no valid session exists

Social media sign-ins should be persisted across app runs until the user signs out or clears persisted data.

The application should expose shared flows for:

- sign in
- sign out
- inspect current session state
- clear all persisted application data

### App session lifecycle

The GUI should also have an application session concept separate from provider authentication.

Recommended behavior:

- an app session starts when the user opens the GUI or resumes interaction after inactivity
- an app session ends after 15 minutes with no user interaction
- when a new app session starts, all configured feeds should be refreshed in parallel
- items marked seen in a previous session should remain hidden from the default unseen view in the new session
- user interaction includes actions such as scrolling, tapping, and window focus changes
- background work such as feed refresh does not count as user interaction
- backgrounding the app does not itself end the session; if the user returns before 15 minutes of inactivity have elapsed, the same session continues

## Error Handling and GUI Presentation

Errors should be modeled in `shared` as structured application errors rather than allowing raw client exceptions to leak into the presentation layer.

Recommended shared error categories:

- `NetworkError`
- `AuthenticationError`
- `RateLimitError`
- `ParsingError`
- `TemporaryFailure`
- `PermanentFailure`

Repositories should translate client-specific failures into shared error types and preserve enough context to identify which source failed.

The repository should be the primary boundary that turns:

- raw client exceptions
- client-specific HTTP or parsing errors
- partial source failures

into shared `ClientError` values and `FeedSourceStatus` entries.

### Error presentation strategy

The GUI should support multiple error scopes because feed loading can fail partially when multiple users or RSS feeds are requested at once.

Recommended presentation rules:

- use a full-screen error only when nothing useful can be shown
- use a top-level warning banner when some sources failed but others succeeded
- use inline source-level error rows or cards for failed users or feeds
- use per-item error rendering only for item-specific failures such as missing preview metadata or media load failures

An error list or diagnostics screen can still exist later, but it should not be the primary presentation for normal feed failures.

### Recommended feed result shape

The repository layer should return both feed items and per-source load status so the GUI can render partial failures cleanly.

The same `FeedLoadResult` shape should also be used by the CLI so command output can report partial failures consistently.

Illustrative shape:

```kotlin
data class FeedLoadResult(
    val items: List<FeedItem>,
    val sourceStatuses: List<FeedSourceStatus>,
)

data class FeedSourceStatus(
    val source: FeedSource,
    val state: SourceLoadState,
)

sealed interface SourceLoadState {
    data object Loading : SourceLoadState
    data object Success : SourceLoadState
    data class Error(val error: ClientError) : SourceLoadState
}
```

This allows the GUI to distinguish:

- complete failure
- partial failure with useful content still visible
- source-specific failure
- item-specific enrichment failure

## Local Cache and Database Design

The application should own a local database for normalized cache data instead of relying on each client to persist its own response format.

This recommendation should apply to both the GUI and the CLI as much as practical. The key CLI difference is the storage location: current-directory app data instead of a global application data location.

The database should store:

- normalized feed items
- feed source metadata
- configured sources
- seen-item state
- account sessions
- sync metadata such as last refresh timestamps or cursors

The cache should be keyed by stable shared identifiers such as `platformId:itemId`, not by raw response payloads.

### Recommended database approach

For Kotlin Multiplatform, `SQLDelight` is the strongest default choice because it provides:

- multiplatform support
- typed schema and queries
- good fit for shared business logic
- reuse across CLI and future GUI targets

For the CLI, the same database approach should be used where practical, but the database files should be located in the current working directory to support isolated test setups.

Recommended SQLDelight drivers:

- Android: `AndroidSqliteDriver`
- Desktop JVM: `JdbcSqliteDriver`
- CLI JVM: `JdbcSqliteDriver`
- iOS: `NativeSqliteDriver`

For web targets, durable local persistence can be deferred until later rather than forcing an early storage decision.

### Recommended storage boundaries

Use the database primarily for normalized application data:

- `feed_items`
- `feed_sources`
- `configured_sources`
- `seen_items`
- `account_sessions`
- `sync_state`

Raw API payload caching can be added later for debugging or performance if needed, but it should not be the primary app data model.

### Cache responsibilities

The cache layer should support:

- storing fetched feed items
- loading cached feed items when offline or before refresh completes
- tracking which items have been seen
- recording source-level sync progress
- persisting configured users and RSS feeds
- persisting social sign-in state

Repositories should decide when cached data is used, refreshed, or invalidated. Clients should not directly control cross-client cache policy.

### First cache policy

The first implementation should use a simple policy:

- read cached items first when available
- refresh each requested source on demand
- merge refreshed items back into the normalized cache
- keep seen-state independent from item refresh so re-fetching an item does not make it unseen again
- prune old cached items by age or item count later, once real usage patterns are known

For GUI sessions, the initial load of a new app session should trigger parallel refresh for all configured feeds after cached content is made available.

If cached items are available, the GUI should render them immediately and then update the screen in place as the parallel refresh completes. If no cached items are available, the GUI should show a loading indicator until the initial refresh produces data or errors.

## Technical Stack

The project should standardize on a small set of core libraries early so the CLI, shared logic, and GUI can evolve on the same foundation.

### Recommended core dependencies

- `kotlinx.coroutines`
  Use for suspend APIs, parallel refresh, `StateFlow`, and effect streams.
- `kotlinx.serialization`
  Use for network DTOs, persisted metadata, and any structured file content that is not stored in the database.
- `Ktor Client`
  Use as the shared HTTP client for RSS and social media integrations.
- `SQLDelight`
  Use as the shared persistence layer for cached feed data, configured sources, sessions, and seen-item state.

### CLI implementation

- use the JVM target
- call shared suspend APIs inside coroutine scopes
- keep the CLI persistence location rooted in the current working directory
- keep the CLI persistence schema and storage abstractions as close as possible to the GUI implementation

CLI command parsing should stay custom for now so dependencies remain minimal and command behavior stays easy to inspect during testing.

For GUI refresh gestures, use the Compose Material pull-to-refresh implementation that matches the existing Compose and Material stack.

### Networking and testing tools

- `Ktor MockEngine`
  Use for client and repository tests that need deterministic HTTP behavior.
- coroutine test utilities
  Use for testing suspend APIs, refresh behavior, and `StateFlow`-based logic.
- SQLDelight test database support
  Use for repository and persistence tests.

### Dependency wiring

Prefer manual dependency wiring first.

That means:

- construct clients, repositories, and storage explicitly
- avoid introducing a DI framework until the wiring becomes difficult to manage

This keeps the first implementation easier to debug, especially in the CLI.

### Feed parsing and data normalization

The implementation will still need a concrete RSS and Atom parsing approach.

This should include:

- XML parsing for feeds
- date parsing utilities for inconsistent feed timestamps
- mapping feed-specific fields into normalized `FeedItem` models

### Remaining technical decisions

Persisted sign-in secrets will not receive extra protection beyond normal persistence in the first version.

## GUI and ViewModel Communication Model

When GUI work begins, communication between Compose UI and ViewModels should follow a simple unidirectional data flow.

Recommended structure:

- UI sends actions to the ViewModel
- ViewModel exposes immutable screen state
- one-off effects are emitted separately from persistent state

### Recommended ViewModel shape

Use:

- `StateFlow<FeedScreenState>` for persistent UI state
- `fun onAction(action: FeedAction)` for user input
- `Flow<FeedEffect>` for one-time events such as navigation or external link opening

Illustrative shape:

```kotlin
data class FeedScreenState(
    val isLoading: Boolean,
    val items: List<FeedItem>,
    val sourceStatuses: List<FeedSourceStatus>,
    val includeSeen: Boolean,
)

sealed interface FeedAction {
    data object Refresh : FeedAction
    data class Load(val request: FeedRequest) : FeedAction
    data class ToggleIncludeSeen(val enabled: Boolean) : FeedAction
    data class MarkSeen(val itemId: String) : FeedAction
    data class MarkItemsSeen(val itemIds: List<String>) : FeedAction
    data object PullToRefresh : FeedAction
    data class OpenItem(val item: FeedItem) : FeedAction
}
```

### Communication responsibilities

The UI layer should:

- render state
- dispatch actions
- react to one-off effects

The ViewModel should:

- call repositories
- map repository results into screen state
- coordinate retries, refreshes, and seen-item updates
- observe app-session lifecycle and inactivity timeout
- avoid direct knowledge of client implementation details

Pull-to-refresh in the GUI should dispatch `PullToRefresh`, which should trigger the same repository refresh behavior as an explicit feed refresh while preserving the current configured source selection.

Seen-item detection in the GUI should use the lazy list visibility state. An item should trigger `MarkSeen(itemId)` only when:

- it has previously been visible in the current app session, and
- it is no longer present in the visible item set at all

This matches the rule that an item must first be shown and then become fully invisible before it is marked seen.

### Error state versus error effects

Errors should be split by whether they are persistent screen state or one-time notifications.

- persistent, renderable errors belong in `StateFlow` screen state
- transient error notifications belong in the effect stream

Examples of errors that belong in screen state:

- full-screen feed load failure
- partial source failure
- source-level authentication expiration

Examples of errors that belong in effects:

- mark-seen failure message
- retry failure snackbar
- open-link failure notification

This keeps the GUI thin and ensures the same repository layer can support both the CLI and future Compose screens.

`MarkItemsSeen` should be used for batch operations such as marking all currently unseen loaded items as seen.

## Implementation Order

### Phase 1: Replace template shared code

Replace the current template-level `Greeting` and `Platform` scaffolding in `shared` with real application packages and interfaces.

### Phase 2: Define shared read-only contracts

Add:

- shared domain models
- `SocialPlatformClient`
- `ClientRegistry`
- `ConfiguredSourceRepository`
- `FeedRequest`
- `FeedQuery`
- `FeedPage`
- `FeedCursor`
- `FeedLoadResult`
- `FeedSourceStatus`
- `ClientError`
- seen-state abstractions
- repository interfaces

### Phase 3: Create the CLI module

Add a JVM CLI application that depends on `shared` and can execute feed commands against client implementations.

This phase should also include management commands for sign-in, sign-out, configured sources, and clearing persisted data.

### Phase 4: Add fake clients

Implement in-memory or fake clients first so CLI behavior can be tested before any real API integration.

### Phase 5: Wire feed loading and unseen filtering

Use fake or real feed data to wire the feed repository so it can:

- merge feed items
- sort them in chronological order
- apply unseen filtering through the `SeenItemRepository` abstraction

At this stage, seen-state storage can still be in-memory or otherwise minimal.

### Phase 6: Build the first real client with RSS

RSS is the simplest way to validate the abstraction because it is read-only and has fewer auth constraints.

### Phase 7: Add Bluesky

Bluesky is a good next client because it supports richer social features and is typically easier to prototype against than Twitter or X.

### Phase 8: Add Twitter or X

Twitter or X should come later because authentication, API access, and feature constraints are usually more complex.

### Phase 9: Add persistence and caching

Add:

- account or session storage
- cached feeds
- configured source storage
- durable seen-item storage
- refresh policies
- offline support where useful

This is where the `SeenItemRepository` should move from a minimal implementation to a database-backed implementation.
This is also where full-reset support for deleting all persisted application data should be implemented.

### Phase 10: Expand into a GUI

Once the CLI and client abstraction are stable, build the GUI on top of the same repositories and domain models.

## Testing Strategy

Add tests at multiple levels:

- contract tests for every client implementation
- mapper tests for client DTO to shared domain conversion
- repository tests for chronological sorting and unseen filtering
- repository tests for partial failure aggregation
- seen-item repository tests
- CLI command tests

This is important because the shared interfaces will only be useful if every client behaves consistently enough for the CLI and later GUI to trust them.

## Design Principles

- Prefer common models for shared concepts only
- Do not leak raw API DTOs outside client implementations
- Start with a read-only CLI and let it shape the shared contracts
- Start with clients plus repositories, and add use cases only if later workflows justify them
- Keep both CLI and GUI code client-agnostic
- Treat RSS as a first-class read-only source, not as a fake social network
- Use the server only where backend participation is actually necessary
- Make seen or unseen filtering part of the core feed workflow
- Use stable IDs and timestamps so ordering is deterministic

## Next Concrete Step

Create the initial shared package structure and define the first version of:

- `SocialPlatformClient`
- `RssClient`
- `BlueskyClient`
- `TwitterClient`
- `ClientRegistry`
- `FeedRequest`
- `FeedQuery`
- `FeedItem`
- `FeedSource`
- `FeedPage`
- `FeedCursor`
- profile, session, and seen-state models
- repository interfaces for feed loading and seen tracking
- seen-item repository contracts
- a fake client for CLI development
- a JVM CLI module with one `list-new-items` command

## Implementation Checklist

Use this checklist to start implementation without reopening design questions:

1. Create shared domain models and enums.
   Include `PlatformId`, `ConfiguredSource`, `FeedSource`, `FeedRequest`, `FeedQuery`, `FeedItem`, `FeedCursor`, `FeedPage`, `SeenState`, `SessionState`, `FeedLoadResult`, `FeedSourceStatus`, and `ClientError`.

2. Create shared repository interfaces.
   Add `FeedRepository`, `SeenItemRepository`, `ConfiguredSourceRepository`, and `SessionRepository`.

3. Add SQLDelight schema and drivers.
   Set up the shared schema for feed items, configured sources, seen items, account sessions, and sync state, using current-directory database files for the CLI.

4. Implement persistence adapters.
   Add concrete SQLDelight-backed implementations for configured sources, sessions, seen items, and cache storage.

5. Create the CLI module and custom command parser.
   Implement `list-new-items`, `signin`, `signout`, `add-user`, `remove-user`, `add-feed`, `remove-feed`, `list-sources`, and `clear-data`.

6. Add fake clients.
   Implement fake `RssClient`, `BlueskyClient`, and `TwitterClient` behavior sufficient to test repository orchestration and CLI flows.

7. Implement `FeedRepository.loadFeedItems`.
   Build per-client `FeedQuery` instances from `FeedRequest`, refresh sources in parallel, merge items, sort chronologically, apply seen filtering, and return `FeedLoadResult`.

8. Implement full-reset behavior.
   Make `clear-data` delete all persisted app data in the current directory for the CLI.

9. Implement the first real client with RSS.
   Add RSS and Atom fetch/parsing, normalization into `FeedItem`, and repository integration.

10. Add tests for the first slice.
    Cover repository merge/filter logic, SQLDelight persistence, CLI commands, and fake-client partial failure behavior.

11. Add GUI scaffolding after the CLI path is stable.
    Build a `FeedScreenState`, `FeedAction`, and ViewModel wired to the same shared repository interfaces.
