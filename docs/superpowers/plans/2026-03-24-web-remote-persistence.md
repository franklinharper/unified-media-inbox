# Web Remote Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace browser-local web persistence with a login-gated, server-backed web client while keeping desktop, Android, and iOS on local persistence.

**Architecture:** Keep `shared` as the application boundary for domain models and repository contracts. Build a Ktor server that owns authentication, per-user source/feed/seen data, and server-side RSS/Bluesky refresh; then add authenticated remote repository adapters for the web `composeApp` bootstrap while preserving local app containers on non-web targets.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Ktor server/client, SQLDelight, Kotlin coroutines, Kotlin test, Ktor test host, and existing `shared` repository/domain code.

---

## File Structure

### Server auth and persistence

- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/auth/AuthModels.kt`
  Responsibility: server-side auth request/response/session DTOs and small internal auth models.
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/auth/PasswordHasher.kt`
  Responsibility: password hashing and verification helpers.
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/auth/ServerSessionService.kt`
  Responsibility: create, restore, and revoke authenticated web sessions.
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/persistence/ServerDatabaseFactory.kt`
  Responsibility: server database bootstrap.
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/persistence/ServerRepositories.kt`
  Responsibility: per-user server repository implementations over persistence.
- Modify: `src/shared/src/commonMain/sqldelight/com/franklinharper/social/media/client/db/SocialMediaDatabase.sq`
  Responsibility: add server-owned user/account/session tables and `user_id` ownership columns where needed for server data isolation.
- Modify: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/repository/SqlDelightRepositories.kt`
  Responsibility: factor or extend SQLDelight-backed repositories so server code can operate per user without changing UI consumers.

### Server HTTP API

- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiModels.kt`
  Responsibility: HTTP DTOs for auth, sources, feed items, and refresh results.
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiRoutes.kt`
  Responsibility: route registration for auth, session, source, feed, and seen-state endpoints.
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiMappers.kt`
  Responsibility: mapping between shared domain models and HTTP DTOs.
- Modify: `src/server/src/main/kotlin/com/franklinharper/social/media/client/Application.kt`
  Responsibility: install Ktor plugins, wire repositories/services/clients, and register authenticated routes.

### Shared remote web adapters

- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebApiHttp.kt`
  Responsibility: low-level authenticated HTTP transport for web remote repositories.
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteConfiguredSourceRepository.kt`
  Responsibility: HTTP-backed `ConfiguredSourceRepository`.
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteFeedRepository.kt`
  Responsibility: HTTP-backed `FeedRepository`.
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteSeenItemRepository.kt`
  Responsibility: HTTP-backed `SeenItemRepository`.
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteSessionRepository.kt`
  Responsibility: HTTP-backed web-auth session repository.
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteFeedCacheRepository.kt`
  Responsibility: no-op or server-projected cache adapter needed to satisfy the app container contract without local browser persistence.
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteSourceErrorRepository.kt`
  Responsibility: remote source-error queries if still required by the app container contract.

### Compose web auth and bootstrap

- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/WebAuthState.kt`
  Responsibility: outer app state for unauthenticated/authenticating/authenticated/session-expired flow.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/LoginScreen.kt`
  Responsibility: email/password login screen and error/loading rendering.
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/App.kt`
  Responsibility: gate web behind login while preserving current feed/add-source/detail flow for authenticated web and local-first native targets.
- Modify: `src/composeApp/src/jsMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.kt`
  Responsibility: switch JS web bootstrap from local persistence to authenticated remote repositories.
- Modify: `src/composeApp/src/wasmJsMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.kt`
  Responsibility: switch Wasm web bootstrap from local persistence to authenticated remote repositories.
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppDependencies.kt`
  Responsibility: expose any additional auth-facing dependency needed by the top-level app root.
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppContainer.kt`
  Responsibility: accommodate authenticated remote bootstrapping without disturbing current feed/add-source contracts.

### Build configuration

- Modify: `src/server/build.gradle.kts`
  Responsibility: add Ktor auth/serialization, SQLDelight JDBC driver, and any password-hash dependency used by the first auth cut.
- Modify: `src/shared/build.gradle.kts`
  Responsibility: add Ktor client serialization dependencies needed by remote repository adapters.
- Modify: `src/composeApp/build.gradle.kts`
  Responsibility: remove web-local SQLDelight worker dependencies once the web target no longer persists locally.

### Tests

- Create: `src/server/src/test/kotlin/com/franklinharper/social/media/client/AuthApiTest.kt`
- Create: `src/server/src/test/kotlin/com/franklinharper/social/media/client/FeedApiTest.kt`
- Create: `src/server/src/test/kotlin/com/franklinharper/social/media/client/SourceApiTest.kt`
- Create: `src/shared/src/commonTest/kotlin/com/franklinharper/social/media/client/remote/WebRemoteRepositoriesTest.kt`
- Create: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/WebAuthStateTest.kt`
- Create: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/LoginScreenTest.kt`
- Modify: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/FeedScreenTest.kt`
  Responsibility: keep authenticated feed behavior covered after the web login gate is added.

## Task 1: Add Server-Side User and Session Persistence

**Files:**
- Modify: `src/shared/src/commonMain/sqldelight/com/franklinharper/social/media/client/db/SocialMediaDatabase.sq`
- Modify: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/repository/SqlDelightRepositories.kt`
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/persistence/ServerDatabaseFactory.kt`
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/auth/AuthModels.kt`
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/auth/PasswordHasher.kt`
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/auth/ServerSessionService.kt`
- Test: `src/server/src/test/kotlin/com/franklinharper/social/media/client/AuthPersistenceTest.kt`

- [ ] **Step 1: Write the failing persistence tests**

```kotlin
@Test
fun `server can create user and restore password-authenticated session`() = testApplication {
    val repositories = testServerRepositories()

    val userId = repositories.users.createUser("alice@example.com", "hash")
    val session = repositories.sessions.createSession(userId)

    assertEquals(userId, repositories.sessions.requireUser(session.token))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "*AuthPersistenceTest" --no-daemon`
Expected: FAIL because the user/session schema and services do not exist.

- [ ] **Step 3: Implement minimal user/session schema and services**

```kotlin
data class AuthenticatedUser(val userId: String, val email: String)
data class ServerSession(val token: String, val userId: String, val expiresAtEpochMillis: Long)
```

Add:
- user table
- password credential storage
- server session table
- per-row `user_id` ownership for server-managed source/feed/seen data

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "*AuthPersistenceTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/shared/src/commonMain/sqldelight/com/franklinharper/social/media/client/db/SocialMediaDatabase.sq \
  src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/repository/SqlDelightRepositories.kt \
  src/server/src/main/kotlin/com/franklinharper/social/media/client/persistence/ServerDatabaseFactory.kt \
  src/server/src/main/kotlin/com/franklinharper/social/media/client/auth/AuthModels.kt \
  src/server/src/main/kotlin/com/franklinharper/social/media/client/auth/PasswordHasher.kt \
  src/server/src/main/kotlin/com/franklinharper/social/media/client/auth/ServerSessionService.kt \
  src/server/src/test/kotlin/com/franklinharper/social/media/client/AuthPersistenceTest.kt
git commit -m "Add server auth persistence"
```

## Task 2: Add Login, Session, and Logout HTTP Endpoints

**Files:**
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiModels.kt`
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiRoutes.kt`
- Modify: `src/server/src/main/kotlin/com/franklinharper/social/media/client/Application.kt`
- Modify: `src/server/build.gradle.kts`
- Test: `src/server/src/test/kotlin/com/franklinharper/social/media/client/AuthApiTest.kt`

- [ ] **Step 1: Write the failing auth API tests**

```kotlin
@Test
fun `sign in returns session for valid email and password`() = testApplication {
    val response = client.post("/api/auth/sign-in") {
        setBody(SignInRequest(email = "alice@example.com", password = "secret"))
    }

    assertEquals(HttpStatusCode.OK, response.status)
}

@Test
fun `restore session returns unauthorized for invalid token`() = testApplication {
    val response = client.get("/api/auth/session") {
        header(HttpHeaders.Authorization, "Bearer bad-token")
    }

    assertEquals(HttpStatusCode.Unauthorized, response.status)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "*AuthApiTest" --no-daemon`
Expected: FAIL because the auth routes and DTOs do not exist.

- [ ] **Step 3: Implement minimal auth routes**

```kotlin
post("/api/auth/sign-in") { ... }
get("/api/auth/session") { ... }
post("/api/auth/sign-out") { ... }
```

Assumption for this plan:
- first owned auth flow is `email/password`
- auth session is bearer-token based for web

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "*AuthApiTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiModels.kt \
  src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiRoutes.kt \
  src/server/src/main/kotlin/com/franklinharper/social/media/client/Application.kt \
  src/server/build.gradle.kts \
  src/server/src/test/kotlin/com/franklinharper/social/media/client/AuthApiTest.kt
git commit -m "Add server auth API"
```

## Task 3: Add Per-User Source and Feed Server APIs With Server-Owned Refresh

**Files:**
- Modify: `src/server/src/main/kotlin/com/franklinharper/social/media/client/Application.kt`
- Modify: `src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiModels.kt`
- Modify: `src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiRoutes.kt`
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiMappers.kt`
- Create: `src/server/src/main/kotlin/com/franklinharper/social/media/client/persistence/ServerRepositories.kt`
- Test: `src/server/src/test/kotlin/com/franklinharper/social/media/client/SourceApiTest.kt`
- Test: `src/server/src/test/kotlin/com/franklinharper/social/media/client/FeedApiTest.kt`

- [ ] **Step 1: Write the failing source/feed API tests**

```kotlin
@Test
fun `add source persists source for authenticated user`() = testApplication {
    val token = createSignedInUser()

    val response = authedClient(token).post("/api/sources") {
        setBody(AddSourceRequest.rss("https://example.com/feed.xml"))
    }

    assertEquals(HttpStatusCode.Created, response.status)
}

@Test
fun `refresh performs server-side fetch and returns feed items`() = testApplication {
    val token = createSignedInUser()

    val response = authedClient(token).post("/api/feed/refresh")

    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.body<FeedResponse>().items.isNotEmpty())
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :server:test --tests "*SourceApiTest" --tests "*FeedApiTest" --no-daemon`
Expected: FAIL because authenticated source/feed routes do not exist.

- [ ] **Step 3: Implement minimal authenticated server APIs**

Required routes:
- `GET /api/sources`
- `POST /api/sources`
- `DELETE /api/sources`
- `GET /api/feed`
- `POST /api/feed/refresh`
- `POST /api/feed/seen`

Required behavior:
- enforce authenticated user context
- read/write only that user’s data
- perform RSS/Bluesky fetches on the server during refresh

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :server:test --tests "*SourceApiTest" --tests "*FeedApiTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/server/src/main/kotlin/com/franklinharper/social/media/client/Application.kt \
  src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiModels.kt \
  src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiRoutes.kt \
  src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiMappers.kt \
  src/server/src/main/kotlin/com/franklinharper/social/media/client/persistence/ServerRepositories.kt \
  src/server/src/test/kotlin/com/franklinharper/social/media/client/SourceApiTest.kt \
  src/server/src/test/kotlin/com/franklinharper/social/media/client/FeedApiTest.kt
git commit -m "Add server feed and source API"
```

## Task 4: Add Shared Remote Repository Adapters For the Web Client

**Files:**
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebApiHttp.kt`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteConfiguredSourceRepository.kt`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteFeedRepository.kt`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteSeenItemRepository.kt`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteSessionRepository.kt`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteFeedCacheRepository.kt`
- Create: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteSourceErrorRepository.kt`
- Modify: `src/shared/build.gradle.kts`
- Test: `src/shared/src/commonTest/kotlin/com/franklinharper/social/media/client/remote/WebRemoteRepositoriesTest.kt`

- [ ] **Step 1: Write the failing remote repository tests**

```kotlin
@Test
fun `remote configured source repository posts add-source request`() = runTest {
    val http = FakeWebApiHttp()
    val repository = WebRemoteConfiguredSourceRepository(http)

    repository.addSource(ConfiguredSource.RssFeed("https://example.com/feed.xml"))

    assertEquals("/api/sources", http.lastPostPath)
}

@Test
fun `remote feed repository translates feed response into shared result`() = runTest {
    val repository = WebRemoteFeedRepository(FakeWebApiHttp(feedResponse = sampleFeedResponse()))

    val result = repository.loadFeedItems(FeedRequest(sources = emptyList(), includeSeen = false))

    assertEquals("item-1", result.items.single().itemId)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:jvmTest --tests "*WebRemoteRepositoriesTest" --no-daemon`
Expected: FAIL because the remote repository adapters do not exist.

- [ ] **Step 3: Implement minimal HTTP-backed repository adapters**

Keep these rules:
- reuse shared domain models at the repository boundary
- keep DTO transport shapes isolated from core domain types
- return unauthorized/session failures in a form the web auth state can react to

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:jvmTest --tests "*WebRemoteRepositoriesTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebApiHttp.kt \
  src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteConfiguredSourceRepository.kt \
  src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteFeedRepository.kt \
  src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteSeenItemRepository.kt \
  src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteSessionRepository.kt \
  src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteFeedCacheRepository.kt \
  src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteSourceErrorRepository.kt \
  src/shared/build.gradle.kts \
  src/shared/src/commonTest/kotlin/com/franklinharper/social/media/client/remote/WebRemoteRepositoriesTest.kt
git commit -m "Add web remote repositories"
```

## Task 5: Add Web Authentication State and Login UI

**Files:**
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/WebAuthState.kt`
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/LoginScreen.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/App.kt`
- Test: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/WebAuthStateTest.kt`
- Test: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/LoginScreenTest.kt`

- [ ] **Step 1: Write the failing web-auth tests**

```kotlin
@Test
fun `successful session restore enters authenticated state`() = runTest {
    val state = WebAuthState(sessionRepository = FakeAuthedSessionRepository())

    state.start()

    assertEquals(WebAuthUiState.Authenticated, state.uiState.value.status)
}

@Test
fun `login screen submits email and password`() {
    runComposeUiTest {
        setContent {
            LoginScreen(
                state = LoginUiState(),
                onSignIn = { email, password -> recorded = email to password },
            )
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:jvmTest --tests "*WebAuthStateTest" --tests "*LoginScreenTest" --no-daemon`
Expected: FAIL because the auth state and login screen do not exist.

- [ ] **Step 3: Implement minimal auth gate**

Required behavior:
- restore existing session on start
- expose unauthenticated/authenticating/authenticated/session-expired/error states
- submit email/password sign-in
- clear state on unauthorized server response

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:jvmTest --tests "*WebAuthStateTest" --tests "*LoginScreenTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/WebAuthState.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/LoginScreen.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/App.kt \
  src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/WebAuthStateTest.kt \
  src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/LoginScreenTest.kt
git commit -m "Add web login flow"
```

## Task 6: Switch Web Bootstrap to Remote Repositories and Keep Native Local

**Files:**
- Modify: `src/composeApp/src/jsMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.kt`
- Modify: `src/composeApp/src/wasmJsMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppDependencies.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppContainer.kt`
- Modify: `src/composeApp/build.gradle.kts`
- Test: `src/composeApp/src/jvmTest/kotlin/com/franklinharper/social/media/client/app/JvmAppContainerFactoryTest.kt`
- Test: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/AppContainerContractTest.kt`

- [ ] **Step 1: Write the failing bootstrap regression tests**

```kotlin
@Test
fun `jvm app container remains local persistence backed`() {
    val container = createJvmAppContainerForTest()

    assertTrue(container.feedRepository !is WebRemoteFeedRepository)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:jvmTest --tests "*JvmAppContainerFactoryTest" --tests "*AppContainerContractTest" --no-daemon`
Expected: FAIL once the new web-facing contract changes are introduced.

- [ ] **Step 3: Implement web-only remote bootstrap**

Required behavior:
- JS and Wasm create remote repository-backed containers
- JVM/Android/iOS remain local persistence-backed
- remove now-unused web SQLDelight worker wiring from `composeApp` build if no longer needed

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:jvmTest --tests "*JvmAppContainerFactoryTest" --tests "*AppContainerContractTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composeApp/src/jsMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.kt \
  src/composeApp/src/wasmJsMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppDependencies.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppContainer.kt \
  src/composeApp/build.gradle.kts \
  src/composeApp/src/jvmTest/kotlin/com/franklinharper/social/media/client/app/JvmAppContainerFactoryTest.kt \
  src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/AppContainerContractTest.kt
git commit -m "Switch web app container to remote persistence"
```

## Task 7: Integrate Authenticated Web Flow With the Existing Feed Shell

**Files:**
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/App.kt`
- Modify: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/FeedScreenTest.kt`
- Modify: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/AddSourceScreenTest.kt`

- [ ] **Step 1: Write the failing app-root flow tests**

```kotlin
@Test
fun `web root shows login before authenticated feed`() { ... }

@Test
fun `unauthorized result returns user to login screen`() { ... }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:jvmTest --tests "*FeedScreenTest" --tests "*AddSourceScreenTest" --tests "*WebAuthStateTest" --no-daemon`
Expected: FAIL until the top-level app routing handles auth state correctly.

- [ ] **Step 3: Implement minimal authenticated root integration**

Required behavior:
- web starts at login
- authenticated web enters the existing feed/add-source/detail flow
- unauthorized failures bounce back to login with a clear message
- native targets remain feed-first and bypass web login

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:jvmTest --tests "*FeedScreenTest" --tests "*AddSourceScreenTest" --tests "*WebAuthStateTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/App.kt \
  src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/FeedScreenTest.kt \
  src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/AddSourceScreenTest.kt
git commit -m "Integrate web auth with feed shell"
```

## Task 8: End-to-End Verification and Cleanup

**Files:**
- Modify only what verification exposes.

- [ ] **Step 1: Run server tests**

Run: `./gradlew :server:test --no-daemon`
Expected: PASS.

- [ ] **Step 2: Run shared tests covering remote adapters and core regressions**

Run: `./gradlew :shared:jvmTest --no-daemon`
Expected: PASS.

- [ ] **Step 3: Run compose JVM tests**

Run: `./gradlew :composeApp:jvmTest --no-daemon`
Expected: PASS.

- [ ] **Step 4: Run web compiles**

Run: `./gradlew :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs --no-daemon`
Expected: PASS.

- [ ] **Step 5: Run server and a web dev target for smoke verification**

Run: `./gradlew :server:run --no-daemon`
Run: `./gradlew :composeApp:wasmJsBrowserDevelopmentRun --no-daemon`
Expected: server starts and the web app reaches the login screen.

- [ ] **Step 6: Commit any final verification fixes**

```bash
git add src/server src/shared src/composeApp
git commit -m "Polish web remote persistence integration"
```

## Review Notes

- This plan assumes the first owned web auth flow is `email/password`.
- The plan intentionally keeps desktop, Android, and iOS local-first.
- iOS verification remains deferred during routine development unless an implementation task is specifically about iOS.
