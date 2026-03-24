# GUI Feed Shell Design

## Status

Approved for planning.

## Context

The repository is currently CLI-first, with the shared module owning repository orchestration, domain models, and persistence-facing abstractions. The Compose Multiplatform app is still the template stub, so the first real GUI surface needs to exercise the same shared boundary without creating a separate app-level logic path.

The GUI should begin as a read-focused application shell that lets users see feed items aggregated from configured sources. Source configuration and filtering need to be accessible, but the design should preserve the feed as the primary surface.

## Goals

- Show a chronological merged feed using shared repositories
- Keep the feed as the main application surface
- Provide a clear empty state when no sources are configured
- Support source filtering on both narrow and wide screens
- Provide a dedicated add-source flow optimized for first-time setup
- Keep app-level behavior in `shared`, not in Compose-only logic

## Non-Goals

- Building a full editing-heavy source management console in the main shell
- Reworking shared repository boundaries for GUI-specific concerns
- Adding sign-in UX in this first GUI shell unless required by source creation flows
- Introducing a separate use-case layer outside the existing shared boundary

## Primary User Experience

### Feed Screen

The default screen is a feed view that shows items from configured sources ordered chronologically, with the oldest items at the top.

The feed header reflects the current filter:

- `All items` when no source-specific filter is active
- the selected source name when a filter is active

If sources exist, the main content area shows feed items only. If there are no sources configured yet, the feed content area is replaced by an empty state that explains the app is ready for setup and presents a prominent `Add sources` action.

### Add Entry Points

Two entry points open the same add-source flow:

- the empty-state `Add sources` button
- a floating action button with a `+` icon anchored at the bottom right of the feed screen

The floating action button remains available even after sources have been added.

## Responsive Layout

### Narrow Screens

Narrow layouts keep the feed full width.

Source filtering stays on the feed screen through a dropdown selector in the feed header. The dropdown includes:

- `All items`
- one entry per configured source

Changing the dropdown selection filters the currently visible feed items in place.

### Wide Screens

Wide layouts split the shell into two areas:

- a persistent sources panel on the left
- the feed list on the right

The left panel is navigation and filtering only. It includes:

- `All items`
- one row per configured source

Selecting a row filters the feed list in place. The panel does not expose inline editing or source creation controls beyond the existing global add entry points.

## Source Management Flow

The first screen in the add-source flow is a source-type picker rather than a management hub. This makes first-time setup faster and matches the empty-state onboarding path.

The source-type picker should offer at least:

- RSS
- Bluesky
- Twitter

After the user selects a source type, the flow advances to source-specific input for the required details.

For the first implementation cut:

- RSS add flows should be fully functional
- Bluesky add flows should be fully functional
- Twitter may appear as deferred or unavailable, but it is not required to be functional in this batch

Management of existing sources can remain a secondary capability behind this dedicated area, but the primary entry path should optimize for adding the next source rather than browsing current ones.

## State Model

The GUI should derive its state from shared repositories and shared domain models.

At minimum, the feed shell needs to represent:

- configured sources
- selected source filter
- loaded feed items
- empty state due to zero configured sources
- empty state due to a filter returning no visible items
- loading and error status for refresh/load operations

The “no sources yet” empty state must be distinct from “this source has no items.”

## Architecture Constraints

- Compose UI depends on shared repositories/interfaces, not concrete platform clients
- Source filtering in the GUI should operate on repository results and source metadata, not duplicate client orchestration logic
- Any reusable feed-loading or filtering policy that belongs across surfaces should remain in `shared`
- The GUI should preserve ADR 0001 and ADR 0002 by treating the shared module as the application boundary

## Repository Wiring

The GUI target is the full Compose surface across Android, iOS, desktop, and web.

During active development, iOS verification is deferred unless the current task is specifically about bringing up or debugging the iOS target. iOS remains part of the intended product surface, but routine implementation work should not block on running iOS compile or execution checks.

All GUI targets should use the shared repositories and shared domain boundary rather than introducing UI-specific feed orchestration.

Desktop should use the same local persistence-backed shared repository approach as the CLI rather than an in-memory or demo-only wiring path.

Android, iOS, and web should follow the same shared repository contracts, with platform-appropriate bootstrap and persistence implementations where required.

This means the GUI rollout should exercise real configured-source persistence, feed cache persistence, seen-state persistence, and the same shared repository orchestration already established in `shared`, while allowing each target to supply the platform-specific repository wiring it needs.

## Initial Screen Set

The first GUI cut should include:

1. Feed screen
2. Add-source type picker
3. Source-specific add flows or forms for supported source types

Supported functional source types in the first cut:

- RSS
- Bluesky

Twitter support can be added later without blocking the first GUI shell.

## Refresh Behavior

The feed screen should automatically load or refresh when it opens.

After the initial automatic load, the GUI should also expose an explicit user-triggered refresh action so users can request a new fetch without leaving the screen.

This first cut does not require a complex separate management dashboard embedded in the shell.

## Testing Focus

The implementation plan should cover tests for:

- feed shell rendering with no sources
- feed shell rendering with items
- narrow-screen source filtering through the dropdown
- wide-screen source filtering through the left panel
- consistent navigation from both add entry points into the same add-source flow
- distinction between “no sources configured” and “no items for selected source”
- reliance on shared repositories rather than direct concrete-client wiring in Compose UI

## Planning Decisions Captured

- The GUI is a cross-platform Compose surface for Android, iOS, desktop, and web
- Desktop Compose uses the same persistence-backed shared repositories as the CLI
- Other GUI targets use the same shared repositories with platform-specific bootstrap and persistence adapters where needed
- RSS and Bluesky add-source flows are fully functional in the first GUI cut
- Twitter add flow is deferred
- Feed loading happens automatically on screen entry, with manual refresh also available
