# Web E2E Automation Bridge Design

## Goal

Reduce Playwright brittleness for the web client by replacing canvas hitbox interactions for the highest-value auth, RSS add-source, and feed-header actions with a stable browser automation surface that still drives the real app callbacks, real browser, and real backend.

## Problem

The current Compose JS web UI renders through a canvas-like surface. Browser semantics are partially visible to Playwright, but pointer events are still intercepted by the canvas. This makes browser tests rely on fragile coordinates for sign in, sign up, RSS add-source, refresh, and sign out.

## Non-Goals

- Do not add alternate business logic for tests.
- Do not bypass repositories, auth, or network requests.
- Do not replace the painted Compose UI.
- Do not solve every web interaction in the first slice.

## Approach

Add a thin web-only automation bridge that is enabled only when the page is loaded with a dedicated query parameter. The bridge exposes DOM inputs and buttons for a narrow set of existing actions:

- auth email
- auth password
- sign in
- sign up
- RSS source URL
- add RSS source
- feed refresh
- sign out

Those controls invoke the same app callbacks the Compose UI already uses.

## Shared Automation State

Add a small shared automation state object in `composeApp/commonMain` that:

- stores the current automation credential and RSS source fields
- tracks whether auth or feed actions should be visible/enabled
- holds the current callback bindings for sign in, sign up, opening the add-source flow, selecting RSS, adding an RSS source, refresh, and sign out

`App` and `AppRoot` update this shared state from the real app state, and the web-only automation bridge reads from it.

This is not a second state machine. It is only a synchronization layer between the real app callbacks and a web-only DOM automation surface.

## Web Bridge

In `webMain`:

- detect `?automationBridge=1`
- create a small DOM overlay with stable `data-testid` hooks
- wire the DOM controls to the shared automation state
- keep the controls stable enough for Playwright to target directly

The DOM overlay should be clearly non-user-facing and scoped to automation runs only.

## First Slice Scope

Implement only:

- auth bridge controls
- RSS add-source bridge controls
- feed refresh bridge control
- feed sign-out bridge control
- Playwright migration for those actions

## Testing

- Reuse existing shared and Compose tests for sign-out behavior.
- Update the HNRSS Playwright test to load the bridge-enabled URL and use bridge selectors for auth, RSS add-source, refresh, and sign-out actions.
- Verify the real browser flow still performs:
  - sign up
  - add HNRSS source
  - verify feed
  - sign out
  - sign back in
  - verify feed again

## Follow-Up TODOs

Later slices can extend the bridge or improve production semantics for:

- feed item open/comments actions
- richer add-source flows beyond RSS
- additional auth error-path e2e scenarios
