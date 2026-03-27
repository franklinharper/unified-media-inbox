# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

All Gradle commands run from `src/` using the repo-local wrapper:

```bash
# Tests
./gradlew :shared:jvmTest        # shared module (run this most often)
./gradlew :cli:test
./gradlew :server:test

# Run
./gradlew :composeApp:run        # desktop app
./gradlew :server:run            # Ktor backend (port 8080)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web app (modern browsers)
./gradlew :composeApp:jsBrowserDevelopmentRun       # web app (older browsers)
./gradlew :composeApp:assembleDebug                 # Android APK

# CLI (preferred over :cli:run)
./social-cli list-sources
./social-cli list-new-items --platform bluesky --user frank.bsky.social
./social-cli --rebuild list-sources   # rebuilds distribution first
```

E2E tests (from `e2e/`):
```bash
npm test                  # Playwright browser tests
npm run debug:signup
```

## Module Structure

```
src/
  shared/       # core boundary — domain models, repository interfaces, platform clients, SQLDelight persistence
  cli/          # JVM CLI, primary delivery surface
  composeApp/   # Compose Multiplatform UI (desktop, web, Android, iOS)
  server/       # Ktor backend (OAuth, auth, sync API)
  androidApp/   # Android host
  iosApp/       # iOS host (SwiftUI/Xcode)
e2e/            # Playwright browser tests
docs/adr/       # Architecture Decision Records — read before making structural changes
```

Dependencies flow inward: `cli`, `composeApp`, `server` → `shared`. Never the reverse.

## Architecture

**Shared core boundary** (`src/shared`): owns domain models, repository contracts, platform client interfaces, orchestration logic, and SQLDelight persistence. Business rules live here — not in CLI or UI code.

**Repository pattern with orchestration**: `DefaultFeedRepository` batches requests across platforms (e.g., multiple Twitter sources → single API call), merges feeds, applies seen-item filtering, and falls back to cache on errors.

**Platform clients** implement `SocialPlatformClient`: `TwitterClient`, `BlueskyClient`, `RssClient`. They handle HTTP (Ktor) and auth, not feed merging or app policy. `ClientRegistry` maps `PlatformId` to implementations.

**SQLDelight** for multiplatform persistence. Schema in `src/shared/src/commonMain/sqldelight/`. No migrations — schema changes go directly to the schema file (not in production yet).

**Web app** runs Wasm/JS via webpack dev server (`:8081`) proxying `/api` to Ktor (`:8080`).

**Sealed types** throughout: `ConfiguredSource`, `FeedQuery`, `SessionState`, `SourceLoadState`, `ClientError`.

## Key Source Locations

| What | Where |
|------|-------|
| Domain models | `src/shared/src/commonMain/kotlin/.../domain/FeedModels.kt` |
| Repository interfaces | `src/shared/src/commonMain/kotlin/.../repository/Repositories.kt` |
| Feed orchestration | `src/shared/src/commonMain/kotlin/.../repository/DefaultFeedRepository.kt` |
| SQLDelight schema | `src/shared/src/commonMain/sqldelight/.../SocialMediaDatabase.sq` |
| Platform client interface | `src/shared/src/commonMain/kotlin/.../client/SocialPlatformClient.kt` |
| CLI entry point | `src/cli/src/main/kotlin/.../cli/Main.kt` |
| Compose DI | `src/composeApp/src/commonMain/kotlin/.../app/ComposeAppContainerFactory.kt` |
| Dependency versions | `src/gradle/libs.versions.toml` |

## Working Rules

- Run Gradle from `src/`, not repo root.
- Keep new app-level logic in `shared`; CLI and GUI depend on shared interfaces.
- Treat CLI as the primary executable surface unless the task is explicitly UI- or server-specific.
- If you change shared models, repository contracts, or persistence, update tests in `src/shared/src/commonTest` or `src/shared/src/jvmTest`.
- Preserve the JVM 11 target unless the task explicitly requires changing it.
- Run the narrowest relevant tests for the modules you changed.
- If you change architecture or module boundaries, verify consistency with the ADRs in `docs/adr/`.
