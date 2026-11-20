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
Commands to run when explicitly asked to build or verify (see guardrails below):
- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Static analysis / formatter: None configured (no `ktlint`, `detekt`, or `spotless` plugin is present in Gradle). Custom read-only backend config verification tasks exist in `:app`: `./gradlew :app:verifyPhoneSignup` and `./gradlew :app:verifyDriverLogin`.
- Instrumented tests (needs emulator): `./gradlew connectedDebugAndroidTest`
- Build prerequisite: `app/build.gradle.kts` reads `client-config.properties` (project root) at configuration time, and the build fails if it is missing or its Supabase values do not match. `local.properties` supplies optional keys such as `MAPTILER_KEY`.
- These task names are standard AGP/Gradle tasks and have not been run as part of the docs review.

UI changes: add or update a `@Preview` where practical, matching the existing Light/Dark preview conventions in `feature/workspace/` and `feature/auth/`.

## Build, Gradle & Parallel Agent Guardrails

Before modifying files or running terminal commands in this repository, strictly follow these rules to prevent file conflicts and Windows Kotlin daemon cache locks:

1. **No Unsolicited Gradle Builds:** Do NOT run `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, or any other Gradle build task automatically after editing code unless the user explicitly asks you to build or verify compilation in this turn. Another agent thread may already be building in `app/build/`.
2. **Pre-Build Process Check (Windows PowerShell):** When explicitly asked to run a Gradle build or test:
   - First check if an active Gradle build is already running (`Get-Process -Name "java","javaw" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match "GradleWorkerMain|GradleDaemon" }`). If a build is actively running, wait or notify the user instead of launching a competing build.
   - Omit `--no-daemon` so Gradle reuses the existing warm daemon instead of spawning conflicting processes.
3. **Kotlin Daemon Lock Recovery:** If compilation hits `Storage for [...] is already registered` or `Could not close incremental caches` under `app\build\kotlin\`:
   - Do not loop on the same command. Run `./gradlew --stop` once, or re-run with `-Dkotlin.compiler.execution.strategy=in-process`.
4. **Strict File Scope:** Only touch files directly required by your current task. Never reformat or refactor unrelated screens or ViewModels that might be open in a parallel agent session.

## Project structure
Single module `:app`. Root project name: `Tanaw`.
Package root: `com.tanaw.app` (`app/src/main/java/com/tanaw/app/`)

**Active (single codebase path)**
- `MainActivity.kt` — launches only `MobileWorkspaceApp` (and routes `tanaw://auth/callback` deep links).
- `core/` — shared utilities and rules: `deeplink/DeepLinkParser`, `error/` (`MobileAuthErrors`, `MobileErrorClassifier`), `network/` (`WorkspaceAutoRefreshRules`, `WorkspaceNetworkMonitor`), `sync/WorkspaceSyncRules`, `validation/` (`ConfirmedMutation`, `MobileValidation`).
- `feature/workspace/` — the whole active app (`screens/MobileWorkspaceApp`, `viewmodel/MobileWorkspaceViewModel`, `state/MobileState`, `model/RoutePreview`, `rules/`, `store/`, `tracking/`, `customer/`, and `driver/` including `driver/components/AddTripExpenseSheet.kt` and `AllowanceRow.kt`).
- `feature/auth/` — active auth UI, dialogs, and policies (`screens/LoginScreen.kt`, `screens/CreateAccountScreen.kt`, `screens/RecoveryScreens.kt`, `components/GoogleSignIn.kt`, `components/GoogleSignInButton.kt`, `dialogs/PasswordChangeDialog.kt`, `dialogs/PhoneChangeDialog.kt`, `rules/PasswordChangePolicy.kt`, `rules/PhoneChangePolicy.kt`).
- `di/` — Dagger Hilt `SupabaseModule` and `ProjectSessionManager`.
- `ui/components/` & `ui/theme/` — shared map/design components (`ShipmentMap.kt`, `TanawComponents.kt`, `WorkspaceDesign.kt`) and Material 3 theme (`Color.kt`, `Theme.kt`, `Type.kt`).
- Navigation: there is no `navigation/` package. Active navigation is state-driven inside `feature/workspace/screens/MobileWorkspaceApp.kt` and `feature/workspace/driver/screens/DriverWorkspace.kt`.

**Removed legacy code**
All previously dormant `TanawNavHost` / `Repository` code (`data/`, `domain/`, `feature/booking/`, `feature/driver/`, `feature/history/`, `feature/notifications/`, `feature/profile/`, `feature/tracking/`, legacy `feature/auth/` ViewModels/screens, `di/RepositoryModule`, `di/CoroutinesModule`, `DriverLocationService`, and unused `ui/components/`) has been removed. Only the unwired `"verify"` email OTP branch in `MobileWorkspaceApp` and `MobileWorkspaceViewModel` remains intentionally preserved.

Architecture of the active flow: a single large `MobileWorkspaceViewModel` talks directly to `SupabaseClient` (RPCs, Postgrest, Realtime, Storage) and local stores, holds a `JsonObject`-based state, and navigates by state (`page`). It is **not** per-screen UiState MVVM, and does not use repository interfaces. Follow `feature/workspace/` when working on the app.

## Historical doc caution
- `progress.md` and `MEMORIES.md` still mention removed legacy screens for historical context. Always verify behaviour against `feature/workspace/` and `docs/FEATURES.md` before trusting legacy descriptions.
- Do not reintroduce per-screen ViewModel/repository layers unless explicitly approved as a separate architecture task.

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
- Known existing exceptions (do not copy them, and do not fix them as a side effect): `app/build.gradle.kts` hardcodes `androidx.credentials:credentials:1.6.0`, `credentials-play-services-auth:1.6.0`, `googleid:1.2.1` and `desugar_jdk_libs:2.1.5`, and declares `storage-kt` and `realtime-kt` by raw coordinates although the catalog has entries. Resolve this only as its own approved task.
- Do not hardcode JDK paths. JDK toolchains are resolved by the foojay plugin (`settings.gradle.kts`; `gradle/gradle-daemon-jvm.properties` pins the daemon JVM).
- Do not edit generated files or anything under `build/`.

## Compose conventions
These are the targets for new code. Existing active code does not fully follow them, so do not refactor it just to comply.
- Stateless composables where possible: state in, events out (state hoisting).
- Screen state lives in a ViewModel, exposed as `StateFlow`. The active workspace code uses `collectAsState()`, so match the surrounding file. Use `collectAsStateWithLifecycle()` in new files that have no existing pattern.
- Use the existing theme (colors, typography, spacing) for new code. The active driver UI currently hardcodes `Color(0xFF…)` values (for example `DriverNavy`) and `dp` values. Reuse those existing constants when editing that file, and do not add new raw colors if a token exists.
- User-facing text for new code goes in `res/values/strings.xml`. Existing workspace text mixes hardcoded strings in Kotlin and `strings.xml` entries (used primarily by customer booking screens). Match the file you are editing, and do not mass-extract strings in an unrelated diff.
- Give composables a `modifier: Modifier = Modifier` parameter.

## Working rules
- One feature or bug per task. Keep diffs small and focused.
- Match existing naming, formatting, and patterns. Reuse existing components.
- No unrelated refactors or formatting changes in the same diff.
- Add or update unit tests for new logic. Existing unit tests cover helper objects, rules, and presentation formatters in `core/`, `feature/auth/`, and `feature/workspace/`; nothing covers `MobileWorkspaceViewModel` directly, so extract testable logic into helper objects as the existing code does.
- When done, report: files changed, how it was verified, and anything left unfinished.
- Do not commit to `main`. Work on a feature branch. (The current branch was not checked.)
- When a feature's status changes, update `docs/FEATURES.md` in the same branch.
- Product rule: drivers never accept or decline assignments. Staff/admin assign them. Drivers only view assigned shipment details and perform trip actions.
- Backend lives in a separate repo owned by someone else. Do not modify it from this repo’s sessions. If a feature needs a backend change, write the proposed change (SQL, RPC signature, affected columns) in docs/BACKEND-REQUESTS.md and stop.