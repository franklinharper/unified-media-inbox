# Web Signup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add open self-service signup to the web app so new users can create an account and enter the authenticated feed flow.

**Architecture:** Add a server `sign-up` route that reuses the existing auth payload and session response, extend the shared web auth repository/state with signup, and evolve the existing login screen into a dual-action auth screen. Keep the scope narrow and preserve the current login-first app flow.

**Tech Stack:** Kotlin Multiplatform, Ktor, SQLDelight, Compose Multiplatform, kotlinx.serialization, Gradle

---

### Task 1: Server Signup API

**Files:**
- Modify: `src/server/src/main/kotlin/com/franklinharper/social/media/client/api/ApiRoutes.kt`
- Modify: `src/server/src/main/kotlin/com/franklinharper/social/media/client/auth/ServerSessionService.kt`
- Test: `src/server/src/test/kotlin/com/franklinharper/social/media/client/AuthApiTest.kt`

- [ ] Step 1: Write failing server tests for successful signup and duplicate-email conflict
- [ ] Step 2: Run `cd src && ./gradlew :server:test --tests "*AuthApiTest" --no-daemon` and verify failure
- [ ] Step 3: Implement `POST /api/auth/sign-up` and duplicate-email handling
- [ ] Step 4: Run the same server test command and verify pass
- [ ] Step 5: Commit server signup API changes

### Task 2: Shared Remote Auth Signup

**Files:**
- Modify: `src/shared/src/commonMain/kotlin/com/franklinharper/social/media/client/remote/WebRemoteSessionRepository.kt`
- Test: `src/shared/src/commonTest/kotlin/com/franklinharper/social/media/client/remote/WebRemoteRepositoriesTest.kt`

- [ ] Step 1: Write failing shared tests for signup request/response mapping
- [ ] Step 2: Run `cd src && ./gradlew :shared:jvmTest --tests "*WebRemoteRepositoriesTest" --no-daemon` and verify failure
- [ ] Step 3: Implement `signUp(email, password)` in the web auth repository
- [ ] Step 4: Run the same shared test command and verify pass
- [ ] Step 5: Commit shared signup repository changes

### Task 3: Web Auth State And UI

**Files:**
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/WebAuthState.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/LoginScreen.kt`
- Test: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/app/WebAuthStateTest.kt`
- Test: `src/composeApp/src/commonTest/kotlin/com/franklinharper/social/media/client/ui/LoginScreenTest.kt`

- [ ] Step 1: Write failing compose/auth tests for signup action dispatch and successful signup state transition
- [ ] Step 2: Run `cd src && ./gradlew :composeApp:jvmTest --tests "*WebAuthStateTest" --tests "*LoginScreenTest" --no-daemon` and verify failure
- [ ] Step 3: Implement signup support in auth state and the combined auth screen
- [ ] Step 4: Run the same compose test command and verify pass
- [ ] Step 5: Commit compose signup changes

### Task 4: Integration Verification

**Files:**
- Modify: `src/README.md` if needed for auth flow notes

- [ ] Step 1: Run `cd src && ./gradlew :server:test --tests "*AuthApiTest" --no-daemon`
- [ ] Step 2: Run `cd src && ./gradlew :shared:jvmTest --tests "*WebRemoteRepositoriesTest" --no-daemon`
- [ ] Step 3: Run `cd src && ./gradlew :composeApp:jvmTest --tests "*WebAuthStateTest" --tests "*LoginScreenTest" --no-daemon`
- [ ] Step 4: Run `cd src && ./gradlew :composeApp:compileKotlinJs :composeApp:compileKotlinWasmJs --no-daemon`
- [ ] Step 5: Commit final verification and docs updates
