# Playwright HNRSS E2E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one Playwright browser test that signs up, adds the Hacker News front-page RSS feed, and verifies the web app loads recent feed items.

**Architecture:** Extend the existing `e2e/` Playwright scaffold with one canvas-interaction helper module and one real browser spec. The test drives the JS web app through mouse/keyboard input and verifies feed loading from the browser-observed `/api/feed` response payload.

**Tech Stack:** Playwright, TypeScript, Gradle, Compose Multiplatform JS, Ktor

---

### Task 1: Canvas Interaction Helpers

**Files:**
- Create: `e2e/tests/support/canvasApp.ts`

- [ ] Step 1: Write helper functions for signup and add-source canvas interactions at a fixed viewport
- [ ] Step 2: Keep the helper API narrowly scoped to the HNRSS flow
- [ ] Step 3: Reuse the helper in the browser spec

### Task 2: HNRSS Browser Spec

**Files:**
- Create: `e2e/tests/auth-rss-feed.spec.ts`
- Modify: `e2e/tests/auth-signup.spec.ts`

- [ ] Step 1: Write the failing Playwright HNRSS flow test
- [ ] Step 2: Replace the placeholder skipped signup spec with a skipped note or narrow helper if still needed
- [ ] Step 3: Run the browser test and capture the first real failure
- [ ] Step 4: Fix the minimal test issues without broadening scope
- [ ] Step 5: Re-run until green

### Task 3: Verification

**Files:**
- Modify: `src/README.md`

- [ ] Step 1: Document that the first live e2e uses canvas interactions and response assertions
- [ ] Step 2: Run `cd src && ./gradlew :composeApp:compileKotlinJs --no-daemon`
- [ ] Step 3: Run `cd e2e && npx playwright test`
- [ ] Step 4: Commit the completed e2e flow
