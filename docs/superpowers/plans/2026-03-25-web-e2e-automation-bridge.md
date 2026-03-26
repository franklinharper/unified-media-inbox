# Web E2E Automation Bridge Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize Playwright browser automation for web auth, RSS add-source, and feed-header actions without changing app behavior.

**Architecture:** Add a shared automation state in `composeApp/commonMain`, then expose a query-param-gated web DOM automation bridge in `webMain` that invokes the same real app callbacks as the Compose UI.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform JS, DOM APIs, Playwright, Ktor

---

### Task 1: Shared Automation State

**Files:**
- Create: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/app/WebAutomationState.kt`
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/App.kt`

- [x] Step 1: Add a minimal shared automation state for auth fields, visibility, and bound callbacks
- [x] Step 2: Wire `App`/`AppRoot` to keep the automation state synchronized with real auth/feed state
- [ ] Step 3: Verify shared and Compose tests still pass

### Task 2: Web Automation Bridge

**Files:**
- Create: `src/composeApp/src/webMain/kotlin/com/franklinharper/social/media/client/app/WebAutomationBridge.kt`
- Modify: `src/composeApp/src/webMain/kotlin/com/franklinharper/social/media/client/main.kt`

- [x] Step 1: Detect the bridge query parameter and install the DOM automation bridge only when enabled
- [x] Step 2: Add stable auth, RSS add-source, and feed-header bridge controls with `data-testid` hooks
- [x] Step 3: Keep the bridge bound to the shared automation state without introducing alternate app logic
- [x] Step 4: Verify JS compilation

### Task 3: Playwright Migration

**Files:**
- Modify: `e2e/tests/support/canvasApp.ts`
- Modify: `e2e/tests/auth-rss-feed.spec.ts`

- [x] Step 1: Load the app with the automation bridge enabled
- [x] Step 2: Switch auth, RSS add-source, refresh, and sign-out actions from canvas coordinates to bridge selectors
- [x] Step 3: Re-run the targeted HNRSS Playwright spec until green

### Task 4: Docs

**Files:**
- Modify: `src/README.md`

- [x] Step 1: Document the bridge purpose and activation query parameter for local browser automation
- [ ] Step 2: Keep the auth error-path e2e follow-ups recorded as TODOs
