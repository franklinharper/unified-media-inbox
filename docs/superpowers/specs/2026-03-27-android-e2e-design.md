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

This first slice also includes the Android test-infrastructure setup needed to make that possible:

- instrumentation runner configuration in `src/androidApp/build.gradle.kts`
- Android test dependencies for Compose/UI instrumentation
- any minimal manifest or packaging configuration required for `connectedAndroidTest`-style execution

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

Issue-class definitions for this slice:

- `warning`: non-fatal issues explicitly recorded by the test harness itself, such as a step recovering after a retry or a known degraded-but-non-failing state. Generic warning-level logcat noise is out of scope for this slice.
- `handled_error`: visible app-level error text shown in the UI while the app continues running
- `assertion_failure`: a failed instrumentation expectation, reported with the active screen and test step instead of only the raw JUnit failure text
- `crash`: uncaught exception or Android runtime process failure observed during the test window

The instrumentation test should explicitly note handled UI errors it encounters at every major user-flow step:

- sign up
- add source
- initial feed verification
- sign out
- sign back in
- refresh
- final feed verification

The host-side runner should identify uncaught crashes and Android runtime failures from logcat captured during the run.

The instrumentation layer should wrap each major step in a small reporting helper so assertion failures are recorded with:

- current screen
- current step name
- expected condition
- actual observed state, when available
- original assertion exception text

To preserve crash context, that same helper should also write the current screen and step to a small host-readable progress artifact before executing each major action. If the app process crashes, the host-side runner should attach the most recent recorded screen/step to the crash entry instead of guessing from logcat alone.

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

For reproducibility, the runner owns backend orchestration for the first slice. It should:

- start the fixture server itself
- start the Ktor server itself
- wait for both health checks before installing and launching the Android test
- tear them down when the run completes

The Android E2E command should therefore be self-contained rather than depending on manually pre-started services.

The Android app talks to the Ktor server through the emulator host alias, but the server is the component that fetches RSS source URLs after the source is submitted. The RSS fixture URL used in the add-source form should therefore stay server-reachable in the same way as the web E2E flow rather than being rewritten to an emulator-only address.

Expected first-slice fixture URL:

- `http://127.0.0.1:9090/feeds/hn-frontpage.xml`

## Verification

Verification for this slice should include:

- the Android instrumentation E2E test running on an emulator from a fresh install
- the host-side runner producing both report files
- at least one successful end-to-end run proving the full auth/add-source/sign-out/sign-in/refresh flow

## Risks

- Compose semantics and Android instrumentation may require a few extra tags in the add-source path.
- Local server availability and emulator host addressing must be handled carefully for deterministic RSS fetches.
- Logcat noise can hide useful failures unless the runner filters the report to the test window and app process.
