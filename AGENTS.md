# AGENTS.md

## Scope

This file applies to the whole repository.

## Repository Shape

This repository is organized around a Kotlin Multiplatform project in `src/`.

- `src/shared`: core shared boundary for domain models, client interfaces, repositories, persistence, and cross-platform business rules.
- `src/cli`: JVM CLI and the primary delivery surface right now.
- `src/composeApp`: Compose Multiplatform UI entry points for desktop, web, and shared UI work.
- `src/androidApp`: Android host app.
- `src/iosApp`: iOS host app and SwiftUI/Xcode entry point.
- `src/server`: Ktor server for backend-only concerns.
- `docs/adr`: architecture decisions. Read these before making structural changes.

## Working Rules

- Run Gradle from `src/`, not from the repository root.
- Use the repo-local wrapper: `./gradlew`.
- Prefer relative wrapper commands such as `./gradlew :shared:jvmTest`.
- For normal CLI usage, prefer `src/social-cli` over `:cli:run`.
- Keep new app-level logic in `src/shared`; do not push business rules down into UI or concrete clients.
- Treat the CLI as the first-class executable surface unless the task is explicitly UI- or server-specific.
- Follow the ADRs, especially:
  - `docs/adr/0001-cli-first-delivery.md`
  - `docs/adr/0002-shared-core-boundary.md`
  - `docs/adr/0003-cli-cwd-persistence.md`

## Architecture Constraints

- `shared` owns the common domain and orchestration layer.
- CLI and GUI code should depend on shared repositories/interfaces, not concrete clients directly.
- Concrete platform clients should focus on platform integration, not feed merging, seen filtering, or other app-level policy.
- CLI persistence is intentionally scoped to the current working directory.

## Common Commands

Run these from `src/`.

```bash
./gradlew :shared:jvmTest
./gradlew :cli:test
./gradlew :server:test
./gradlew :composeApp:run
./gradlew :server:run
./social-cli list-sources
./social-cli --rebuild list-sources
```

## Change Guidance

- If you change shared models, repository contracts, or persistence behavior, update or add tests in `src/shared/src/commonTest` or `src/shared/src/jvmTest`.
- If you change CLI behavior, update tests in `src/cli/src/test`.
- Keep edits small and module-local when possible.
- Do not introduce direct dependencies from `composeApp` or `cli` onto implementation details that belong behind `shared`.
- Preserve the existing JVM 11 target/toolchain assumptions unless the task explicitly requires changing them.
- This code is not in production yet. When schema changes are needed, update the schema directly and do not create database migrations.

## Before Finishing

- Run the narrowest relevant tests for the modules you changed.
- If you changed architecture or module boundaries, confirm the change is still consistent with the ADRs.
- Mention any commands you could not run or any assumptions you had to make.
