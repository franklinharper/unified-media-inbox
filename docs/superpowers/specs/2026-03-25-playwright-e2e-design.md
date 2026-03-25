# Playwright E2E Design

## Goal

Add browser automation tests that drive the real web client against the real local server, with Playwright automatically starting and stopping both processes.

## Scope

The first version targets the JS web build rather than Wasm to maximize browser automation compatibility. The first test covers self-service signup through the real browser UI and verifies the user lands in the authenticated feed shell.

## Approach

Create a separate root-level `e2e/` Playwright project. Configure Playwright `webServer` entries to launch:

- `cd src && ./gradlew :server:run --no-daemon`
- `cd src && ./gradlew :composeApp:jsBrowserDevelopmentRun --no-daemon`

Playwright waits for `http://127.0.0.1:8080/` and `http://127.0.0.1:8081/`, runs Chromium against `http://127.0.0.1:8081/`, and tears both processes down when the run completes.

## Test Strategy

- Use existing auth `testTag` selectors for the login/signup form
- Add one or two stable selectors in the authenticated shell if needed
- Use unique signup emails per run to avoid fixture collisions
- Keep the first test narrow: signup only, then verify authenticated shell visibility

## Repo Integration

- Add `e2e/package.json`
- Add `e2e/playwright.config.ts`
- Add `e2e/tests/auth-signup.spec.ts`
- Document the new command in `src/README.md`

## Verification

- Install Playwright dependencies
- Run the new end-to-end command locally
- Confirm Playwright starts and stops both Gradle processes automatically
