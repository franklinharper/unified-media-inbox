# GUI Feed Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first real cross-platform Compose GUI shell for Android, iOS, desktop, and web: a feed-first app with responsive source filtering, automatic initial load, manual refresh, and fully functional RSS and Bluesky add-source flows backed by shared repositories.

**Development note:** iOS remains in product scope, but routine development should not block on iOS compile or run verification. Only run iOS-specific verification when the task is explicitly about iOS bootstrap or iOS debugging.

**Architecture:** Keep app-level behavior in `shared` and put GUI orchestration in focused `composeApp` state holders and screens. Reuse the existing repository/contracts stack from `shared`, add platform-specific bootstrap for persistence-backed repositories, and keep the UI shell thin: it renders state, triggers repository actions, and delegates client/repository policy to shared code.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3, shared repository/contracts in `src/shared`, SQLDelight persistence, Ktor-backed clients, Kotlin coroutines, kotlinx-coroutines-test, Kotlin test, and Compose UI tests.

---

## File Structure

### Shared UI/Application bootstrap files

- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppDependencies.kt`
  Responsibility: small interfaces/data holders for the Compose app’s repository/client dependencies and test doubles.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppContainer.kt`
  Responsibility: shared app container contract plus ownership of the repository stack and client registry used by the UI layer.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/FeedShellState.kt`
  Responsibility: state model and actions for feed screen, source filter selection, refresh, and navigation to add-source flow.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AddSourceState.kt`
  Responsibility: add-source flow state, source-type picker, RSS form, Bluesky form, deferred Twitter state.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/ResponsiveLayout.kt`
  Responsibility: layout breakpoint helpers for dropdown vs side panel behavior.

### Compose UI files

- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/App.kt`
  Responsibility: replace template stub with app shell root.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/FeedScreen.kt`
  Responsibility: main feed shell rendering, header, dropdown filter on narrow screens, left source panel on wide screens, refresh trigger, floating add button.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/FeedItemList.kt`
  Responsibility: list rendering and empty-result states.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/SourcePanel.kt`
  Responsibility: wide-screen source filter panel.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/SourceFilterDropdown.kt`
  Responsibility: narrow-screen source filtering control.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/EmptyFeedState.kt`
  Responsibility: zero-sources CTA state.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/AddSourceScreen.kt`
  Responsibility: dedicated add-source flow root.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/AddSourceTypePicker.kt`
  Responsibility: RSS / Bluesky / Twitter options.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/AddRssSourceForm.kt`
  Responsibility: RSS entry form and validation surface.
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/AddBlueskySourceForm.kt`
  Responsibility: Bluesky entry form and validation surface.

### Platform bootstrap files

- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.kt`
  Responsibility: `expect`/platform abstraction for constructing the app container.
- Create: `src/composeApp/src/androidMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.android.kt`
  Responsibility: Android repository/client/bootstrap wiring.
- Create: `src/composeApp/src/iosMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.ios.kt`
  Responsibility: iOS repository/client/bootstrap wiring.
- Create: `src/composeApp/src/jvmMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.jvm.kt`
  Responsibility: desktop repository/client/bootstrap wiring using the same persistence-backed shared stack as CLI.
- Create: `src/composeApp/src/webMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.web.kt`
  Responsibility: web repository/client/bootstrap wiring with platform-appropriate persistence or a clearly documented fallback if persistent storage support requires an incremental slice.
- Create: `src/composeApp/src/wasmJsMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.wasmJs.kt`
  Responsibility: wasm web repository/client/bootstrap wiring aligned with the JS web target.

### Shared platform database support

- Modify: `src/shared/build.gradle.kts`
  Responsibility: add JS/Wasm persistence dependencies and source-set wiring needed by shared web database/bootstrap code.
- Create or modify: `src/shared/src/androidMain/kotlin/com/franklinharper/social/media/client/db/...`
  Responsibility: Android SQLDelight database factory if missing.
- Create or modify: `src/shared/src/iosMain/kotlin/com/franklinharper/social/media/client/db/...`
  Responsibility: iOS SQLDelight database factory if missing.
- Create or modify: `src/shared/src/jsMain/kotlin/com/franklinharper/social/media/client/db/...`
  Responsibility: JS web persistence/database bridge.
- Create or modify: `src/shared/src/wasmJsMain/kotlin/com/franklinharper/social/media/client/db/...`
  Responsibility: Wasm web persistence/database bridge.

### Build/Test configuration

- Modify: `src/composeApp/build.gradle.kts`
  Responsibility: add coroutine-test and Compose UI test dependencies/source-set wiring required by the plan.
- Modify: `src/gradle/libs.versions.toml`
  Responsibility: add version-catalog entries for coroutine-test and any Compose UI test libraries referenced by the new test suite.

### Tests

- Create: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/FeedShellStateTest.kt`
- Create: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/AddSourceStateTest.kt`
- Create: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/FeedScreenTest.kt`
- Create: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/AddSourceScreenTest.kt`
- Modify or create: `src/shared/src/commonTest/...` only if shared repository APIs need small supporting changes.

### Docs

- Modify: `docs/superpowers/specs/2026-03-23-gui-feed-shell-design.md` only if implementation uncovers a real spec correction.
- Optionally create: `docs/adr/...` only if platform bootstrap decisions change architecture materially.

## Task 1: Establish the GUI App Container Boundary

**Files:**
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppDependencies.kt`
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppContainer.kt`
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.kt`
- Modify: `src/composeApp/build.gradle.kts`
- Modify: `src/gradle/libs.versions.toml`
- Test: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/AppContainerContractTest.kt`

- [ ] **Step 1: Write the failing contract test for the app container**

```kotlin
@Test
fun `app container exposes repositories needed by feed and add-source flows`() {
    val container = FakeAppContainer()

    assertNotNull(container.feedRepository)
    assertNotNull(container.configuredSourceRepository)
    assertNotNull(container.sessionRepository)
    assertNotNull(container.seenItemRepository)
    assertNotNull(container.feedCacheRepository)
    assertNotNull(container.sourceErrorRepository)
    assertNotNull(container.clientRegistry)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:allTests --tests "*AppContainerContractTest" --no-daemon`
Expected: FAIL because the container types do not exist yet.

- [ ] **Step 3: Write minimal app container interfaces and test dependency wiring**

```kotlin
interface AppContainer {
    val clientRegistry: ClientRegistry
    val configuredSourceRepository: ConfiguredSourceRepository
    val sessionRepository: SessionRepository
    val seenItemRepository: SeenItemRepository
    val feedCacheRepository: FeedCacheRepository
    val sourceErrorRepository: SourceErrorRepository
    val feedRepository: FeedRepository
}

expect fun createAppContainer(): AppContainer
```

Also add the test dependencies the rest of this plan assumes:

- `kotlinx-coroutines-test`
- Compose UI test dependency appropriate for this repo’s Compose Multiplatform setup

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:allTests --tests "*AppContainerContractTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppDependencies.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppContainer.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.kt \
  src/composeApp/build.gradle.kts \
  src/gradle/libs.versions.toml \
  src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/AppContainerContractTest.kt
git commit -m "Add compose app container contract"
```

## Task 2: Add Feed Shell State With Automatic Load and Filtering

**Files:**
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/FeedShellState.kt`
- Test: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/FeedShellStateTest.kt`

- [ ] **Step 1: Write the failing state tests**

```kotlin
@Test
fun `initial load fetches items automatically`() = runTest {
    val state = FeedShellState(...)

    state.start()

    assertEquals(1, fakeFeedRepository.requests.size)
}

@Test
fun `selecting a source filters visible items`() = runTest {
    val state = FeedShellState(...)

    state.start()
    state.selectSource("rss-1")

    assertEquals(listOf("item-1"), state.visibleItems.map { it.itemId })
}

@Test
fun `feed shell keeps configured sources and selected source empty state distinct`() = runTest {
    val state = FeedShellState(...)

    state.start()
    state.selectSource("rss-1")

    assertEquals(listOf("rss-1"), state.sources.map { it.sourceId })
    assertEquals(VisibleFeedEmptyState.NoItemsForSelectedSource("rss-1"), state.emptyState)
}

@Test
fun `feed shell exposes loading and error state`() = runTest {
    fakeFeedRepository.error = ClientError.NetworkError("offline")
    val state = FeedShellState(...)

    state.start()

    assertFalse(state.isLoading)
    assertEquals("offline", (state.loadError as ClientError.NetworkError).message)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:allTests --tests "*FeedShellStateTest" --no-daemon`
Expected: FAIL because `FeedShellState` does not exist.

- [ ] **Step 3: Write minimal feed-shell state implementation**

```kotlin
class FeedShellState(...) {
    suspend fun start() { refresh() }
    suspend fun refresh() { ... }
    fun selectSource(sourceId: String?) { ... }
    val sources: List<FeedSource> get() = ...
    val visibleItems: List<FeedItem> get() = ...
    val emptyState: VisibleFeedEmptyState? get() = ...
    val isLoading: Boolean get() = ...
    val loadError: ClientError? get() = ...
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:allTests --tests "*FeedShellStateTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/FeedShellState.kt \
  src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/FeedShellStateTest.kt
git commit -m "Add feed shell state"
```

## Task 3: Add Source Flow State For RSS and Bluesky

**Files:**
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AddSourceState.kt`
- Test: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/AddSourceStateTest.kt`

- [ ] **Step 1: Write the failing add-source tests**

```kotlin
@Test
fun `rss submission stores configured source`() = runTest {
    val state = AddSourceState(...)

    state.selectType(SourceType.Rss)
    state.submitRss("https://hnrss.org/newest")

    assertEquals(
        listOf(ConfiguredSource.RssFeed("https://hnrss.org/newest")),
        fakeConfiguredSourceRepository.sources
    )
}

@Test
fun `bluesky submission stores configured source`() = runTest {
    val state = AddSourceState(...)

    state.selectType(SourceType.Bluesky)
    state.submitBluesky("frank.bsky.social")

    assertEquals(
        listOf(ConfiguredSource.SocialUser(PlatformId.Bluesky, "frank.bsky.social")),
        fakeConfiguredSourceRepository.sources
    )
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:allTests --tests "*AddSourceStateTest" --no-daemon`
Expected: FAIL because the state does not exist.

- [ ] **Step 3: Write minimal add-source state**

```kotlin
class AddSourceState(...) {
    fun selectType(type: SourceType) { ... }
    suspend fun submitRss(url: String) { ... }
    suspend fun submitBluesky(handle: String) { ... }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:allTests --tests "*AddSourceStateTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/AddSourceState.kt \
  src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/AddSourceStateTest.kt
git commit -m "Add source flow state for rss and bluesky"
```

## Task 4: Replace the Template `App()` With the Feed Shell Root

**Files:**
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/App.kt`
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/FeedScreen.kt`
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/FeedItemList.kt`
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/EmptyFeedState.kt`
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/ResponsiveLayout.kt`
- Test: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/FeedScreenTest.kt`

- [ ] **Step 1: Write the failing feed-screen tests**

```kotlin
@Test
fun `feed screen shows add sources empty state when no sources exist`() = runComposeUiTest {
    setContent { FeedScreen(state = fakeState(noSources = true)) }

    onNodeWithText("Add sources").assertExists()
}

@Test
fun `feed screen shows oldest items first`() = runComposeUiTest {
    setContent { FeedScreen(state = fakeState(items = listOf(oldest, newest))) }

    onNodeWithText(oldest.title!!).assertExists()
}

@Test
fun `feed screen shows no items for selected source state distinctly from no sources`() = runComposeUiTest {
    setContent { FeedScreen(state = fakeState(noSources = false, noItemsForSelectedSource = true)) }

    onNodeWithText("No items for this source").assertExists()
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:allTests --tests "*FeedScreenTest" --no-daemon`
Expected: FAIL because the screen does not exist.

- [ ] **Step 3: Write minimal feed screen implementation**

```kotlin
@Composable
fun App() {
    val container = remember { createAppContainer() }
    val state = remember { FeedShellState(...) }
    FeedScreen(state = state, ...)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:allTests --tests "*FeedScreenTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/App.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/ResponsiveLayout.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/FeedScreen.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/FeedItemList.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/EmptyFeedState.kt \
  src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/FeedScreenTest.kt
git commit -m "Add compose feed shell screen"
```

## Task 5: Implement Responsive Source Filtering Controls

**Files:**
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/SourcePanel.kt`
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/SourceFilterDropdown.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/FeedScreen.kt`
- Test: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/FeedScreenTest.kt`

- [ ] **Step 1: Write the failing responsive filter tests**

```kotlin
@Test
fun `narrow layout uses dropdown source selector`() = runComposeUiTest {
    setContent { FeedScreen(state = fakeState(...), isWideLayout = false) }

    onNodeWithText("All items").assertExists()
    onNodeWithTag("source-filter-dropdown").assertExists()
}

@Test
fun `wide layout uses persistent source panel`() = runComposeUiTest {
    setContent { FeedScreen(state = fakeState(...), isWideLayout = true) }

    onNodeWithTag("source-panel").assertExists()
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:allTests --tests "*FeedScreenTest" --no-daemon`
Expected: FAIL because the responsive filter controls do not exist.

- [ ] **Step 3: Write minimal responsive filtering UI**

```kotlin
if (isWideLayout) {
    SourcePanel(...)
} else {
    SourceFilterDropdown(...)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:allTests --tests "*FeedScreenTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/SourcePanel.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/SourceFilterDropdown.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/FeedScreen.kt \
  src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/FeedScreenTest.kt
git commit -m "Add responsive source filtering controls"
```

## Task 6: Implement the Dedicated Add-Source Screens

**Files:**
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/AddSourceScreen.kt`
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/AddSourceTypePicker.kt`
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/AddRssSourceForm.kt`
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/AddBlueskySourceForm.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/App.kt`
- Test: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/AddSourceScreenTest.kt`

- [ ] **Step 1: Write the failing add-source screen tests**

```kotlin
@Test
fun `empty state button opens add-source picker`() = runComposeUiTest {
    setContent { AppRoot(fakeAppState()) }

    onNodeWithText("Add sources").performClick()
    onNodeWithText("Add a source").assertExists()
}

@Test
fun `floating action button opens same add-source picker`() = runComposeUiTest {
    setContent { AppRoot(fakeAppState()) }

    onNodeWithTag("feed-add-source-fab").performClick()
    onNodeWithText("Add a source").assertExists()
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:allTests --tests "*AddSourceScreenTest" --no-daemon`
Expected: FAIL because the add-source UI does not exist.

- [ ] **Step 3: Write minimal add-source screen implementation**

```kotlin
@Composable
fun AddSourceScreen(state: AddSourceState, ...) {
    when (state.step) {
        Picker -> AddSourceTypePicker(...)
        Rss -> AddRssSourceForm(...)
        Bluesky -> AddBlueskySourceForm(...)
        Twitter -> Text("Twitter support coming later")
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:allTests --tests "*AddSourceScreenTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/App.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/AddSourceScreen.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/AddSourceTypePicker.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/AddRssSourceForm.kt \
  src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/AddBlueskySourceForm.kt \
  src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/AddSourceScreenTest.kt
git commit -m "Add dedicated source creation flow"
```

## Task 7: Add Desktop/JVM Bootstrap Using CLI-Equivalent Persistence

**Files:**
- Create: `src/composeApp/src/jvmMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.jvm.kt`
- Modify: `src/composeApp/src/jvmMain/kotlin/com/franklinharper/social/media/client/main.kt`
- Reference: `src/cli/src/main/kotlin/com/franklinharper/social/media/client/cli/commands/DefaultCliApp.kt`
- Test: `src/composeApp/src/jvmTest/kotlin/com/franklinharper/social/media/client/app/JvmAppContainerFactoryTest.kt`

- [ ] **Step 1: Write the failing JVM bootstrap test**

```kotlin
@Test
fun `jvm app container uses file backed shared repositories`() {
    val container = createJvmAppContainer(tempFile)

    assertIs<DefaultFeedRepository>(container.feedRepository)
    assertNotNull(container.feedCacheRepository)
    assertNotNull(container.seenItemRepository)
    assertNotNull(container.sourceErrorRepository)
    assertEquals(listOf(PlatformId.Rss, PlatformId.Bluesky, PlatformId.Twitter), container.clientRegistry.all().map { it.id })
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:jvmTest --tests "*JvmAppContainerFactoryTest" --no-daemon`
Expected: FAIL because the JVM factory does not exist.

- [ ] **Step 3: Write minimal JVM bootstrap**

```kotlin
fun createJvmAppContainer(databasePath: File = defaultPath()): AppContainer {
    val database = JvmDatabaseFactory.fileBacked(databasePath)
    ...
    return DefaultAppContainer(...)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:jvmTest --tests "*JvmAppContainerFactoryTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composeApp/src/jvmMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.jvm.kt \
  src/composeApp/src/jvmMain/kotlin/com/franklinharper/social/media/client/main.kt \
  src/composeApp/src/jvmTest/kotlin/com/franklinharper/social/media/client/app/JvmAppContainerFactoryTest.kt
git commit -m "Add jvm compose app bootstrap"
```

## Task 8: Add Android and iOS Bootstrap

**Files:**
- Create: `src/shared/src/androidMain/kotlin/com/franklinharper/social/media/client/db/AndroidDatabaseFactory.kt`
- Create: `src/shared/src/iosMain/kotlin/com/franklinharper/social/media/client/db/IosDatabaseFactory.kt`
- Create: `src/composeApp/src/androidMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.android.kt`
- Create: `src/composeApp/src/iosMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.ios.kt`
- Modify: `src/androidApp/src/main/kotlin/com/franklinharper/social/media/client/MainActivity.kt`
- Modify: `src/composeApp/src/iosMain/kotlin/com/franklinharper/social/media/client/MainViewController.kt`
- Test: platform-targeted smoke coverage where feasible; common tests for shared bootstrap helpers if possible

- [ ] **Step 1: Write the failing bootstrap helper tests or compile targets**

Run: the nearest valid Android compile target for `composeApp` in this repo.
Expected: FAIL because platform container factories/database factories do not exist.
Note: skip iOS compile verification during routine development unless the task is specifically about iOS bring-up.

- [ ] **Step 2: Implement Android and iOS database/bootstrap factories**

```kotlin
actual fun createAppContainer(): AppContainer = createPlatformAppContainer(...)
```

- [ ] **Step 3: Re-run compile verification**

Run: the nearest valid Android compile target for `composeApp` in this repo.
Expected: PASS.
Note: skip iOS compile verification during routine development unless the task is specifically about iOS bring-up.

- [ ] **Step 4: Add any small shared tests for helper logic**

Run: `./gradlew :composeApp:allTests :shared:jvmTest --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/shared/src/androidMain/kotlin/com/franklinharper/social/media/client/db/AndroidDatabaseFactory.kt \
  src/shared/src/iosMain/kotlin/com/franklinharper/social/media/client/db/IosDatabaseFactory.kt \
  src/composeApp/src/androidMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.android.kt \
  src/composeApp/src/iosMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.ios.kt \
  src/androidApp/src/main/kotlin/com/franklinharper/social/media/client/MainActivity.kt \
  src/composeApp/src/iosMain/kotlin/com/franklinharper/social/media/client/MainViewController.kt
git commit -m "Add android and ios compose bootstrap"
```

## Task 9: Add Web Bootstrap and Persistence Strategy

**Files:**
- Modify: `src/shared/build.gradle.kts`
- Create: `src/composeApp/src/webMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.web.kt`
- Create: `src/composeApp/src/wasmJsMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.wasmJs.kt`
- Create or modify: `src/shared/src/jsMain/kotlin/com/franklinharper/social/media/client/db/...`
- Create or modify: `src/shared/src/wasmJsMain/kotlin/com/franklinharper/social/media/client/db/...`
- Modify: `src/composeApp/src/webMain/kotlin/com/franklinharper/social/media/client/main.kt`
- Modify: `src/composeApp/build.gradle.kts` if JS/Wasm test source sets need explicit test dependency wiring
- Test: `./gradlew :composeApp:jsTest :composeApp:compileKotlinWasmJs --no-daemon` or the nearest valid web targets in this repo

- [ ] **Step 1: Verify the current web persistence constraints**

Run: `./gradlew :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs --no-daemon`
Expected: PASS or reveal the exact source-set/bootstrap gap to close.

- [ ] **Step 2: Write the failing web bootstrap test or compile target check**

Run: `./gradlew :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs --no-daemon`
Expected: FAIL after adding `expect` usage until the web `actual` factory exists.

- [ ] **Step 3: Implement the web container factory**

```kotlin
actual fun createAppContainer(): AppContainer = ...
```

- [ ] **Step 4: Re-run web compile/tests**

Run: `./gradlew :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composeApp/src/webMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.web.kt \
  src/composeApp/src/wasmJsMain/kotlin/com/franklinharper/social/media/client/app/AppContainerFactory.wasmJs.kt \
  src/shared/build.gradle.kts \
  src/shared/src/jsMain/kotlin/com/franklinharper/social/media/client/db \
  src/shared/src/wasmJsMain/kotlin/com/franklinharper/social/media/client/db \
  src/composeApp/src/webMain/kotlin/com/franklinharper/social/media/client/main.kt \
  src/composeApp/build.gradle.kts
git commit -m "Add web compose bootstrap"
```

## Task 10: Finish Integration, Refresh UX, and Verification

**Files:**
- Modify: whichever UI/bootstrap files need final integration cleanup
- Test: `src/composeApp/src/commonTest/...`, `src/composeApp/src/jvmTest/...`, and relevant compile targets

- [ ] **Step 1: Add the manual refresh trigger test**

```kotlin
@Test
fun `refresh action triggers another feed load`() = runComposeUiTest {
    setContent { FeedScreen(state = fakeState(...)) }

    onNodeWithTag("feed-refresh-button").performClick()

    assertEquals(2, fakeFeedRepository.requests.size)
}
```

- [ ] **Step 2: Run targeted tests to verify the new test fails**

Run: `./gradlew :composeApp:allTests --tests "*FeedScreenTest" --no-daemon`
Expected: FAIL until refresh wiring is connected.

- [ ] **Step 3: Implement final refresh/navigation polish**

```kotlin
IconButton(
    modifier = Modifier.testTag("feed-refresh-button"),
    onClick = { scope.launch { state.refresh() } },
)
```

- [ ] **Step 4: Run full relevant verification**

Run:
```bash
cd src
./gradlew :composeApp:allTests :composeApp:jvmTest :shared:jvmTest :composeApp:compileKotlinAndroid :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composeApp src/shared/src/androidMain src/shared/src/iosMain src/shared/src/jsMain
git commit -m "Finish compose gui feed shell"
```

## Notes For Execution

- Follow TDD literally for each task: write the failing test, run it and confirm the failure, then write the minimum code to pass.
- Keep Compose UI files focused. Do not let `App.kt` turn into a large navigation/state blob.
- Do not block normal development on iOS compile or runtime verification. Only run iOS checks when the current task is specifically about the iOS target.
- If web persistence cannot cleanly match the persistence-backed requirement in one batch, stop and surface the exact technical constraint rather than quietly shipping an in-memory-only web path.
- Keep Twitter visibly deferred in the add-source picker rather than partially implemented.
- Run Gradle from `src/`, per repo instructions.
