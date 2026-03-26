# Non-Web Bidirectional Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add server-authoritative bidirectional sync for non-web clients so Android, iOS, and JVM desktop keep their local SQLDelight database in sync with the server while preserving offline local edits.

**Architecture:** Add dedicated server sync endpoints and sync metadata, then add a shared client sync layer that queues local mutations, pushes them with idempotency keys, pulls authoritative remote changes, and reconciles local SQLDelight state. Non-web clients will bootstrap on first hydration, account change, or invalid sync metadata, then use pure incremental pull with tombstones after that.

**Tech Stack:** Kotlin Multiplatform, Ktor, SQLDelight, Compose Multiplatform, kotlinx.serialization, coroutines, Gradle

---

### Task 1: Server Sync Schema Foundation

**Files:**
- Modify: `src/shared/src/commonMain/sqldelight/com/franklinharper/social/media/client/db/SocialMediaDatabase.sq`
- Create: `src/server/src/test/kotlin/com/franklinharper/social/media/client/SyncPersistenceTest.kt`

- [ ] Step 1: Write failing persistence tests for server-side sync metadata, change-log cursors, tombstones, and `mutation_id` replay receipts
- [ ] Step 2: Run `cd src && ./gradlew :server:test --tests "*SyncPersistenceTest" --no-daemon` and verify failure from missing tables/queries
- [ ] Step 3: Add server sync tables and queries to `SocialMediaDatabase.sq`
  Tables to add:
  - `sync_change_log`
  - `sync_mutation_receipts`
  - entity version/tombstone columns for synced server-owned state
  - distinct account sync metadata names that do not reuse the existing feed-cache `sync_state`
- [ ] Step 4: Re-run `cd src && ./gradlew :server:test --tests "*SyncPersistenceTest" --no-daemon` and verify pass
- [ ] Step 5: Commit the server sync schema foundation

### Task 2: Server Sync DTOs, Mappers, and Service

**Files:**
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/api/SyncApiModels.kt`
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/api/SyncApiMappers.kt`
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/sync/ServerSyncService.kt`
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/sync/ServerSyncRepository.kt`
- Modify: `src/server/src/main/kotlin/com/franklinharper/social/media/client/persistence/ServerRepositories.kt`
- Test: `src/server/src/test/kotlin/com/franklinharper/social/media/client/SyncPersistenceTest.kt`

- [ ] Step 1: Write failing server tests for bootstrap assembly, per-mutation idempotent push replay, last-write-wins by server acceptance order, incremental pull tombstones, and source-error payload inclusion
- [ ] Step 2: Run `cd src && ./gradlew :server:test --tests "*SyncPersistenceTest" --no-daemon` and verify failure in the new service expectations
- [ ] Step 3: Implement server sync domain DTOs, mappers, and transactional orchestration
  Service responsibilities:
  - build full bootstrap payloads
  - apply push batches transactionally
  - record `mutation_id` receipts
  - emit ordered incremental change-log entries
  - resolve conflicts by last accepted server write
  - include synced source-error state in bootstrap and incremental pull payloads
- [ ] Step 4: Re-run `cd src && ./gradlew :server:test --tests "*SyncPersistenceTest" --no-daemon` and verify pass
- [ ] Step 5: Commit the server sync service layer

### Task 3: Server Sync Routes

**Files:**
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/api/SyncRoutes.kt`
- Modify: `src/server/src/main/kotlin/com/franklinharper/social/media/client/Application.kt`
- Create: `src/server/src/test/kotlin/com/franklinharper/social/media/client/SyncApiTest.kt`
- Modify: `src/server/src/test/kotlin/com/franklinharper/social/media/client/ApplicationTest.kt` if route registration coverage needs updates

- [ ] Step 1: Write failing API tests for `GET /api/sync/bootstrap`, `GET /api/sync/pull`, and `POST /api/sync/push`, including source-error payload coverage
- [ ] Step 2: Run `cd src && ./gradlew :server:test --tests "*SyncApiTest" --no-daemon` and verify failure from missing routes
- [ ] Step 3: Register auth-protected sync routes
  Route requirements:
  - `GET /api/sync/bootstrap`
  - `GET /api/sync/pull?since=<cursor>`
  - `POST /api/sync/push`
  - `401` for missing/expired auth
  - bootstrap and pull responses include synced source-error state
  - per-mutation result payloads and next cursor values
- [ ] Step 4: Re-run `cd src && ./gradlew :server:test --tests "*SyncApiTest" --no-daemon` and verify pass
- [ ] Step 5: Commit the server sync API

### Task 4: Shared Non-Web Auth And Sync Session Foundation

**Files:**
- Modify: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteSessionRepository.kt`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/sync/AuthenticatedSessionRepository.kt`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/sync/ServerSyncSessionStore.kt`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/sync/SqlDelightServerSyncSessionStore.kt`
- Modify: `src/shared/src/commonMain/sqldelight/com/franklinharper/social/media/client/db/SocialMediaDatabase.sq`
- Create: `src/shared/src/commonTest/kotlin/com/franklinharper/social/media/client/sync/ServerSyncSessionStoreTest.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/WebAuthState.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/App.kt`
- Modify: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/WebAuthStateTest.kt`
- Modify: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/FeedScreenTest.kt`

- [ ] Step 1: Write failing shared and Compose tests for durable server-auth session storage plus non-web sign-in, restore, and session-expiry gating
- [ ] Step 2: Run `cd src && ./gradlew :shared:jvmTest --tests "*ServerSyncSessionStoreTest" --no-daemon` and `cd src && ./gradlew :composeApp:jvmTest --tests "*WebAuthStateTest" --tests "*FeedScreenTest" --no-daemon` and verify failure
- [ ] Step 3: Generalize the auth repository naming and add a durable non-web server session store
  Required outcomes:
  - non-web can persist the signed-in server account
  - app auth gating is no longer web-only in behavior
  - Android, iOS, and JVM desktop can enter the same sign-in, restore, and session-expired flow before sync starts
  - sync can derive account-scoped ownership from the signed-in server account
  - sign-out clears or invalidates queued account-scoped sync mutations before another account can sign in
- [ ] Step 4: Re-run `cd src && ./gradlew :shared:jvmTest --tests "*ServerSyncSessionStoreTest" --no-daemon` and `cd src && ./gradlew :composeApp:jvmTest --tests "*WebAuthStateTest" --tests "*FeedScreenTest" --no-daemon` and verify pass
- [ ] Step 5: Commit the non-web auth/session foundation

### Task 5: Shared Client Sync Tables And Stores

**Files:**
- Modify: `src/shared/src/commonMain/sqldelight/com/franklinharper/social/media/client/db/SocialMediaDatabase.sq`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/sync/SyncModels.kt`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/sync/SyncMutationStore.kt`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/sync/SyncStateRepository.kt`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/sync/SqlDelightSyncStores.kt`
- Create: `src/shared/src/commonTest/kotlin/com/franklinharper/social/media/client/sync/SyncMutationStoreTest.kt`
- Create: `src/shared/src/jvmTest/kotlin/com/franklinharper/social/media/client/sync/SqlDelightSyncStoresJvmTest.kt`
- Modify: `src/shared/src/jvmTest/kotlin/com/franklinharper/social/media/client/repository/SqlDelightRepositoriesJvmTest.kt`

- [ ] Step 1: Write failing shared tests for pending mutation persistence, durable `sync_failures`, account scoping, and bootstrap reset behavior
- [ ] Step 2: Run `cd src && ./gradlew :shared:jvmTest --tests "*SyncMutationStoreTest" --tests "*SqlDelightSyncStoresJvmTest" --tests "*SqlDelightRepositoriesJvmTest" --no-daemon` and verify failure
- [ ] Step 3: Implement the client-side sync tables and SQLDelight-backed stores
  Store requirements:
  - queue pending mutations by account
  - persist last successful pull cursor by account
  - persist durable sync failures by account
  - support clearing old account-scoped synced data on account switch
- [ ] Step 4: Re-run `cd src && ./gradlew :shared:jvmTest --tests "*SyncMutationStoreTest" --tests "*SqlDelightSyncStoresJvmTest" --tests "*SqlDelightRepositoriesJvmTest" --no-daemon` and verify pass
- [ ] Step 5: Commit the shared sync stores

### Task 6: Remote Sync API Client

**Files:**
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/sync/RemoteSyncApi.kt`
- Modify: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebApiHttp.kt`
- Create: `src/shared/src/commonTest/kotlin/com/franklinharper/social/media/client/sync/RemoteSyncApiTest.kt`

- [ ] Step 1: Write failing shared tests for bootstrap, incremental pull, and push request/response mapping
- [ ] Step 2: Run `cd src && ./gradlew :shared:jvmTest --tests "*RemoteSyncApiTest" --no-daemon` and verify failure
- [ ] Step 3: Implement the shared sync HTTP client
  Mapping requirements:
  - bootstrap payload parsing
  - incremental pull cursor handling
  - push batch encoding with `mutation_id`
  - auth failures surfaced distinctly from transient failures
- [ ] Step 4: Re-run `cd src && ./gradlew :shared:jvmTest --tests "*RemoteSyncApiTest" --no-daemon` and verify pass
- [ ] Step 5: Commit the remote sync API client

### Task 7: Sync Coordinator And Source Sync

**Files:**
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/sync/SyncCoordinator.kt`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/repository/SyncingConfiguredSourceRepository.kt`
- Modify: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/repository/SqlDelightRepositories.kt`
- Create: `src/shared/src/commonTest/kotlin/com/franklinharper/social/media/client/sync/SyncCoordinatorTest.kt`
- Create: `src/shared/src/commonTest/kotlin/com/franklinharper/social/media/client/sync/SyncReconciliationTest.kt`

- [ ] Step 1: Write failing shared tests for serialized sync passes, trigger coalescing, bootstrap hydration, and configured-source reconciliation
- [ ] Step 2: Run `cd src && ./gradlew :shared:jvmTest --tests "*SyncCoordinatorTest" --tests "*SyncReconciliationTest" --no-daemon` and verify failure
- [ ] Step 3: Implement the coordinator and syncing configured-source repository decorator
  Required behavior:
  - single in-flight sync pass per account
  - push before pull
  - one queued follow-up pass while active
  - sign-in bootstrap when DB is empty, account changes, or metadata is invalid
  - pure incremental pull after bootstrap
- [ ] Step 4: Re-run `cd src && ./gradlew :shared:jvmTest --tests "*SyncCoordinatorTest" --tests "*SyncReconciliationTest" --no-daemon` and verify pass
- [ ] Step 5: Commit the coordinator and source sync

### Task 8: Seen-State And Source-Error Sync

**Files:**
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/repository/SyncingSeenItemRepository.kt`
- Modify: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/repository/SqlDelightRepositories.kt`
- Modify: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/repository/Repositories.kt`
- Modify: `src/shared/src/commonMain/sqldelight/com/franklinharper/social/media/client/db/SocialMediaDatabase.sq`
- Modify: `src/shared/src/commonTest/kotlin/com/franklinharper/social/media/client/sync/SyncReconciliationTest.kt`
- Modify: `src/shared/src/jvmTest/kotlin/com/franklinharper/social/media/client/repository/SqlDelightRepositoriesJvmTest.kt`

- [ ] Step 1: Write failing tests for canonical seen-item ID sync, unseen tombstone application, and server-owned source-error replacement
- [ ] Step 2: Run `cd src && ./gradlew :shared:jvmTest --tests "*SyncReconciliationTest" --tests "*SqlDelightRepositoriesJvmTest" --no-daemon` and verify failure
- [ ] Step 3: Implement seen-state and source-error reconciliation
  Requirements:
  - use the server public feed item ID as the seen key
  - add an explicit unseen/remove-seen repository contract so tombstone pulls can be applied locally
  - preserve unseen reversals with tombstones
  - keep device-local transient source errors out of synced source-error state
  - replace synced source-error state with pulled server truth
- [ ] Step 4: Re-run `cd src && ./gradlew :shared:jvmTest --tests "*SyncReconciliationTest" --tests "*SqlDelightRepositoriesJvmTest" --no-daemon` and verify pass
- [ ] Step 5: Commit seen-state and source-error sync

### Task 9: Non-Web Container And App Trigger Integration

**Files:**
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppDependencies.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppContainer.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/ComposeAppContainerFactory.kt`
- Modify: `src/composeApp/src/androidMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.kt`
- Modify: `src/composeApp/src/jvmMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/App.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AddSourceState.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/FeedShellState.kt`
- Modify: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/AddSourceStateTest.kt`
- Modify: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/FeedShellStateTest.kt`
- Modify: `src/composeApp/src/jvmTest/kotlin/com/franklinharper/social/media/client/app/JvmAppContainerFactoryTest.kt`
- Modify: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/AppContainerContractTest.kt`

- [ ] Step 1: Write failing Compose tests for app-start sync, post-mutation trigger dispatch, and pre/post-refresh sync integration
- [ ] Step 2: Run `cd src && ./gradlew :composeApp:jvmTest --tests "*AddSourceStateTest" --tests "*FeedShellStateTest" --tests "*JvmAppContainerFactoryTest" --tests "*AppContainerContractTest" --no-daemon` and verify failure
- [ ] Step 3: Wire the non-web app containers to sync-aware repositories and coordinator triggers
  Integration requirements:
  - app start with signed-in server account triggers bootstrap or incremental sync
  - local add-source mutations queue then trigger sync
  - feed refresh coalesces with sync instead of racing it
  - account switch clears prior account-scoped synced state before bootstrap
- [ ] Step 4: Re-run `cd src && ./gradlew :composeApp:jvmTest --tests "*AddSourceStateTest" --tests "*FeedShellStateTest" --tests "*JvmAppContainerFactoryTest" --tests "*AppContainerContractTest" --no-daemon` and verify pass
- [ ] Step 5: Commit non-web app integration

### Task 10: Final Verification And Docs

**Files:**
- Modify: `src/README.md` if local non-web sync setup or auth instructions changed
- Modify: `docs/superpowers/specs/2026-03-26-non-web-bidirectional-sync-design.md` only if implementation decisions need to be recorded

- [ ] Step 1: Run `cd src && ./gradlew :server:test --tests "*SyncApiTest" --tests "*SyncPersistenceTest" --no-daemon`
- [ ] Step 2: Run `cd src && ./gradlew :shared:jvmTest --tests "*ServerSyncSessionStoreTest" --tests "*SyncMutationStoreTest" --tests "*SqlDelightSyncStoresJvmTest" --tests "*RemoteSyncApiTest" --tests "*SyncCoordinatorTest" --tests "*SyncReconciliationTest" --tests "*SqlDelightRepositoriesJvmTest" --no-daemon`
- [ ] Step 3: Run `cd src && ./gradlew :composeApp:jvmTest --tests "*WebAuthStateTest" --tests "*AddSourceStateTest" --tests "*FeedShellStateTest" --tests "*JvmAppContainerFactoryTest" --tests "*AppContainerContractTest" --no-daemon`
- [ ] Step 4: Run `cd src && ./gradlew :composeApp:compileAndroidMain :androidApp:compileDebugKotlin :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs --no-daemon`
- [ ] Step 5: Commit final verification and docs updates
