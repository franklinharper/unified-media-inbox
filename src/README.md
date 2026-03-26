This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM), Server.

Run Gradle from the `src/` directory with the repo-local wrapper using relative commands such as `./gradlew :shared:jvmTest`. Avoid absolute wrapper paths.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/server](./server/src/main/kotlin) is for the Ktor server application.

* [/shared](./shared/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./shared/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### Build and Run Server

To build and run the development version of the server, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :server:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :server:run
  ```

### Run the Social CLI

For normal CLI use, prefer the repo-local launcher instead of `gradlew :cli:run`. It installs the CLI distribution once and then runs the generated binary directly, which avoids most Gradle noise on repeated runs.

- on macOS/Linux
  ```shell
  ./social-cli list-sources
  ./social-cli list-new-items --platform bluesky --user frank.bsky.social
  ```

Use `./social-cli --rebuild ...` after changing CLI code and wanting a fresh install.

Examples:
```shell
./social-cli add-feed https://hnrss.org/newest
./social-cli remove-feed https://hnrss.org/newest
./social-cli import-follows bluesky
./social-cli import-opml ~/Downloads/feedly-export.opml
./social-cli list-errors
```

### Build and Run Web Application

To build and run the development version of the web app, use the run configuration from the run widget
in your IDE's toolbar or run it directly from the terminal:
- for the Wasm target (faster, modern browsers):
  - on macOS/Linux
    ```shell
    ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
    ```
  - on Windows
    ```shell
    .\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
    ```
- for the JS target (slower, supports older browsers):
  - on macOS/Linux
    ```shell
    ./gradlew :composeApp:jsBrowserDevelopmentRun
    ```
  - on Windows
    ```shell
    .\gradlew.bat :composeApp:jsBrowserDevelopmentRun
    ```

The web app uses same-origin `/api/...` requests by default. To point it at a different API origin in staging or production,
set the `social-media-client-api-base-url` meta tag in [composeApp/src/webMain/resources/index.html](./composeApp/src/webMain/resources/index.html):

```html
<meta name="social-media-client-api-base-url" content="https://api.example.com">
```

For local development, the webpack dev server runs on `http://localhost:8081` and proxies `/api/...` requests to the Ktor server on `http://localhost:8080`.

### Browser E2E Scaffold

There is a Playwright scaffold in [`../e2e`](../e2e) that can start the local server and JS web app automatically:

```shell
cd ../e2e
npm test
```

The current browser test drives the Compose JS app through Playwright and validates the sign-up, sign-in, sign-out, add-source, and feed-refresh flow against a deterministic RSS fixture served over HTTP.
For the most brittle browser interactions, the test runner now enables a web-only automation bridge with `?automationBridge=1`. The bridge exposes stable DOM controls for auth, RSS add-source, refresh, and sign-out, plus read-only feed metadata for assertions, while still invoking the same app callbacks and backend requests as the real UI.

Playwright also starts a tiny fixture server on `http://127.0.0.1:9090` so the app server fetches a real RSS URL during the test without depending on live third-party feed content.

Near-term browser auth e2e TODOs:
- duplicate-email signup error
- wrong-password sign-in error
- session-expired bounce back to login

For repeatable browser debugging without ad hoc `node -e` commands, use the checked-in debug scripts:

```shell
cd ../e2e
npm run debug:signup
npm run debug:add-source
npm run debug:hnrss
```

These scripts are intended to be approved once as stable `npm run ...` commands instead of approving each one-off Playwright probe separately.

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
