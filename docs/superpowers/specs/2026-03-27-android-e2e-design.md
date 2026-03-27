# Android Emulator E2E Design

## Goal

Add one Android end-to-end test flow that runs against an emulator, starts from a fresh install, performs the same user journey as the existing web E2E test, and produces a report that includes crashes plus application-level warnings, errors, and failures with screen and step context.

## User Flow

The Android flow should match the current web HNRSS E2E coverage:

- fresh install with app data removed
- sign up with a unique account
- add the fixture-backed RSS source
- verify the expected feed items appear
- sign out
- sign back in
- refresh the feed
- verify the expected feed items still appear

## Non-Goals

- Do not add coordinate-based automation.
- Do not add production-only logic branches for tests.
- Do not replace the existing web E2E harness.
- Do not build a general-purpose Android automation framework in the first slice.

## Approach

Use an Android instrumentation test in `src/androidApp/src/androidTest` as the primary driver for UI actions. The instrumentation test will use Compose test APIs and stable logical selectors such as existing `testTag` values and visible text instead of pixel coordinates.

Pair that instrumentation test with a small host-side runner script that prepares and reports the run:

- uninstall the Android app first to guarantee a fresh install
- ensure the backend fixture dependencies are running
- clear logcat before the test window
- install the app and Android test APK
- run the instrumentation test on the selected emulator
- collect instrumentation output and fresh logcat into a structured report

This keeps UI interaction in the Android test layer, where Compose selectors are stable, while leaving fresh-install orchestration and crash/log capture to the host side where they are easier to control.

## Test Seams

The instrumentation test should use logical selectors only. Existing tags already cover key auth and feed shell actions, but the RSS add-source path currently lacks the stable selectors needed for instrumentation parity with the web test. This slice should add only the missing tags needed to drive:

- RSS URL entry
- RSS add-source submission
- visible handled-error text where needed

These tags are test-only seams on existing UI elements, not alternate UI or alternate business logic.

## Diagnostics Model

The final report should distinguish between different classes of issues:

- `warning`
- `handled_error`
- `assertion_failure`
- `crash`

Each issue entry should include enough context to make the result actionable:

- screen name
- test step
- message text shown to the user, if any
- exception or crash text, if any
- relevant log excerpt, if available

The instrumentation test should explicitly note handled UI errors it encounters during login, add-source, and feed verification steps. The host-side runner should identify uncaught crashes and Android runtime failures from logcat captured during the run.

## Report Format

Produce both:

- a human-readable summary file
- a machine-readable JSON report

Both reports should live under a stable output directory so repeated runs are easy to inspect. The report should include:

- emulator/device identifier
- app/test package info
- step-by-step outcomes
- collected warnings/errors/failures/crashes
- overall status

## Fixture And Backend Dependencies

The Android test should use the same deterministic RSS fixture concept as the web E2E suite. The host-side runner should reuse the existing local Ktor server and fixture server pattern rather than inventing a second backend stack.

For Android, the RSS fixture URL must be reachable from the emulator. The simplest expected mapping is the emulator host alias form of the existing local fixture server URL.

## Verification

Verification for this slice should include:

- the Android instrumentation E2E test running on an emulator from a fresh install
- the host-side runner producing both report files
- at least one successful end-to-end run proving the full auth/add-source/sign-out/sign-in/refresh flow

## Risks

- Compose semantics and Android instrumentation may require a few extra tags in the add-source path.
- Local server availability and emulator host addressing must be handled carefully for deterministic RSS fetches.
- Logcat noise can hide useful failures unless the runner filters the report to the test window and app process.
