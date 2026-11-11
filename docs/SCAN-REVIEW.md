# SCAN-REVIEW — `docs/FEATURES.md` vs. actual code

Scope: static review only. Nothing was built, run or tested, and no device was used.
Edits: none to `FEATURES.md`, `Features.md`, `AGENTS.md` or any source file. This file is the only addition.

Paths are relative to `app/src/main/java/com/tanaw/app/` unless they start with `docs/`, `supabase/`, `app/` or are root files.

## 0. Cross-cutting findings (they affect many rows)

### 0.1 `TanawNavHost` is never invoked, so the legacy flow is dead code
- `MainActivity.kt` calls only `setContent { TanawTheme { MobileWorkspaceApp(hiltViewModel()) } }`.
- `find_usages` on `TanawNavHost` returned only documentation mentions.
- Dead, as a consequence: every screen and ViewModel in `feature/{auth (Verify*, Loginviewmodel, CreateAccountViewModel), booking, driver/*Screen, driver/components/ReportIncidentSheet, SignaturePad, history, notifications, profile, tracking}`.
- `feature/driver/components/AddTripExpenseSheet.kt` is the exception. It is reused by the active `feature/workspace/DriverWorkspace.kt` (about line 248).
- `FEATURES.md` lists many of these dead files as evidence under "done" rows. The files exist, but they prove nothing about the shipped app.
- The intro note in `FEATURES.md` says "both paths are referenced". It does not say that one path is unreachable.
- Other dead or unwired pieces:
  - `data/service/DriverLocationService.kt` has no callers but is still declared in the manifest.
  - `data/util/NetworkMonitor.kt` and `ui/components/OfflineBanner.kt` have zero usages.
  - `MockAuthRepositoryImpl` is not bound. `di/RepositoryModule` binds the Supabase implementations.
  - `data/model/MockData.kt` is used only by dead screens and by `TanawNavHost`.
  - `SupabaseDriverRepositoryImpl` belongs to the dead repository-based flow.

### 0.2 Backend pieces are not in this repo (client logic is real, server side not verifiable here)
- `supabase/migrations` contains only `mobile_workspace`, `mobile_create_request`, `mobile_save_profile`, an early `mobile_driver_action` wrapper and `mobile_shipment_details`.
- Edge functions: only `supabase/functions/create-driver-user` exists.
- Not defined here: `driver_arrive_at_pickup`, `driver_submit_pod`, `driver_report_incident_evidence`, `driver_record_position`, `mobile_driver_history`, `mobile_driver_log_fuel`, `mobile_driver_evidence`, `delivery_conversation`, `send_delivery_message`.
- Also not defined here: edge functions `mobile-email-login`, `delivery-routing`, `calculate-route`.
- `supabase/README.md` says the canonical migrations live in the `mabutol-trucking-react` repo, so this is probably intentional.
- I could not confirm these RPCs and functions exist or behave correctly. Rows that depend on them are "client verified, backend unverified".

### 0.3 Test coverage
- `src/test` has unit tests for helper objects and the legacy ViewModels.
- A grep for `MobileWorkspaceViewModel`, `TripTrackingService`, `DriverTripActions`, `DriverFlowDialogs`, `ShipmentMap` and `MobileWorkspaceApp` in `src/test` returned nothing.
- `src/androidTest` has many UI tests. I did not inspect them, so I make no claim about what they cover.

---

## 1. Rows I disagree with

### #3 — Customer account creation & SMS / Email OTP verification
- FEATURES.md: **done**. I believe **partial**.
- Evidence:
  - `feature/workspace/MobileWorkspaceViewModel.kt`: `signup()` leads only to the `verify_phone` page.
  - `feature/workspace/MobileWorkspaceApp.kt`: the email verify branch (`page == "verify"`, `verifySignup`, `resend`) exists, but nothing in the active code sets `page = "verify"`.
  - `feature/auth/VerifyEmailScreen.kt`, `VerifyPhoneScreen.kt` and `OtpVerifyContent.kt` are only reachable via the dead `TanawNavHost`.
  - The SMS path is real. Live delivery depends on the external TextBee gateway.
- So the email OTP is unreachable in the active app.
- Confidence: **high** (reachability), **medium** on whether this is intentional (docs do not say).

### #11 — Customer active shipment tracking, history filtering & real-time map position
- FEATURES.md: **done**. I believe **partial**, or done with a caveat.
- Evidence:
  - `feature/workspace/ShipmentMap.kt`: the marker icon is `if (label == "Destination") Destination else Pickup`.
  - The "Last recorded vehicle position" marker therefore renders as the red Pickup pin, not a distinct vehicle marker.
  - `MobileWorkspaceViewModel.kt`: realtime (`startLive`) only triggers a refetch through `backgroundRefresh` → `readDetails`. It does not push positions directly to the map.
  - `feature/tracking/*` and `feature/history/*` cited in the notes are dead.
- Confidence: **medium**. The behaviour is real but the visual ambiguity is a judgement call.

### #17 — Driver route navigation, HGV route status, live ETA, Google Maps links
- FEATURES.md: **done**. I believe **done, dependent on external backend**. Treat it as **unsure** for the ETA and route part.
- Evidence:
  - `feature/workspace/DriverDirections.kt`: navigation is only an external Google Maps URL. There is no in-app turn-by-turn.
  - `feature/workspace/DriverRouteStatus.kt` (`eta`): ETA shows only when `route_verified` is true and GPS is under 120 s old. Otherwise it shows "ETA unavailable/expired".
  - Route data depends on the `delivery-routing` and `calculate-route` edge functions, which are not in this repo.
  - `feature/driver/DriverNavigationScreen.kt` and `DriverNavigationViewModel.kt` cited in the notes are dead.
  - `MEMORIES.md` describes a 15-minute countdown in `DriverNavigationScreen`. In the active code the grace timer in `DriverTripActions.kt` is a text label plus a 1 s tick that gates the no-show button.
- Confidence: **medium**.

### #8 — Customer map location picker, MapTiler geocoding & road route preview
- FEATURES.md: **done**. I believe **done, with a configuration caveat**.
- Evidence:
  - `ShipmentMap.kt` (`MobileGeocoding.search`) needs `MAPTILER_KEY` from `local.properties`. If blank it throws and the UI shows "Address search is unavailable".
  - The map falls back to OpenFreeMap. Reverse geocoding falls back to the Android `Geocoder`.
  - Route preview depends on the `calculate-route` edge function, which is not in this repo.
  - `feature/booking/LocationPickerScreen.kt`, `LocationPickerViewModel.kt` and `SupabaseLocationRepositoryImpl.kt` are dead.
- Confidence: **medium**.

### #1 — Phone/password & email/password login (+ contact-email alias fallback)
- FEATURES.md: **done**. I believe **done**, but the notes cite dead files.
- Evidence:
  - Active login is `MobileWorkspaceViewModel.login`, and `loginWithContactEmail` calls the `mobile-email-login` edge function, which is not in this repo.
  - `feature/auth/Loginviewmodel.kt` and `data/repository/SupabaseAuthRepositoryImpl.kt` (cited) are not on the active path. I did not read `SupabaseAuthRepositoryImpl`.
  - `feature/auth/LoginScreen.kt` is used by the active flow.
- Confidence: **medium**.

### #19 — Driver foreground GPS tracking service & SQLite queue
- FEATURES.md: **done**. I agree with the status. The note is wrong.
- Evidence:
  - `data/service/DriverLocationService.kt` (cited as "legacy FusedLocation service") has no callers.
  - The active service is `feature/workspace/TripTrackingService.kt`. It uses `LocationManager.requestLocationUpdates` every 15 s and the SQLite `TrackingQueue`. It is `START_NOT_STICKY`, so tracking is resumed manually.
  - Physical-device acceptance is still open (audit E07).
- Confidence: **high**.

### #22 — Driver incident reporting
- FEATURES.md: **done**. I agree with the status. The note cites a dead file.
- Evidence:
  - The active implementation is `DriverFlowDialogs.kt` (`DriverIncidentForm`), `IncidentRequest.kt` and `DriverEvidence.kt`.
  - `feature/driver/components/ReportIncidentSheet.kt` is dead.
- Confidence: **high**.

### #27 — Offline connectivity detection & encrypted offline cache
- FEATURES.md: **partial**. I agree with **partial**. The note is misleading.
- Evidence:
  - The note says "Network callback observer and banner exist". `NetworkMonitor.kt` and `OfflineBanner.kt` exist but have zero usages. Connectivity detection is not wired in.
  - Active offline handling is the `state.offline` flag, set when calls throw.
  - The cache covers driver snapshots only (`RecoveryPolicy.workspace` requires role `driver`).
- Confidence: **high**.

### Rows with correct status but dead-file evidence in the notes
These are #5 (`ProfileScreen`), #7 (`ProfileScreen`, `ProfileViewModel`, `SupabaseUserRepositoryImpl`), #9 (`feature/booking/*`, `SupabaseBookingsRepositoryImpl`), #12 (`PODScreen`), #13 (`NotificationsScreen`, `NotificationsViewModel`), #14 (`DriverHomeScreen`, `DriverHomeViewModel`), #18 (`SupabaseDriverRepositoryImpl`), #20 (`SubmitPod*`, `DeliveryCompleteScreen`, `SignaturePad`), #21 (`SupabaseDriverRepositoryImpl`), #25 (`MyTrips*`), #26 (`DriverProfileScreen`, `DriverProfileViewModel`).
- Status: I agree with **done** for all of them. The active workspace code exists and has real logic.
- Confidence: **high** that the cited files are dead, **medium–high** that the active logic is complete.
- Rows #9, #18, #20, #21, #23, #24, #25 and #26 also depend on RPCs missing from this repo (see 0.2).

### #28 — Background refresh error classification & off-UI-thread image encoding
- FEATURES.md: **partial**. I did not check this row as thoroughly as the others.
- I read `MobileWorkspaceViewModel.kt`, `DriverFlowDialogs.kt` and `DriverEvidence.kt`. I did not re-trace the specific claims (all non-cancellation exceptions set `offline = true`; bitmap compression on the caller thread).
- Confidence: **low** that the details are exact. The status looks plausible.

---

## 2. Rows I checked and agree with

| # | Status | What I checked | Confidence |
|---|--------|----------------|-----------|
| 2 | partial | `GoogleSignIn.kt` and the view model code exist. `GOOGLE_WEB_CLIENT_ID` is optional in `app/build.gradle.kts`. | medium |
| 4 | done | `MainActivity`, `MobileWorkspaceApp`, `ProjectSessionManager`, `SupabaseModule` | high |
| 6 | partial | `AndroidManifest.xml` has only a MAIN/LAUNCHER filter. | high |
| 10 | partial | The active flow submits "Awaiting office quotation". `AddDetailsScreen` uses `MockData` and is dead. | high |
| 15 | unsure | The code has no driver accept/decline. The source DOCX is unavailable per `MISSING_FEATURES_AUDIT.md`. | high |
| 16 | unsure | No return-load planning in the code. Same DOCX caveat. | high |
| 23 | done | `DriverFuelForm`, `DriverOperationalRecords`, `logFuel`. Receipt photo is noted as unavailable. Backend RPC unverified. | medium–high |
| 24 | done | `DriverConversation`, `DriverWorkspace` Messages tab, `sendConversation`. Backend RPC unverified. | medium–high |
| 29 | missing | `MobileWorkspaceApp.kt` Help text confirms it. | high |

I read the active code for #5, #7, #12, #13, #14, #20, #21, #25 and #26 end to end. The status is fine on the client side.

---

## 3. Documented features missing from FEATURES.md
Sources: `MISSING_FEATURES_AUDIT.md`, `README.md`, `TEXTBEE_SETUP.md`, `project.md`.

| Candidate | Where documented | Assessment |
|-----------|------------------|-----------|
| Login Terms/Privacy panels (U01) | `MISSING_FEATURES_AUDIT.md` | Implemented as `InfoDialog` in `MobileWorkspaceApp`. Genuine gap in FEATURES.md. |
| Driver Help & Support panel (F12) | audit | Only partly covered by #26. |
| Customer "complete your profile" gate when the phone is missing | `MobileWorkspaceApp.kt` | Real behaviour, not documented as a row. |
| Driver logout cleanup (recovery store, tracking queue, proof drafts) | `MobileWorkspaceViewModel.logout` | Real behaviour, no row. |
| TextBee shipment SMS opt-in/out | `TEXTBEE_SETUP.md` | Mentioned in #7 only. Could be its own row. |
| Selected-trip-unavailable recovery (E04) | audit | Partly in #14. |
| Location provider failure handling (E06) | audit | Partly in #19 (`TrackingSafety`). |
| Gradle tasks `verifyPhoneSignup` and `verifyDriverLogin` | `README.md`, `app/build.gradle.kts` | Exist. They are tooling, not app features. |
| `project.md` roadmap: Maps integration, DB pricing integration, live GPS via `tracking_logs` | `project.md` | Overlaps with #8, #10, #19. Not stated as separate rows. |

`progress.md`, `project.md` and `MEMORIES.md` also describe behaviour from the dead flow, for example the `DriverNavigationScreen` countdown. I did not map those to rows.

---

## 4. AGENTS.md claims that do not match the project

| Claim in AGENTS.md | Reality | Confidence |
|--------------------|---------|-----------|
| A note that no root `docs/` directory existed before | `docs/` now exists (it holds `FEATURES.md` and this file), so the wording is stale. | high |
| (Listing of) `app/src/main/java/com/tanaw/app/docs/Features.md` | A duplicate of `FEATURES.md` sits inside the source tree, which is an odd location. | high |
| Structure lists `NetworkMonitor`, `OfflineBanner` and `DriverLocationService` as live components | All three are unused or unreferenced (see 0.1). | high |
| "MVVM, ViewModel + StateFlow" architecture | The active flow is one large `MobileWorkspaceViewModel` with a `JsonObject` state and state-driven navigation. Per-screen UiState ViewModels exist only in dead code. | high |
| "Collect with `collectAsStateWithLifecycle()`" | Only dead screens use it. Active code uses `collectAsState()`. | high |
| "User-facing text goes in `strings.xml`" | `res/values/strings.xml` has 19 strings, used by the dead driver home. Active workspace code hardcodes almost all text. | high |
| "Use theme tokens, no hardcoded colors or dp" | The active driver UI hardcodes `Color(0xFF…)` (for example `DriverNavy`) and `dp` values. | high |
| "If a task changes UI, add or update a `@Preview`" | Previews exist only in `LoginScreen`, `DriverHomePreviews`, `DriverProfileScreen`, `MyTripsScreen` and `BookingsPreviews`. `feature/workspace/*` has none. | high |
| "Do not hardcode versions in build files" | `app/build.gradle.kts` hardcodes `androidx.credentials:credentials:1.6.0`, `credentials-play-services-auth:1.6.0`, `googleid:1.2.1` and `desugar_jdk_libs:2.1.5`. `storage-kt` and `realtime-kt` use raw coordinates although the catalog has entries. The catalog's `desugarJdkLibs` is 2.1.4 and unused, so the two versions disagree. | high |
| Build/test/lint commands | `assembleDebug`, `testDebugUnitTest`, `lintDebug` and `connectedDebugAndroidTest` are standard AGP tasks. I did not run them, so they are **unverified**. | n/a |
| Build prerequisites (not in AGENTS.md) | `app/build.gradle.kts` reads `client-config.properties` at configuration time. The root file exists. Worth documenting. | medium |

Claims that match: package root `com.tanaw.app`, single `:app` module, version catalog present (AGP 9.4.0, Kotlin 2.4.10), repositories only in `settings.gradle.kts` with `FAIL_ON_PROJECT_REPOS` (no `repositories {}` in root or app build files), foojay resolver plugin 1.0.0 applied, no hardcoded JDK paths, no ktlint/detekt/spotless configured, Hilt used for DI.

Unchecked or unsure:
- Branch rule: "Do not commit to main". I did not check the git branch.
- Root files `hs_err_pid11776.log` and `replay_pid11776.log` exist. `.gitignore` does not list them. Root `/build` is ignored, but I found no rule for `app/build`; an `app/.gitignore` may cover it, which I did not check. Unsure.
- Whether `client-config.properties` contains secrets and is ignored: not checked.

---

## 5. What I checked
- Read in full or near full: `MainActivity`, `MobileWorkspaceApp`, `MobileWorkspaceViewModel`, `DriverWorkspace`, `DriverNavigateOverview`, `DriverTripActions`, `DriverFlowDialogs`, `DriverProfile`, `TripTrackingService`, `ShipmentMap`, `DriverConversation`, `DriverRecordPresentation`, `DriverFuelForm`, `DriverEvidence`, `DriverRouteStatus`, `DriverOperationalRecords`, `WorkspaceRecoveryStore`, `ProofDraftStore`, `PasswordChangeDialog`, `DriverProfileRules`, `ConfirmedMutation`, `DriverDirections`, `ProofSubmission`, `ExpenseSubmission`, `IncidentRequest`, `TrackingSafety`, `AddTripExpenseSheet`, `RepositoryModule`, `SupabaseModule`, `CoroutinesModule`, `AndroidManifest.xml`, `strings.xml`, `settings.gradle.kts`, `gradle/libs.versions.toml`, root and app `build.gradle.kts`, `.gitignore`.
- Docs: `README.md`, `project.md`, `progress.md`, `MEMORIES.md`, `MISSING_FEATURES_AUDIT.md`, `GOOGLE_AUTH_SETUP.md`, `TEXTBEE_SETUP.md`, `supabase/README.md`.
- Searches: usages of `TanawNavHost`, `NetworkMonitor`, `OfflineBanner`, `DriverLocationService`, `MockData` and `MockAuthRepositoryImpl`; RPC names in `supabase/migrations`; `@Preview` and `collectAsState*` usage; `src/test` references.
- **Not read:** `SupabaseAuthRepositoryImpl`, `Loginviewmodel`, the other `Supabase*RepositoryImpl` files, the legacy ViewModels, the bodies of `NetworkMonitor`, `OfflineBanner` and `DriverLocationService`, `MobileValidation`, `ProofImage`, `RoutePreview`, `DriverDatabaseData`, `BookingSchedule`, `MobileBookingRules`, and everything under `androidTest`.
- **Not done:** no Gradle build, unit test, lint or device run.
