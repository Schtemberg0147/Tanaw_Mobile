# Wave 1 Implementation Plan: Tanaw Mobile

**Scope:** Wave 1 — Client-Only, Low-Risk Reliability & UX Improvements  
**Target Branch:** `JBranch`  
**Active Working Root:** `app/src/main/java/com/tanaw/app/feature/workspace/`  
**Last Updated:** 2026-11-05

---

## 1. Global Working Rules for All Agents

> [!IMPORTANT]
> ### Architectural & Codebase Rules
> 1. **Active Path Only:** All executable app code is located under `app/src/main/java/com/tanaw/app/feature/workspace/` and its shared dependencies (`feature/auth/{LoginScreen,CreateAccountScreen,GoogleSignIn}.kt`, `feature/driver/components/AddTripExpenseSheet.kt`, `di/`, `ui/theme/`).
> 2. **Strict Legacy Screen Quarantine:** Legacy screens (`TanawNavHost`, `feature/booking/*`, `feature/history/*`, `feature/notifications/*`, `feature/profile/*`, `feature/tracking/*`, `feature/driver/Delivery*`) are compiled but unreachable. **Never edit, extend, or cite legacy screens.**
> 3. **Single Responsibility Branches:** Each Wave 1 backlog item is developed on an isolated feature branch named by its ID (e.g., `feat/C02-profile-save-confirmation`), approved, unit-tested, and fast-forward/squash merged into `JBranch`.
> 4. **No Backend Inventions:** Supabase migrations and canonical RPCs reside in the external repository `mabutol-trucking-react`. Never invent or alter RPC contracts on the client; all mutations must handle offline/transport failures idempotently.
> 5. **Quality Gate:** Before declaring any item complete:
>    - Unit tests must pass: `./gradlew testDebugUnitTest`
>    - Debug compilation must succeed: `./gradlew assembleDebug`
>    - Documentation sync: update `docs/BACKLOG.md` and `docs/FEATURES.md`.

---

## 2. Wave 1 Progress Scorecard

| ID | Module / Feature Item | Type | FEATURES Row | Status | Notes / Next Step |
|:---|:----------------------|:----:|:------------:|:------:|:------------------|
| **D04** | Process photos, receipts & signatures off UI thread | Driver Workspace | #28 | **Done (just need testing)** | Merged into `JBranch`. Needs physical device bitmap/memory check. |
| **D03** | Separate network failures from auth/backend errors + Auto-refresh | Workspace Core | #28 | **Done (just need testing)** | Merged into `JBranch`. Needs physical airplane mode / reconnect test. |
| **C02** | Show profile-save success even if following refresh fails | Customer Profile | #7 | **Done (just need testing)** | Merged into `JBranch`. Unit-tested against refresh exceptions. |
| **D02** | Durable drafts for incident & expense forms (incl. attachments) | Driver Operations | #20, #21, #22 | **Pending** | Ready for implementation on `feat/D02-durable-incident-expense-drafts`. |
| **C04** | Restore booking drafts & uncertain submissions after cold restart | Customer Booking | #9 | **Pending** | Ready for implementation on `feat/C04-booking-recovery-store`. |
| **C07** | Keep signed-in password change inside customer Profile | Profile / Auth | #5 | **Pending** | Ready for implementation on `feat/C07-customer-profile-password-change`. |

---

## 3. Module Details & Implementation Instructions

---

### Module D04: Off-UI-Thread Image & Signature Processing
* **Status:** `Done (just need testing)`
* **Branch:** `feat/D04-image-off-ui-thread` (merged to `JBranch`)
* **Reference Docs:** `docs/BACKLOG.md` (#28), `docs/FEATURES.md` (#28)

#### Implementation Summary
- `ProofImage.kt`: Added `decodeAsync`, `processCameraPhoto` (bounds checking up to 20MB in, 5MB out on `Dispatchers.IO`/`Default`), and `renderSignaturePng` (1000×400 canvas line rasterization on `Dispatchers.Default` with safe `finally { bitmap.recycle() }`).
- `DriverEvidence.kt`: Added `readPhotoAsync`, `readReceiptAsync`, and `jpegAsync` off the main thread. Decoupled incident thumbnail rendering into `IncidentPhotoThumbnail` with `LaunchedEffect` + `DisposableEffect` bitmap recycling.
- `DriverFlowDialogs.kt`: Form submission and camera capture run within coroutine scopes; added `processing` state with progress bars and button disabling.
- `AddTripExpenseSheet.kt`: Background receipt file stream read and thumbnail generation.
- `MobileWorkspaceViewModel.kt`: `addExpense` transcode uses `DriverEvidence.jpegAsync(it)`.
- **Testing Required:** Physical phone or emulator run: capture a photo and draw a signature; verify smooth 60fps rendering without UI freezes or OOMs.

---

### Module D03: Background Error Classification & Auto-Refresh
* **Status:** `Done (just need testing)`
* **Branch:** `feat/D03-error-classification` (merged to `JBranch`)
* **Reference Docs:** `docs/BACKLOG.md` (#28), `docs/FEATURES.md` (#28), `MISSING_FEATURES_AUDIT.md` (E05)

#### Implementation Summary
- `MobileErrorClassifier.kt`: Pure-Kotlin classifier isolating `NETWORK` (socket/host timeouts, connection aborts), `AUTH` (401/403, JWT expiration, inactive account), `CONTRACT` (SerializationException, 500/502/503), and `VALIDATION` errors with sanitized friendly messages.
- `WorkspaceNetworkMonitor.kt`: Reactive network state flow leveraging `ConnectivityManager.NetworkCallback` with `NET_CAPABILITY_INTERNET` and `NET_CAPABILITY_VALIDATED`.
- `MobileWorkspaceViewModel.kt`:
  - `backgroundRefresh()` and `refresh()` only set `state.offline = true` on genuine transport failures.
  - Reactive reconnection listener: when `isOnline` transitions to `true` and the workspace is currently `offline`, it automatically triggers a debounced refresh without requiring manual user button clicks.
  - Manual refresh fallback remains intact.
- **Testing Required:** Physical phone or emulator run: toggle Airplane Mode ON -> verify offline notice; toggle Airplane Mode OFF -> verify app automatically reconnects and dismisses offline state.

---

### Module C02: Profile-Save Confirmation Resilience
* **Status:** `Done (just need testing)`
* **Branch:** `feat/C02-profile-save-confirmation` (merged to `JBranch`)
* **Reference Docs:** `docs/BACKLOG.md` (#7), `docs/FEATURES.md` (#7)

#### Problem & Root Cause
In `MobileWorkspaceViewModel.kt` (`profile` function around line 500):
```kotlin
fun profile(name: String, phone: String, address: String, city: String) = operation {
    val normalized = MobileValidation.phone(phone) ?: error("INVALID_CONTACT")
    client.postgrest.rpc("mobile_save_profile", buildJsonObject { ... })
    load() // <--- If this reload throws a network blip or timeout, the whole operation fails!
    mutable.update { it.copy(message = "Profile saved.") }
}
```
If `mobile_save_profile` succeeds on Supabase but `load()` fails, `operation` catches the exception and displays: `"Unable to complete the request. Check your connection..."`. The customer believes their edits were lost even though they were safely committed to the database.

#### Implementation Instructions
1. In `MobileWorkspaceViewModel.kt`:
   - After `mobile_save_profile` completes, optimistically update `mutable.value.data`'s `profile` object so the UI reflects the saved values immediately.
   - Wrap `load()` in a try/catch:
     ```kotlin
     try { load() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
         mutable.update { it.copy(message = "Profile saved. Refresh to sync latest office records.", error = null) }
     }
     ```
2. In `CustomerProfile.kt`:
   - Ensure `state.message` is visibly rendered to confirm success.
3. Add a unit test verifying `profile()` retains success message even if `load()` throws.

---

### Module D02: Durable Drafts for Incidents and Expenses
* **Status:** `Pending`
* **Target Branch:** `feat/D02-durable-incident-expense-drafts`
* **Reference Docs:** `docs/BACKLOG.md` (#20, #21, #22), `docs/FEATURES.md` (#20, #21, #22)

#### Problem & Root Cause
`DriverIncidentForm` in `DriverFlowDialogs.kt` and `AddTripExpenseSheet.kt` keep input fields and attached photo byte arrays solely in in-memory Compose state (`rememberSaveable`). If the process is killed in the background or the user switches tabs, captured photo attachments and form inputs are wiped out.

#### Implementation Instructions
1. Follow the `ProofDraftStore` pattern (which uses encrypted file storage via `WorkspaceRecoveryStore`):
   - Create an encrypted draft store for incident and expense forms.
   - Keys: `"${driverId}_incident_${deliveryId}"` and `"${driverId}_expense_${deliveryId}"`.
   - Store category, description, delay minutes, and photo byte arrays.
2. In `DriverFlowDialogs.kt` (`DriverIncidentForm`) and `AddTripExpenseSheet.kt`:
   - Restore draft on open.
   - Auto-persist draft when fields or photos change.
   - Delete draft upon successful RPC submission (`reportIncident` / `addExpense`).
3. Add unit tests for draft persistence, retrieval, and deletion.

---

### Module C04: Restore Booking Drafts Across Cold Restart
* **Status:** `Pending`
* **Target Branch:** `feat/C04-booking-recovery-store`
* **Reference Docs:** `docs/BACKLOG.md` (#9), `docs/FEATURES.md` (#9)

#### Problem & Root Cause
Customer multi-step booking forms in `CustomerBookingFlow.kt` do not persist intermediate wizard state to disk. If the app is closed or killed, customer inputs are lost. Additionally, in-flight requests that were submitted right as network dropped cannot reconcile their tracking number on cold restart.

#### Implementation Instructions
1. In `CustomerBookingFlow.kt`:
   - Persist active booking wizard state to `WorkspaceRecoveryStore` under `"${customerId}_booking_draft"`.
   - On screen entry, offer to resume draft if found.
2. For uncertain submissions:
   - Persist client `requestId` before sending `mobile_create_request`.
   - On cold start `load()`, check if `requestId` was created in `shipments`. If yes, display confirmation; if not, restore draft for retry.
3. Clear draft once `book()` returns verified tracking number.

---

### Module C07: Customer Signed-In Password Change Inside Profile
* **Status:** `Pending`
* **Target Branch:** `feat/C07-customer-profile-password-change`
* **Reference Docs:** `docs/BACKLOG.md` (#5), `docs/FEATURES.md` (#5)

#### Problem & Root Cause
In `PasswordChangePolicy.kt`:
```kotlin
require(state.page == "workspace" && !state.offline && profile != null && profile.value("id") == owner && profile.value("role") == "driver")
```
The role `"driver"` is hardcoded. When a customer in `CustomerProfile.kt` clicks "Change Password", `openPasswordChange()` calls `PasswordChangePolicy.begin()`, which throws `IllegalArgumentException` and displays an error banner: *"Reconnect and verify your account phone or email before changing your password."*

#### Implementation Instructions
1. In `PasswordChangePolicy.kt`:
   - Change `profile.value("role") == "driver"` to `profile.value("role") in listOf("driver", "customer")` across `begin()`, `current()`, and `cancel()`.
2. In `MobileWorkspaceApp.kt`:
   - Verify `PasswordChangeDialog` renders cleanly over the customer profile tab when `state.passwordChangeOwner != null`.
3. Add unit test in `PasswordChangePolicyTest.kt` verifying customer accounts can start, verify, and cancel password changes.

---

## 4. Execution Workflow for New Agent Sessions

When picking up a pending item from this plan:
1. Check `git status` and pull `JBranch`.
2. Create and switch to the item's branch:
   ```bash
   git checkout -b feat/<ITEM_ID>-<slug>
   ```
3. Implement the changes following the module instructions above.
4. Verify:
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```
5. Update `docs/BACKLOG.md`, `docs/FEATURES.md`, and this file (`docs/WAVE1_IMPLEMENTATION_PLAN.md`).
6. Merge into `JBranch`:
   ```bash
   git checkout JBranch
   git merge feat/<ITEM_ID>-<slug> --no-edit
   ```
