# AGENTS.md — Tanaw

Android app, Kotlin + Jetpack Compose. Single Gradle module (`:app`).
The app is already built. Work here is improving existing code and filling in
features that are documented but missing. Do not rewrite working code.

## Source of truth
- Feature docs:
  - Root markdown docs:
    - `project.md` — system architecture, user/profile/password flows, roadmap, and 27-table PostgreSQL schema reference
    - `progress.md` — senior developer production-readiness audit and feature scorecard
    - `MEMORIES.md` — project handoff blueprint and Phase A/B/C milestones
    - `MISSING_FEATURES_AUDIT.md` — driver-scoped technical audit, coverage matrix, and finding statuses (`U01`–`U03`, `F01`–`F13`, `S01`–`S10`, `E01`–`E08`)
    - `README.md` — build setup, Tanaw Dev backend verification, and driver login/recovery acceptance
    - `GOOGLE_AUTH_SETUP.md` — Google OAuth / Credential Manager setup and acceptance criteria
    - `TEXTBEE_SETUP.md` — TextBee SMS gateway & Supabase Send SMS hook integration status
    - `supabase/README.md` — shared backend contract (`mabutol-trucking-react`) and SQL verification gates
  - `docs/` — `FEATURES.md` (status table) and `SCAN-REVIEW.md` (the code-vs-doc review that produced the current statuses)
  - Caution: `project.md`, `progress.md` and `MEMORIES.md` also describe behaviour of the legacy screens (for example the `DriverNavigationScreen` countdown). Check that behaviour exists in `feature/workspace/` before trusting it.
- Feature status: `docs/FEATURES.md` (done / partial / missing / unsure). This file is the only copy.
  The old duplicate under `app/src/main/java/com/tanaw/app/docs/` no longer exists. Do not recreate it.
- Before implementing anything, find the matching doc section and `docs/FEATURES.md` entry.
  If the doc and the code disagree, stop and ask. Do not guess.
- Rows in `docs/FEATURES.md` marked **⚠ backend unverified** call RPCs/edge functions that are not defined in this repo. Do not assume they exist or behave as the client expects.

## Build & verify
Run these before declaring a task done:
- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Static analysis / formatter: None configured (no `ktlint`, `detekt`, or `spotless` plugin is present in Gradle). Custom read-only backend config verification tasks exist in `:app`: `./gradlew :app:verifyPhoneSignup` and `./gradlew :app:verifyDriverLogin`.
- Instrumented tests (needs emulator): `./gradlew connectedDebugAndroidTest`
- Build prerequisite: `app/build.gradle.kts` reads `client-config.properties` (project root) at configuration time, and the build fails if it is missing or its Supabase values do not match. `local.properties` supplies optional keys such as `MAPTILER_KEY`.
- These task names are standard AGP/Gradle tasks and have not been run as part of the docs review.

UI changes: add or update a `@Preview` where practical. Note that the active `feature/workspace/*` code currently has no previews (existing previews are in legacy screens), so there is no local pattern to copy.

## Project structure
Single module `:app`. Root project name: `Tanaw`.
Package root: `com.tanaw.app` (`app/src/main/java/com/tanaw/app/`)

**Active (reachable) code**
- `MainActivity.kt` — launches only `MobileWorkspaceApp`.
- `feature/workspace/` — the whole active app: `MobileWorkspaceApp`, `MobileWorkspaceViewModel`, `DriverWorkspace`, `DriverTripActions`, `DriverFlowDialogs`, `TripTrackingService`, `ShipmentMap`, `WorkspaceRecoveryStore`, `ProofDraftStore`, and helpers.
- Shared pieces used by the active flow: `feature/auth/LoginScreen.kt`, `feature/auth/CreateAccountScreen.kt`, `feature/auth/GoogleSignIn.kt`, `feature/auth/GoogleSignInButton.kt`, `feature/driver/components/AddTripExpenseSheet.kt`, `di/`, `ui/theme/`.
- `di/` — Dagger Hilt modules (`SupabaseModule`, `RepositoryModule`, `CoroutinesModule`) and `ProjectSessionManager`.
- `ui/theme/` — Material 3 theme (`Color.kt`, `Theme.kt`, `Type.kt`).
- Navigation: there is no `navigation/` package. Active navigation is state-driven inside `feature/workspace/MobileWorkspaceApp.kt` and `feature/workspace/DriverWorkspace.kt`.

**Legacy / unreachable code (compiled, never run)**
`TanawNavHost` and `Routes` in `MainActivity.kt` are never invoked. As a result, these are unreachable:
- `feature/auth/` — `Loginviewmodel`, `CreateAccountViewModel`, `OtpVerifyContent`, `VerifyEmailScreen`, `VerifyPhoneScreen`
- `feature/booking/`, `feature/history/`, `feature/notifications/`, `feature/profile/`, `feature/tracking/` — all files
- `feature/driver/` — all screens and ViewModels, plus `components/ReportIncidentSheet.kt` and `components/SignaturePad.kt`
- `data/repository/*Impl`, `data/dto/`, `data/mapper/`, `data/model/` (including `MockData`), `domain/` (models and repository interfaces) — used only by the legacy ViewModels
- `data/service/DriverLocationService.kt` — no callers, but still declared in the manifest
- `data/util/NetworkMonitor.kt` and `ui/components/OfflineBanner.kt` — zero usages
- Most of `ui/components/` — used by legacy screens
The authoritative per-feature list is the "Legacy / unreachable" section of `docs/FEATURES.md`.

Architecture of the active flow: a single large `MobileWorkspaceViewModel` talks directly to `SupabaseClient` (RPCs, Postgrest, Realtime, Storage) and local stores, holds a `JsonObject`-based state, and navigates by state (`page`). It is **not** per-screen UiState MVVM, and it does not use the repository interfaces. The per-screen `@HiltViewModel` + repository pattern exists only in the legacy code. Follow `feature/workspace/` when working on the live app.

## Legacy code rules
- Legacy screens are unreachable. **Do not edit them**, and do not extend, "fix" or build new features on them.
- Do not cite legacy files as proof that a feature is done. Evidence must come from code reachable from `MobileWorkspaceApp`.
- If a task needs behaviour that only exists in legacy code, port it into `feature/workspace/` as a separate, explicit task. Ask first.
- Do not delete legacy code as a side effect of another task. Removing it is its own task and needs approval.

## Backend rules
- The backend (Supabase migrations, RPCs, edge functions, RLS) lives in a **different repo**: `mabutol-trucking-react` (see `supabase/README.md`). This repo contains only a partial copy: `supabase/migrations` (`mobile_workspace`, `mobile_create_request`, `mobile_save_profile`, `mobile_shipment_details`, an early `mobile_driver_action`) and `supabase/functions/create-driver-user`.
- Do not add or change backend contracts here. If a feature needs a new or changed RPC, edge function, table or policy, stop and report it as a backend dependency.
- Do not invent RPC names or parameters. Check the call site in `MobileWorkspaceViewModel` and the list in `docs/FEATURES.md` ("Backend dependencies not in this repo").
- When a task touches a **⚠ backend unverified** feature, say in the report that the server side was not verified.

## Gradle rules (important)
- Repositories are declared ONLY in `settings.gradle.kts`
  (`RepositoriesMode.FAIL_ON_PROJECT_REPOS`). Never add `repositories {}` to
  any `build.gradle.kts`.
- Do not add or upgrade dependencies, plugins, or the Kotlin/AGP/Compose
  versions without asking first. Version catalog (`gradle/libs.versions.toml`):
  **Yes** — it is used. New dependencies go through the catalog; do not hardcode versions in build files.
- Known existing exceptions (do not copy them, and do not fix them as a side effect): `app/build.gradle.kts` hardcodes `androidx.credentials:credentials:1.6.0`, `credentials-play-services-auth:1.6.0`, `googleid:1.2.1` and `desugar_jdk_libs:2.1.5`, and declares `storage-kt` and `realtime-kt` by raw coordinates although the catalog has entries. The catalog's `desugarJdkLibs` is 2.1.4 and unused, so it disagrees with the 2.1.5 in the build file. Resolve this only as its own approved task.
- Do not hardcode JDK paths. JDK toolchains are resolved by the foojay plugin (`settings.gradle.kts`; `gradle/gradle-daemon-jvm.properties` pins the daemon JVM).
- Do not edit generated files or anything under `build/`.

## Compose conventions
These are the targets for new code. Existing active code does not fully follow them, so do not refactor it just to comply.
- Stateless composables where possible: state in, events out (state hoisting).
- Screen state lives in a ViewModel, exposed as `StateFlow`. The active workspace code uses `collectAsState()`, so match the surrounding file. Use `collectAsStateWithLifecycle()` in new files that have no existing pattern.
- Use the existing theme (colors, typography, spacing) for new code. The active driver UI currently hardcodes `Color(0xFF…)` values (for example `DriverNavy`) and `dp` values. Reuse those existing constants when editing that file, and do not add new raw colors if a token exists.
- User-facing text for new code goes in `res/values/strings.xml`. Existing workspace text is mostly hardcoded in Kotlin (`strings.xml` has only 19 entries, used by legacy screens). Match the file you are editing, and do not mass-extract strings in an unrelated diff.
- Give composables a `modifier: Modifier = Modifier` parameter.

## Working rules
- One feature or bug per task. Keep diffs small and focused.
- Match existing naming, formatting, and patterns. Reuse existing components.
- No unrelated refactors or formatting changes in the same diff.
- Add or update unit tests for new logic. Existing unit tests cover helper objects and legacy ViewModels, and nothing covers `MobileWorkspaceViewModel`, so extract testable logic into helper objects as the existing code does.
- When done, report: files changed, how it was verified, and anything left unfinished.
- Do not commit to `main`. Work on a feature branch. (The current branch was not checked.)
- When a feature's status changes, update `docs/FEATURES.md` in the same branch.
- Product rule: drivers never accept or decline assignments. Staff/admin assign them. Drivers only view assigned shipment details and perform trip actions.
- Backend lives in a separate repo owned by someone else. Do not modify it from this repo’s sessions. If a feature needs a backend change, write the proposed change (SQL, RPC signature, affected columns) in docs/BACKEND-REQUESTS.md and stop.