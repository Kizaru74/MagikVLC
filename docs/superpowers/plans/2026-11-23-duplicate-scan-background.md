# Reliable Background Duplicate Scan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent duplicate scans over an empty/stale list and keep one scan observable while the user changes tabs or minimizes MagikVLC, with foreground progress notification.

**Architecture:** A unique WorkManager `CoroutineWorker` owns the scan independently of Compose and runs foreground with a progress/cancel notification. The worker loads a fresh MediaStore snapshot, calls the existing SHA/probable algorithm, persists a serializable report keyed by work ID, and exposes small progress/status values through WorkInfo. `LibraryScreen` starts only after a completed nonempty library load and observes the work/report on return.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX WorkManager foreground worker, existing kotlinx.serialization and coroutine APIs.

**Spec:** `docs/superpowers/specs/2026-11-23-duplicate-scan-background-design.md`

## Global Constraints

- Never start a duplicate scan while the library is loading or from an uninitialized empty list.
- An actually empty library must show an empty-library state, not a successful `Escaneados 0 vídeos` duplicate report.
- Keep exact duplicates confirmed by full SHA-256 and preserve current probable-group heuristic and basic/deep behavior.
- Continue the unique scan across tab/navigation changes and app minimization; user cancellation is explicit only.
- Start automatically upon entering Duplicados after the library snapshot is ready, matching current UX.
- On Android 13+ request notification permission in context; if denied, continue and explain the system may hide the notification from the drawer.
- Do not put the full library or full report in WorkManager `Data`; use a private serialized report store keyed by work ID.
- Keep notification text privacy-safe and progress updates throttled; do not log each file.
- Leave vertical-video gesture work out of this plan.

## Review Focus

- Library load races: Duplicados opens before cache/MediaStore finishes; scan must wait and use the final snapshot.
- Empty vs. not-yet-loaded: a truly empty library must not create a worker or report zero scanned videos as “no duplicates”.
- Background interruption: switching tabs/minimizing must not cancel; system stop/process recreation must not expose a stale success report.
- Notification denial: scan still runs, status remains queryable in-app, and the app explains drawer visibility limitations.
- Mode/result races: changing basic/deep mode or requesting a fresh scan must not let an older worker overwrite the current report.

---

### Task 1: Scan readiness and report-store policies

**Files:**
- Create: `app/src/main/java/com/magikvlc/app/data/DuplicateScanPolicy.kt`
- Create: `app/src/test/java/com/magikvlc/app/data/DuplicateScanPolicyTest.kt`
- Create: `app/src/main/java/com/magikvlc/app/data/DuplicateScanReportStore.kt`
- Create: `app/src/test/java/com/magikvlc/app/data/DuplicateScanReportStoreTest.kt`

**Interfaces:**
- Produces `DuplicateScanPolicy.canStart(loading: Boolean, hasPermission: Boolean, videoCount: Int): Boolean`.
- Produces `DuplicateScanPolicy.shouldReplace(active: Boolean, requestedDeepScan: Boolean, activeDeepScan: Boolean): Boolean`.
- Produces a report store with `suspend fun save(workId: String, report: StoredDuplicateScanReport)` and `suspend fun load(workId: String): StoredDuplicateScanReport?` using app-private JSON files and atomic replace.
- The stored report DTO contains report counts/error, exact groups, probable groups, reasons, and serializable `VideoItem` metadata/URI strings; it is not added to WorkManager Data.

- [ ] **Step 1: Write failing policy tests** for loading, missing permission, zero videos, and valid ready/nonempty state; assert only the valid case starts.
- [ ] **Step 2: Run `DuplicateScanPolicyTest` and verify it fails** because `DuplicateScanPolicy` does not exist.
- [ ] **Step 3: Implement `DuplicateScanPolicy`** with the signatures above and a pure decision for replacing work when deep mode changes.
- [ ] **Step 4: Run `DuplicateScanPolicyTest` and verify it passes.**
- [ ] **Step 5: Write failing report-store round-trip tests** for exact/probable groups, URIs containing volume IDs, empty reports, errors, and work-ID isolation.
- [ ] **Step 6: Run `DuplicateScanReportStoreTest` and verify it fails** because the DTO/store do not exist.
- [ ] **Step 7: Implement serializable DTO mapping and atomic save/load** under `context.filesDir` or another non-evictable app-private directory; key every report by work ID.
- [ ] **Step 8: Run `DuplicateScanReportStoreTest` and verify it passes.**

### Task 2: WorkManager foreground scan worker

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/magikvlc/app/data/DuplicateScanWorker.kt`
- Create: `app/src/main/java/com/magikvlc/app/data/DuplicateScanNotifications.kt`
- Create: `app/src/main/java/com/magikvlc/app/data/DuplicateScanWorkRepository.kt`
- Create: `app/src/test/java/com/magikvlc/app/data/DuplicateScanWorkPolicyTest.kt`

**Interfaces:**
- Add WorkManager KTX version compatible with project minSdk 21; pin the selected version in Gradle.
- `DuplicateScanWorker` is a `CoroutineWorker`; input is only `deepScan: Boolean` and stable report/work key.
- `DuplicateScanWorkRepository.enqueue(deepScan: Boolean)` enqueues one unique work named `duplicate_scan`; active equivalent work is reused; a mode change replaces it.
- `DuplicateScanWorkRepository.cancel()` cancels the active unique work; `observe()` exposes unique work info/progress to UI.
- Worker loads a fresh snapshot with `VideosDataSource.load(applicationContext, VideoSort.NEWEST)`, calls `findDuplicates`, updates WorkManager progress with current phase/completed/total, saves final report by `id.toString()`, and returns only a small report key/count in output data.
- Notification channel has an ongoing title, phase/progress summary, and explicit cancel action; no video names/URIs appear in notification.

- [ ] **Step 1: Write failing pure work-policy tests** for keeping an equivalent active scan, replacing on basic/deep mode change, and explicit cancellation intent.
- [ ] **Step 2: Run `DuplicateScanWorkPolicyTest` and verify it fails** because policy/repository contract is absent.
- [ ] **Step 3: Add WorkManager KTX and required foreground permissions/merged-service type declarations** for supported Android versions; keep minSdk 21 unchanged.
- [ ] **Step 4: Implement notification channel and foreground info** with a stable notification ID, privacy-safe text, progress phase, and cancel PendingIntent.
- [ ] **Step 5: Implement the worker** to load MediaStore itself, reject/report an empty fresh snapshot distinctly, run the existing scanner, throttle progress publication, persist the report before returning success, and rethrow cancellation.
- [ ] **Step 6: Implement unique-work repository** with `KEEP` for the same active mode, `REPLACE` for changed mode, and explicit cancellation.
- [ ] **Step 7: Run `DuplicateScanWorkPolicyTest` and all compile/unit tests**; expected: pass with WorkManager integration compiling.

### Task 3: Connect the library UI to durable scan state

**Files:**
- Modify: `app/src/main/java/com/magikvlc/app/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/magikvlc/app/ui/library/LibraryLoadPolicy.kt` only if a pure readiness helper belongs there; otherwise keep readiness in `DuplicateScanPolicy`.
- Modify: `app/src/main/java/com/magikvlc/app/ui/about/AboutScreen.kt` only if diagnostics are copied/displayed through an existing log surface (no separate diagnostic screen).
- Test: `app/src/test/java/com/magikvlc/app/data/DuplicateScanPolicyTest.kt`

**Interfaces:**
- `LibraryScreen` remains the entry point and observes `DuplicateScanWorkRepository` rather than launching `findDuplicates` in `LaunchedEffect`.
- WorkInfo progress maps to existing `DuplicateScanProgress` (`PREPARING`, `QUICK_HASH`, `FULL_HASH`).
- On success, UI loads the report using the output work/report ID; while active, tab changes do not cancel it.
- User Cancel invokes repository cancellation. Changing the deep-scan switch replaces the active work and starts the selected mode after the library is ready.

- [ ] **Step 1: Add failing policy assertions** proving `loading=true`, no permission, or zero videos cannot enqueue; valid loaded/nonempty library can.
- [ ] **Step 2: Run the policy test and verify failure** for the UI-start cases not yet handled.
- [ ] **Step 3: Remove the Compose-owned scan coroutine** and gate automatic enqueue on `!loading && granted && videos.isNotEmpty()`; when loaded and empty, render an empty-library message without starting work.
- [ ] **Step 4: Collect WorkManager status/report** so switching away and back restores current progress or final report; do not key/cancel work to `tab` or composition disposal.
- [ ] **Step 5: Wire Cancel and mode change** to cancel/replace unique work; disable duplicate enqueue while an equivalent scan is active.
- [ ] **Step 6: Add scan diagnostics** at start (work ID, mode, source count), completion (scanned count, candidate runs, exact/probable groups, unreadable), cancellation, and error; do not log per item.
- [ ] **Step 7: Run focused tests and verify UI state transitions** in code-level tests or available instrumentation; expected: no scan before data ready, active scan reused across tab changes, completed report restored.

### Task 4: Runtime permission handling and final verification

**Files:**
- Modify: `app/src/main/java/com/magikvlc/app/ui/library/LibraryScreen.kt` or a focused notification-permission helper.
- Create: `app/src/test/java/com/magikvlc/app/data/NotificationPermissionPolicyTest.kt` if permission behavior can be isolated without Android framework calls.

**Interfaces:**
- Request `POST_NOTIFICATIONS` on API 33+ at the user-facing Duplicates entry point, not on app launch.
- Permission denial does not cancel or block the foreground worker; show a concise in-app message that Android may hide the notification from the drawer.
- On API <33, do not request runtime notification permission.

- [ ] **Step 1: Write failing pure permission-policy tests** for API <33, API 33+ granted, and API 33+ denied; denied must continue the scan and produce the visibility warning.
- [ ] **Step 2: Run the policy tests and verify failure.**
- [ ] **Step 3: Implement the runtime permission request and denial message** without making it a prerequisite for enqueue.
- [ ] **Step 4: Run `./gradlew.bat testDebugUnitTest --console=plain`**; expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Run `./gradlew.bat clean assembleDebug --console=plain`**; expected: `BUILD SUCCESSFUL` and fresh `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 6: Verify on-device:** open Duplicates before library load finishes; confirm no zero-video report; change tabs and minimize while scanning; confirm notification/progress when permitted; deny permission and confirm scan still proceeds with an in-app warning; return to Duplicates and confirm report; test explicit cancel and basic/deep replacement.

## Scope coverage self-check

- Empty-list race: Tasks 1 and 3.
- Background continuation and notification: Tasks 2 and 3.
- Durable result/report and no stale worker overwrite: Tasks 1–3.
- Permission denied behavior: Task 4.
- Existing SHA-256/probable behavior: Task 2 invokes existing scanner unchanged; regression suite in Task 4.
- Diagnostics and manual verification: Tasks 3–4.
