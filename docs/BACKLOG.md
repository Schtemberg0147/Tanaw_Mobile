# Tanaw — Improvement Backlog

Source: office improvement list (C01–C12 customer, D01–D08 driver).
Active app code lives in `feature/workspace/`. Never edit the legacy screens
(`TanawNavHost`, `feature/{auth,booking,driver,...}`), they are unreachable.
Backend (Supabase RPCs, edge functions, migrations) lives in the other repo.
If an item needs a backend change, write down what is needed and stop.

Type: `client` = Android code only | `backend?` = may need backend change, check first
| `test` = manual/device testing, no code | `content` = not code | `optional`

## Wave 1: client-only, low risk (do first)

| ID | Item | FEATURES row | Type | Notes |
|----|------|--------------|------|-------|
| [x] D04 | Process photos/receipts/signatures off the UI thread | #28 | client | Done (`feat/D04-image-off-ui-thread`). Bitmap compression in `DriverFlowDialogs.kt`, `DriverEvidence.kt`, `AddTripExpenseSheet.kt`, `MobileWorkspaceViewModel.kt` moved off UI thread (`Dispatchers.Default` / `IO`). |
| [x] D03 | Separate network failures from auth/backend errors | #28 | client | Done (`feat/D03-error-classification`). `MobileErrorClassifier.kt` isolates network transport errors (`offline = true`) from auth and contract/server errors. `WorkspaceNetworkMonitor.kt` auto-refreshes when reconnecting while offline, retaining manual refresh. |
| [x] C02 | Show profile-save success even if the following refresh fails | #7 | client | Done (`feat/C02-profile-save-confirmation`). Optimistic update in `CustomerProfileRules.kt`, resilient confirmation in `MobileWorkspaceViewModel.kt`, notices visible in `CustomerProfile.kt`. |
| [x] D02 | Durable drafts for incident and expense forms (incl. attachments) | #20, #21, #22 | client | Done (`feat/D02-durable-incident-expense-drafts`). Encrypted drafts via `IncidentDraftStore` and `ExpenseDraftStore` (`WorkspaceRecoveryStore`), auto-persisted off UI thread, restored on open, cleared on RPC success/logout. |
| [x] C04 | Restore booking drafts and uncertain submissions after cold restart | #9 | client | Done (`feat/C04-booking-recovery-store`). Encrypted draft persistence via `BookingDraftStore` (`WorkspaceRecoveryStore`) scoped under `"${customerId}_booking_draft"`. Auto-persisted during wizard entry, offered to resume on screen entry, cleared upon verified submission or explicit cancellation. In-flight submissions persisted with `submitted = true` and reconciled on cold restart `load()` via `BookingRecoveryRules`. |
| [x] C07 | Keep signed-in password change inside the customer Profile | #5 | client | Done (`feat/C07-customer-profile-password-change`). Allowed customer role in `PasswordChangePolicy.kt`, `MobileWorkspaceViewModel.kt`, and `MobileWorkspaceApp.kt`. `PasswordChangeDialog` opens cleanly from customer Profile and retains workspace state on cancellation. |

## Wave 2: needs a plan and a backend check

| ID | Item | FEATURES row | Type | Notes |
|----|------|--------------|------|-------|
| [x] C01 | Paginate history and alerts; search beyond latest 200 shipments | #11, #13 | client | Done (`feat/C01-pagination-search`). Implemented `CustomerPaginationRules.kt` (`mergePage`, `hasMore`, `sanitizeSearchQuery`, `filterOfflineShipments`, `filterOfflineNotifications`). Added paginated and searchable PostgREST queries in `MobileWorkspaceViewModel.kt` (`loadCustomerShipments`, `loadNotifications`) targeting `public.shipments` (with `ilike` on `tracking_number`, `pickup_address`, `dropoff_address`) and `public.notifications`. Integrated search debounce (`350ms`), `"Load more shipments"` / `"Load more notifications"`, and offline fallback in `MobileWorkspaceApp.kt` and `DriverWorkspace.kt`. |
| [x] C03 | Add Barangay and province to customer addresses | #7, #9 | client (+ backend request) | Done (`feat/C03-structured-customer-address`). Pure-Kotlin `CustomerAddressRules.kt` (`StructuredCustomerAddress`, `formatForRpc`, `parseFromSaved`, validation rules). Structured street/building, barangay, city, and province across Customer Profile, Signup, and Booking with backward-compatible RPC mapping (`p_address` format `"<street>, Brgy. <barangay>"`, `p_city` format `"<city>, <province>"`). Added default address quick-chip to booking and updated `optimisticUpdate`. Backend schema enhancement proposal documented in `docs/BACKEND-REQUESTS.md`. |
| [x] C05 | Verify changes to login/SMS phone number | #3, #7 | backend? | Done (`feat/C05-phone-change-verification`). Single-tier Supabase phone change OTP flow (`auth.updateUser { phone }` -> TextBee SMS hook -> `auth.verifyPhoneOtp(OtpType.Phone.PHONE_CHANGE)` -> `mobile_save_profile`). Verified `auth.users.phone` kept in sync with `public.users.phone` and `public.customers.phone`. Durable 15-minute challenge encrypted in `WorkspaceRecoveryStore` (`"phone_change_challenge"`), accessible 6-digit `PhoneChangeDialog`, resend countdown, and duplicate number (`phone_exists`) classification. |
| [x] C06 | Tracking freshness; separate scheduled arrival from live ETA | #11, #17 | client + backend? | Done (`feat/C06-tracking-freshness-live-eta`). Distinct Emerald Green vehicle marker with inner truck badge in `ShipmentMap.kt` (`shipment-pin-Vehicle`). Evaluated position freshness (`evaluatePositionFreshness`) and ETA presentation (`customerEtaSummary`) in `TrackingFreshnessRules.kt`. Resilient customer routing fetch in `MobileWorkspaceViewModel.kt` (`readDetails`). Dedicated Arrival & Tracking Freshness section in `MobileWorkspaceApp.kt` (`ShipmentDetails`) backed by 15s clock tick separating Scheduled Arrival (office plan) from Live ETA and GPS Freshness badge. |
| [x] D07 | Open recovery links directly in the app | #6 | client + config | Done (`feat/D07-recovery-deeplink-callback`). Manifest `singleTask` with `VIEW`/`BROWSABLE` filter for `tanaw://auth/callback`. Pure-Kotlin `DeepLinkParser.kt` extracts OTP tokens (`token_hash`/`token`), implicit fragment sessions (`access_token`/`refresh_token`), and error responses with untrusted origin rejection. Handled in `MainActivity.kt` (`onNewIntent`) and `MobileWorkspaceViewModel.kt` (`handleDeepLink`), prefilling recoveryCode and auto-transitioning to password reset or signup verification. Documented Supabase Redirect URL in `README.md`. |
| C12 | Small-screen layouts, keyboards, accessibility, field errors | cross-cutting | client | Do screen by screen, not in one pass |

## Wave 3: optional additions (need backend work first)

| ID | Item | FEATURES row | Type |
|----|------|--------------|------|
| C09 | Customer-to-office support conversations | #29 | optional, backend |
| C10 | Edit/withdraw eligible pending requests | #29 | optional, backend |
| C11 | Accept/reject a versioned quotation | #10 | optional, backend |

## Testing (no code, run on a physical phone / real backend)

| ID | What to test | FEATURES row |
|----|--------------|--------------|
| D01 | Full delivery workflow, screen-off GPS, reconnect, process restart | #19 |
| D05 | Truck routes and moving ETA with confirmed truck measurements | #17 |
| D06 | Website-to-driver messaging, unread/read sync | #24 |
| C08 | Shipment/delay SMS delivery, opt-out, duplicate prevention | #7 |

Record results in `docs/TEST-LOG.md` (date, device, pass/fail, notes).
Bugs found here may change Wave 1/2 priorities.

## Content dependency

| ID | Item | Notes |
|----|------|-------|
| D08 | Publish approved company policies and support contacts | Set in `system_settings`; app already shows "unpublished" notices |

## Working rules for backlog items
- **Single Active Working Branch (`JBranch`):** All active development takes place directly on `JBranch`. Do not create or switch to isolated feature branches (`feat/...`).
- **Multi-Agent Concurrency:** Multiple agents can work simultaneously in parallel conversation threads by following the file partitioning matrix and concurrency protocol in `docs/WAVE2_IMPLEMENTATION_PLAN.md`:
  - *Domain Isolation First:* Implement domain logic, policies, and pure-Kotlin unit tests in new, isolated files (e.g., `*Rules.kt`, `*Policy.kt`, `*Test.kt`) to prevent any file collision with concurrent agents.
  - *Surgical Integration:* Read shared orchestrator files (`MobileWorkspaceViewModel.kt`, `MobileWorkspaceApp.kt`, `MainActivity.kt`, `AndroidManifest.xml`) immediately before editing, and apply small, non-contiguous replacements. Never overwrite entire shared files.
- **Plan & Verification Gate:** Follow the approved plan (`docs/WAVE2_IMPLEMENTATION_PLAN.md`). Before committing, run:
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
  - `./gradlew lintDebug`
- **Atomic Commits & Documentation Sync:**
  - Commit changes directly to `JBranch` using conventional format: `git commit -m "feat(<ITEM_ID>): <summary>"`.
  - When an item is done, tick it off here in `docs/BACKLOG.md`, update `docs/FEATURES.md`, and update `docs/WAVE2_IMPLEMENTATION_PLAN.md`.