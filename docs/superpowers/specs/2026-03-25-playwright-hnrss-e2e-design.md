# Playwright HNRSS E2E Design

## Goal

Add one real browser automation test that signs up through the web client, adds the Hacker News RSS front-page feed, and verifies the web app loads recent feed items for that source.

## Scope

This test targets the existing JS web build and uses the current Compose canvas UI. It covers the full browser flow for signup and RSS source creation. Feed verification is based on the web client's network response payload because item text is rendered into canvas and is not yet exposed through stable DOM selectors.

## Approach

Use the existing root-level Playwright project with automatic `webServer` startup for:

- `cd src && ./gradlew :server:run --no-daemon`
- `cd src && ./gradlew :composeApp:jsBrowserDevelopmentRun --no-daemon`

Implement one test helper module that performs deterministic canvas interactions at a fixed viewport:

- fill login email/password fields
- click `Create account`
- open the add-source flow
- choose RSS
- fill the feed URL
- click `Add source`

Then wait for the feed API response triggered by the app and assert:

- response status is `200`
- feed contains at least one item
- at least one item title is non-blank
- the newest item is recent enough to indicate the front page feed loaded successfully

## Constraints

- Compose JS currently exposes a single canvas rather than browser-accessible inputs and item text.
- The test therefore uses click coordinates and response-payload assertions instead of DOM selectors.
- The viewport must stay fixed for this version.

## Verification

- `cd src && ./gradlew :composeApp:compileKotlinJs --no-daemon`
- `cd e2e && npx playwright test`
