# Wave 2 Implementation Plan: Tanaw Mobile

**Scope:** Wave 2 — Pagination, Structured Addresses, Phone Change Verification, Tracking Freshness & Live ETA, Deep-Link Recovery, and Cross-Cutting UX/Accessibility  
**Target Branch:** `JBranch`  
**Active Working Root:** `app/src/main/java/com/tanaw/app/feature/workspace/`  
**Last Updated:** 2026-11-07

---

## 1. Global Working Rules for All Agents

> [!IMPORTANT]
> ### Architectural & Codebase Rules
> 1. **Active Path Only:** All executable app code is located under `app/src/main/java/com/tanaw/app/feature/workspace/` and its shared dependencies (`MainActivity.kt`, `feature/auth/{LoginScreen,CreateAccountScreen,GoogleSignIn,GoogleSignInButton}.kt`, `feature/driver/components/AddTripExpenseSheet.kt`, `di/`, `ui/theme/`).
> 2. **Strict Legacy Screen Quarantine:** Legacy screens (`TanawNavHost`, `feature/booking/*`, `feature/history/*`, `feature/notifications/*`, `feature/profile/*`, `feature/tracking/*`, `feature/driver/*Screen`) are compiled but unreachable. **Never edit, extend, or cite legacy screens.**
> 3. **Single Responsibility Branches:** Each Wave 2 backlog item (or sub-pass for `C12`) is developed on an isolated feature branch named by its ID (e.g., `feat/C01-paginate-history-alerts`), verified, and merged into `JBranch`.
> 4. **Backend Rules & `docs/BACKEND-REQUESTS.md`:** Canonical Supabase migrations, RPCs, and Edge Functions live in the external `mabutol-trucking-react` repository. Never invent RPC names on the client. Where an optional or future backend enhancement would improve a Wave 2 module, document the exact SQL / RPC / Edge Function specification in `docs/BACKEND-REQUESTS.md` while keeping the Android client resilient using existing Postgrest RLS queries and graceful fallbacks.
> 5. **Quality Gate:** Before declaring any item complete:
>    - Unit tests must pass: `./gradlew testDebugUnitTest`
>    - Debug compilation must succeed: `./gradlew assembleDebug`
>    - Lint check must run cleanly: `./gradlew lintDebug`
>    - Documentation sync: update `docs/BACKLOG.md` and `docs/FEATURES.md`.

---

## 2. Wave 2 Overview & Decisions Matrix

| ID | Module / Feature Item | FEATURES Row | Client / Backend Strategy | Recommended Order |
|:---|:----------------------|:------------:|:--------------------------|:-----------------:|
| **C06** | Tracking freshness; separate scheduled arrival from live ETA; distinct vehicle pin | #11, #17 | **Hybrid Resilient:** Distinct vehicle pin in `ShipmentMap.kt` + `TrackingFreshnessRules` + try `delivery-routing` for active customer deliveries with graceful fallback to Scheduled Arrival + GPS Freshness badge | **1st** |
| **D07** | Open recovery links directly in the app (`VIEW`/`BROWSABLE`) | #6 | **Client + Config:** Custom URI scheme `tanaw://auth/callback` (plus HTTPS verify link parsing) in `AndroidManifest.xml`, `MainActivity.kt`, and `MobileValidation.kt` | **2nd** |
| **C03** | Add Barangay and Province to customer addresses | #7, #9 | **Client-Structured + Backward-Compatible:** Structured fields in Profile, Signup, & Booking (`CustomerAddressRules`), formatted into existing `address`/`city` RPC parameters + optional schema spec in `docs/BACKEND-REQUESTS.md` | **3rd** |
| **C01** | Paginate history and alerts; search beyond latest 200 shipments | #11, #13 | **Postgrest RLS Table Queries:** Offset/range pagination (`limit`/`range`) and server-side `ilike` search on `public.shipments` and `public.notifications` under existing RLS policies | **4th** |
| **C05** | Verify changes to login/SMS phone number | #3, #7 | **Single-Tier Supabase Phone Change OTP:** `auth.updateUser { phone }` → SMS OTP via TextBee hook → `auth.verifyPhoneOtp(OtpType.Phone.PHONE_CHANGE)` → `mobile_save_profile` | **5th** |
| **C12** | Small-screen layouts, keyboards, accessibility, field errors | Cross-cutting | **Screen-by-Screen Client Passes (C12a–C12d):** Incremental passes across Auth, Customer Booking/Tracking, Customer Profile/History/Alerts, and Driver Workspace | **6th** |

---

## 3. Module Details & Step-by-Step Implementation Plan

---

### Module C06: Tracking Freshness, Separate Scheduled Arrival vs. Live ETA & Distinct Vehicle Pin
* **Branch:** `feat/C06-tracking-freshness-live-eta`
* **Reference Docs:** `docs/BACKLOG.md` (C06), `docs/FEATURES.md` (#11, #17), `MISSING_FEATURES_AUDIT.md` (F01, F02)

#### Problem Analysis
1. **Ambiguous Map Pin (`ShipmentMap.kt`):**
   - `pinIcon(context, label)` only checks `if (label == "Destination")`. Consequently, `"Last recorded vehicle position"` passed from `MobileWorkspaceApp.kt` (line 232) renders with the red `"Pickup"` icon (`shipment-pin-Pickup`), making it visually indistinguishable from the pickup pin.
2. **Scheduled Arrival vs. Live ETA Confusion (`MobileWorkspaceApp.kt` & `DriverNavigateOverview.kt`):**
   - `shipments.estimated_arrival` is the static office-planned arrival timestamp, but `ShipmentDetails` labels it `"Estimated arrival"`, which users confuse with a live GPS-calculated ETA.
   - `MobileWorkspaceViewModel.readDetails(id)` exits early for non-drivers (`if (role != "driver") return details`), never attempting to fetch live `routing` status for customers with an active delivery.
3. **Basic Freshness Text (`MobileWorkspaceApp.kt`):**
   - Currently uses a static one-line `runCatching` check for 300 seconds without a live clock tick, structured age formatting, or clear visual separation between Scheduled Arrival and Live Tracking ETA.

#### Implementation Instructions
1. **Create `TrackingFreshnessRules.kt` (`feature/workspace/TrackingFreshnessRules.kt`):**
   - Extract pure-Kotlin testable logic for evaluating GPS position freshness and formatting Customer/Driver arrival labels:
     - `evaluatePositionFreshness(recordedAt: String, accuracyMeters: String, now: Instant = Instant.now()): PositionFreshness`
       - Thresholds:
         - `Fresh`: age `< 120s` (2 min)
         - `Recent`: age `120s..300s` (2–5 min)
         - `Stale`: age `> 300s` (> 5 min)
         - `Unavailable`: blank or unparseable `recorded_at`
       - Human-readable relative age formatter (e.g., `"Updated 35s ago"`, `"Updated 3m ago"`, `"Stale · 18m ago"`).
     - `customerEtaSummary(shipment: JsonObject, delivery: JsonObject?, routing: JsonObject?, freshness: PositionFreshness, offline: Boolean, now: Instant = Instant.now()): CustomerEtaPresentation`
       - Always surfaces `scheduledArrival`: formatted `shipment.value("estimated_arrival")` labeled explicitly as `"Scheduled arrival (office plan)"`.
       - Evaluates `liveEta`:
         - If `offline`: `"Live ETA unavailable while offline (showing scheduled arrival)"`.
         - If `delivery == null` or `delivery.value("status") !in listOf("assigned", "loading", "in_transit")`: `"No active transit ETA"`.
         - If `routing != null` and `DriverRouteStatus.eta(routing, offline = false, now)` returns a valid `"ETA to ..."` string: displays that live HGV ETA + `"Office-confirmed truck route · live GPS"`.
         - Fallback (when `delivery-routing` returns 403 for customer role, or route is unverified, or GPS is > 120s old): returns a clear status based on `freshness` (e.g., `"Live route ETA unavailable · relying on scheduled arrival"` + GPS freshness badge).
2. **Add Distinct Vehicle Marker in `ShipmentMap.kt`:**
   - Update `pinIcon(context, label)` to support three distinct marker types:
     - `"Pickup"` → Red pin (`RGB(220, 38, 38)`)
     - `"Destination"` → Blue pin (`RGB(37, 99, 235)`)
     - `"Vehicle"` → Distinct Navy/Emerald Truck marker (`RGB(0, 45, 84)` or `RGB(22, 163, 74)`) with an inner truck/navigation badge or distinct geometry so it stands out even for color-blind users.
   - In `ShipmentMap`, register `"Vehicle" to pinIcon(context, "Vehicle")` in `pinIcons`, and map `label == "Last recorded vehicle position" || label == "Vehicle"` to `"shipment-pin-Vehicle"`.
   - Update map legend text to `"Red: pickup · Blue: destination · Green/Navy: last recorded vehicle position"`.
3. **Resilient Customer Routing Fetch in `MobileWorkspaceViewModel.kt`:**
   - In `readDetails(id: String)`:
     - When `role != "driver"`, check if there is an active delivery (`assigned`, `loading`, `in_transit`) for shipment `id` in `mutable.value.data?.rows("deliveries")`.
     - If an active delivery exists, call `readRouteStatus(deliveryId)` inside a safe `try / catch` block. If it succeeds, attach `"routing" to routing` to the returned `details` `JsonObject`. If it fails (e.g., 401/403 or network error), attach `"routing" to buildJsonObject { put("reason", "Live route calculation unavailable") }` without failing `readDetails(id)`.
4. **Update Customer `ShipmentDetails` (`MobileWorkspaceApp.kt`) & Driver `DriverNavigateOverview.kt`:**
   - Add a `LaunchedEffect(Unit)` 15-second clock tick in `ShipmentDetails` (matching `DriverRouteStatusCard`) so GPS age and ETA expiry update automatically while the user views the screen.
   - Render a dedicated **Arrival & Tracking Freshness** section separating:
     1. **Scheduled departure** & **Scheduled arrival (office plan)**
     2. **Live ETA** (from `TrackingFreshnessRules.customerEtaSummary`)
     3. **GPS Freshness Badge** (`Fresh` / `Recent` / `Stale` with timestamp and accuracy in meters).

#### Acceptance Criteria & Tests
- **Unit Tests (`TrackingFreshnessRulesTest.kt`):**
  - Verify `Fresh` (< 2m), `Recent` (2–5m), `Stale` (> 5m), future-timestamp guard, and blank timestamp handling.
  - Verify `customerEtaSummary` when `delivery-routing` provides a valid verified ETA vs. when it falls back gracefully (403 / unverified route / stale GPS / offline).
- **UI / Instrumented Test (`ShipmentMapRenderTest.kt`):**
  - Verify 3-point map (`Pickup`, `Destination`, `Last recorded vehicle position`) renders distinct pin icons without crashing.

---

### Module D07: Open Recovery Links Directly in the App
* **Branch:** `feat/D07-recovery-deep-links`
* **Reference Docs:** `docs/BACKLOG.md` (D07), `docs/FEATURES.md` (#6), `MISSING_FEATURES_AUDIT.md` (S10)

#### Problem Analysis
- Currently, users who request an email password reset or signup confirmation must copy the URL from their email and manually paste it into the `"Recovery code (or original email link)"` field in `MobileWorkspaceApp.kt`.
- `AndroidManifest.xml` only declares `MAIN`/`LAUNCHER` on `MainActivity`.
- `MobileValidation.linkToken` only parses `https://<projectHost>/auth/v1/verify?token_hash=...&type=...` URLs, and does not extract tokens from `tanaw://auth/callback` redirect URLs (which Supabase generates when redirecting after `/auth/v1/verify`, or when using PKCE / implicit fragment or query parameters like `token_hash`, `token`, `code`, or `#access_token=...&type=recovery`).

#### Implementation Instructions
1. **Configure `AndroidManifest.xml`:**
   - Set `android:launchMode="singleTask"` on `.MainActivity` so deep links route to the existing activity instance via `onNewIntent` instead of spawning duplicate activities over the workspace.
   - Add a `VIEW` / `BROWSABLE` `<intent-filter>` to `.MainActivity` for the custom scheme:
     ```xml
     <intent-filter>
         <action android:name="android.intent.action.VIEW" />
         <category android:name="android.intent.category.DEFAULT" />
         <category android:name="android.intent.category.BROWSABLE" />
         <data
             android:scheme="tanaw"
             android:host="auth"
             android:pathPrefix="/callback" />
     </intent-filter>
     ```
2. **Expand Link Parsing in `MobileValidation.kt` (or `DeepLinkParser.kt`):**
   - Support extracting recovery/signup verification parameters from both:
     1. Direct Supabase verify URLs: `https://<projectHost>/auth/v1/verify?token_hash=...&type=recovery|signup`
     2. App deep-link callback URLs: `tanaw://auth/callback?...` and `tanaw://auth/callback#...`
   - Parse query and fragment parameters safely:
     - Case A (`token_hash` / `token` + `type`): extract `tokenHash` and `type` (`recovery` or `signup`) for `verifyEmailOtp`.
     - Case B (`access_token` + `refresh_token` + `type=recovery` from Supabase implicit redirect): extract tokens to establish the recovery session and transition directly to the password-update step.
     - Reject malformed URIs, unknown schemes/hosts, or mismatched types.
3. **Wire Intent Handling in `MainActivity.kt` & `MobileWorkspaceViewModel.kt`:**
   - Override `onNewIntent(intent: Intent)` in `MainActivity` and expose incoming deep-link URIs to `MobileWorkspaceApp` / `MobileWorkspaceViewModel.handleDeepLink(uri: String)`.
   - In `MobileWorkspaceViewModel.handleDeepLink(rawUri: String)`:
     - If the deep link is a `recovery` link:
       - Transition to `"recover_code"` (or keep `"workspace"` if `passwordChangeOwner != null` matches the signed-in user), prefill the extracted token/code (or import the recovery session if Supabase redirected with tokens), and display message: `"Recovery link opened. Enter and confirm your new password."`
     - If the deep link is a `signup` confirmation link:
       - Automatically invoke `verifySignup(rawUri)` or prefill `"verify"` page so confirmation completes seamlessly.
4. **Document Supabase Dashboard Configuration in `README.md`:**
   - Document adding `tanaw://auth/callback` to **Authentication → URL Configuration → Redirect URLs** in the Tanaw Dev Supabase dashboard.

#### Acceptance Criteria & Tests
- **Unit Tests (`MobileValidationTest.kt`):**
  - Test `tanaw://auth/callback?token_hash=...&type=recovery`, `tanaw://auth/callback#access_token=...&refresh_token=...&type=recovery`, `https://<projectHost>/auth/v1/verify?...`, and rejection of untrusted hosts/schemes.
- **Instrumented Test (`PublicNavigationTest.kt` / `PasswordChangeNavigationTest.kt`):**
  - Simulate launching/delivering a `VIEW` intent with `tanaw://auth/callback?token_hash=abcdef1234567890abcdef&type=recovery` and verify the app lands on the password reset screen with the token ready.

---

### Module C03: Add Barangay and Province to Customer Addresses
* **Branch:** `feat/C03-customer-barangay-province`
* **Reference Docs:** `docs/BACKLOG.md` (C03), `docs/FEATURES.md` (#7, #9)

#### Problem Analysis
- Currently:
  - `CustomerProfile.kt` and `MobileWorkspaceApp.kt` (`ProfileForm`) only show `Address` and `City`.
  - `CreateAccountScreen.kt` has a single free-text `LOCATION ADDRESS` field.
  - `BookingDraftStore.kt` and `CustomerBookingFlow.kt` store a single string for `pickup` and `dropoff`.
  - On the backend (`20261024155504_mobile_workable_system.sql`), `mobile_save_profile(p_name, p_phone, p_address, p_city)` only accepts 4 parameters, and `mobile_workspace()` only selects `id, name, phone, address, city` from `public.customers` (omitting `customers.province` which defaults to `'Nueva Ecija'`, while `barangay` has no dedicated column).
- Per our architectural decision, we will implement **structured Barangay & Province fields on the client** that format cleanly and deterministically into the existing `address` and `city` RPC fields (requiring **zero blocking backend changes**), while documenting the optional native column migration in `docs/BACKEND-REQUESTS.md`.

#### Implementation Instructions
1. **Create `CustomerAddressRules.kt` (`feature/workspace/CustomerAddressRules.kt`):**
   - Define `StructuredCustomerAddress(val streetAddress: String, val barangay: String, val city: String, val province: String)`:
     - `formatForRpc(street: String, barangay: String, city: String, province: String): Pair<String, String>`:
       - Formats `p_address` as: `if (barangay.isNotBlank()) "${street.trim()}, Brgy. ${barangay.trim().removePrefix("Brgy.").removePrefix("Barangay").trim()}" else street.trim()`
       - Formats `p_city` as: `if (province.isNotBlank()) "${city.trim()}, ${province.trim()}" else city.trim()`
       - Enforces length limits (`p_address <= 500`, `p_city <= 100`) so `mobile_save_profile` never rejects with `INVALID_PROFILE`.
     - `parseFromSaved(rawAddress: String, rawCity: String, explicitBarangay: String = "", explicitProvince: String = ""): StructuredCustomerAddress`:
       - If the backend later returns `barangay` or `province` keys in `customer` JSON, prefer those.
       - Otherwise, parses `", Brgy. <barangay>"` from `rawAddress` and `"<city>, <province>"` from `rawCity` so round-trip editing in Profile preserves all 4 fields!
     - Validation helpers:
       - `validateStreet(street: String): Boolean` (1..380 chars)
       - `validateBarangay(barangay: String): Boolean` (1..100 chars)
       - `validateCity(city: String): Boolean` (1..60 chars)
       - `validateProvince(province: String): Boolean` (1..35 chars)
2. **Update Customer Profile (`CustomerProfile.kt`, `MobileWorkspaceApp.kt`, `CustomerProfileRules.kt`):**
   - In `CustomerProfile.kt`:
     - Under `"Saved Addresses"` panel and `"Edit Default Address"` dialog, display 4 structured fields:
       1. `House / Building / Street Address`
       2. `Barangay`
       3. `City / Municipality`
       4. `Province` (e.g., default suggestion/placeholder `"Nueva Ecija"`)
     - Display `BARANGAY` and `PROVINCE` clearly in the `"Saved Addresses"` card.
   - In `MobileWorkspaceApp.kt` (`ProfileForm` — shown on initial `"Complete your profile"` gate):
     - Add `Barangay` and `Province` fields alongside `Address` and `City`.
   - In `CustomerProfileRules.kt`:
     - Update `optimisticUpdate` to store both the formatted `address`/`city` and `barangay`/`province` fields in the cached `customer` `JsonObject`.
3. **Update Customer Account Creation (`CreateAccountScreen.kt`) & Booking (`CustomerBookingFlow.kt`, `BookingDraftStore.kt`):**
   - In `CreateAccountScreen.kt`:
     - Replace the single `LOCATION ADDRESS` field with structured `Street / Building`, `Barangay`, `City / Municipality`, and `Province` fields (or compose them into the `address` string passed to `onVerifyAndContinue`).
   - In `CustomerBookingFlow.kt` & `BookingDraftStore.kt`:
     - When a customer has a saved profile address (`customer.address`, `customer.city`), offer a quick **"Use Saved Default Address"** chip/action or prefill for pickup that includes Street, Barangay, City, and Province.
     - In `AddMoreDetailsScreen` / address confirmation, allow optional Barangay / Province refinement if reverse geocoding omitted the Barangay.
4. **Create `docs/BACKEND-REQUESTS.md`:**
   - Document the optional backend enhancement for `mabutol-trucking-react`: adding `barangay text` to `public.customers`, returning `barangay, province` in `mobile_workspace()`, and adding `p_barangay text DEFAULT NULL, p_province text DEFAULT NULL` to `mobile_save_profile`.

#### Acceptance Criteria & Tests
- **Unit Tests (`CustomerProfileRulesTest.kt` & `CustomerAddressRulesTest.kt`):**
  - Round-trip parsing and formatting (`street + barangay + city + province` → `p_address, p_city` → `StructuredCustomerAddress`).
  - Boundary checks ensuring combined `p_address` (`<= 500`) and `p_city` (`<= 100`) never violate `mobile_save_profile` constraints.
  - Backward compatibility with legacy addresses that lack `"Brgy."` or `", <Province>"`.

---

### Module C01: Paginate History and Alerts; Search Beyond Latest 200 Shipments
* **Branch:** `feat/C01-paginate-history-alerts`
* **Reference Docs:** `docs/BACKLOG.md` (C01), `docs/FEATURES.md` (#11, #13)

#### Problem Analysis
- `mobile_workspace()` (in `20261024155504_mobile_workable_system.sql`) hardcodes `LIMIT 200` on `public.shipments` and `LIMIT 100` on `public.notifications`.
- In `MobileWorkspaceApp.kt` (lines 177–197), Customer `Active`, `History`, and `Alerts` tabs only filter in-memory `data.rows("shipments")` and `data.rows("notifications")`.
- If a customer has more than 200 shipments (or searches for an older shipment #201+), or has more than 100 notifications, those records cannot be reached.
- `live_schema_dump.sql` confirms that RLS SELECT policies already exist for authenticated customers and users:
  - `CREATE POLICY "customer reads own shipments" ON "public"."shipments" FOR SELECT TO "authenticated" USING (("customer_id" = "public"."my_customer_id"()));`
  - `CREATE POLICY "own notifications read" ON "public"."notifications" FOR SELECT TO "authenticated" USING (("user_id" = "auth"."uid"()));`

#### Implementation Instructions
1. **Create `CustomerPaginationRules.kt` (`feature/workspace/CustomerPaginationRules.kt`):**
   - Define page size constants: `SHIPMENTS_PAGE_SIZE = 50`, `NOTIFICATIONS_PAGE_SIZE = 50`.
   - Pure helper functions:
     - `mergePage(existing: List<JsonObject>, incoming: List<JsonObject>, reset: Boolean): List<JsonObject>` (deduplicates by `id` while preserving `created_at DESC` order).
     - `hasMore(lastFetchedCount: Int, pageSize: Int): Boolean = lastFetchedCount >= pageSize`.
     - `sanitizeSearchQuery(raw: String): String` (trims, escapes `%` / `_` / `,` / `()` characters so Postgrest `or` / `ilike` filters are safe against filter-syntax injection).
2. **Add Paginated & Searchable State to `MobileState` & `MobileWorkspaceViewModel.kt`:**
   - Expand `MobileState` with customer pagination fields:
     - `customerShipments: List<JsonObject>? = null`
     - `customerShipmentsLoading: Boolean = false`
     - `customerShipmentsHasMore: Boolean = false`
     - `customerShipmentsError: String? = null`
     - `notificationsPage: List<JsonObject>? = null`
     - `notificationsLoading: Boolean = false`
     - `notificationsHasMore: Boolean = false`
     - `notificationsError: String? = null`
   - Implement `loadCustomerShipments(tab: String, statusFilter: String, query: String, loadMore: Boolean = false)`:
     - Uses `client.from("shipments").select { ... }` with:
       - Columns matching `mobile_workspace`: `id,tracking_number,status,pickup_address,dropoff_address,pickup_lat,pickup_lng,dropoff_lat,dropoff_lng,route_geometry,cargo_description,weight_kg,special_handling,scheduled_departure,estimated_arrival,quoted_amount,final_amount,driver_notes,cancelled_reason,created_at,updated_at`
       - Status filter:
         - If `tab == "Active"`: `filter { isNotIn("status", listOf("completed", "cancelled")) }`
         - If `tab == "History"` and `statusFilter == "All"`: `filter { isIn("status", listOf("completed", "cancelled")) }`
         - If `tab == "History"` and `statusFilter in listOf("Completed", "Cancelled")`: `filter { eq("status", statusFilter.lowercase()) }`
       - Search filter (if `sanitizedQuery.isNotBlank()`):
         - `or { ilike("tracking_number", "%$q%"); ilike("pickup_address", "%$q%"); ilike("dropoff_address", "%$q%") }`
       - Ordering & Range: `order("created_at", Order.DESCENDING)` and `range(offset.toLong(), (offset + PAGE_SIZE - 1).toLong())`.
   - Implement `loadNotifications(loadMore: Boolean = false)` (used by both Customer and Driver `Alerts` tabs):
     - Queries `client.from("notifications").select { filter { eq("user_id", uid) }; order("created_at", Order.DESCENDING); range(offset.toLong(), (offset + PAGE_SIZE - 1).toLong()) }`.
   - Ensure `detail` lookup in `MobileWorkspaceApp.kt` checks `(state.customerShipments.orEmpty() + data.rows("shipments"))` so clicking a shipment loaded from page 2+ or a deep search opens `ShipmentDetails` properly!
3. **Update `MobileWorkspaceApp.kt` & `DriverWorkspace.kt` UI:**
   - In Customer `Active` and `History` tabs:
     - Debounce search input (`350ms`) via `LaunchedEffect(tab, historyFilter, query)` to trigger `vm.loadCustomerShipments(tab, historyFilter, query, loadMore = false)`.
     - Update supporting text from `"Your latest 200 permitted shipments"` to `"Search all your shipments by tracking reference or address"`.
     - Add a `"Load more shipments"` `OutlinedButton` at the bottom when `state.customerShipmentsHasMore` is true, plus a `LinearProgressIndicator` when `state.customerShipmentsLoading` is true.
   - In Customer & Driver `Alerts` tabs:
     - Display paginated notifications and a `"Load more notifications"` button when `state.notificationsHasMore` is true.

#### Acceptance Criteria & Tests
- **Unit Tests (`CustomerPaginationRulesTest.kt`):**
  - Test `mergePage` deduplication, ordering, `hasMore` boundary conditions, and `sanitizeSearchQuery` escaping of special Postgrest characters.
- **Offline Fallback:**
  - Verify that when `state.offline == true`, searching/filtering gracefully falls back to filtering cached `data.rows("shipments")` and `data.rows("notifications")` with an offline notice.

---

### Module C05: Verify Changes to Login/SMS Phone Number
* **Branch:** `feat/C05-verify-phone-change`
* **Reference Docs:** `docs/BACKLOG.md` (C05), `docs/FEATURES.md` (#3, #7), `TEXTBEE_SETUP.md`

#### Problem Analysis
- Per `TEXTBEE_SETUP.md`:
  > *"Queue requires active owner, opted-in notification type, verified Auth phone matching contact number... A changed operational contact number stops shipment SMS until it matches the verified signup number. Verified phone-change enrollment is separate work."*
- Currently, when a customer edits their phone number in `CustomerProfile.kt` (or `ProfileForm`), `vm.profile(...)` calls `mobile_save_profile` which updates `public.users.phone` and `public.customers.phone`, **without** updating or verifying `auth.users.phone`.
- As a result:
  1. The user's login phone number in `auth.users` becomes out of sync with their profile phone number.
  2. Shipment SMS delivery stops because `public.customers.phone` no longer matches the verified `auth.users.phone`.

#### Implementation Instructions
1. **Create `PhoneChangePolicy.kt` (`feature/workspace/PhoneChangePolicy.kt`):**
   - Pure-Kotlin policy helper modeled after `PasswordChangePolicy.kt`:
     - `isPhoneChanged(currentAuthPhone: String?, currentProfilePhone: String?, requestedPhone: String): Boolean`:
       - Normalizes all numbers via `MobileValidation.phone(...)` and returns `true` if `requestedPhone` differs from the currently verified phone.
     - `begin(state: MobileState, owner: String, newPhone: String, pendingProfile: PendingProfileEdit): MobileState`
     - `challenge(owner: String, newPhone: String, pendingProfile: PendingProfileEdit, now: Long): JsonObject` (persisted in `WorkspaceRecoveryStore` under `"phone_change_challenge"` for 15 minutes so an app background/kill during SMS check can resume).
2. **Add Single-Tier Phone Change OTP Flow in `MobileWorkspaceViewModel.kt`:**
   - Add state fields to `MobileState`:
     - `phoneChangeTarget: String? = null`
     - `phoneChangePendingName: String? = null`
     - `phoneChangePendingAddress: String? = null`
     - `phoneChangePendingCity: String? = null`
   - Update `profile(name: String, phone: String, address: String, city: String)`:
     - Normalize `phone` via `MobileValidation.phone(phone) ?: error("INVALID_CONTACT")`.
     - Check `PhoneChangePolicy.isPhoneChanged(client.auth.currentUserOrNull()?.phone, mutable.value.data?.obj("profile")?.value("phone"), normalized)`.
     - **If unchanged:** proceed immediately with `CustomerProfileRules.saveProfile(...)` as before.
     - **If changed:** initiate `requestPhoneChange(name, normalized, address, city)`:
       1. Call `client.auth.updateUser { this.phone = normalized }` (which triggers Supabase Auth to send a 6-digit `phone_change` OTP via the signed `textbee-auth` Send SMS hook).
       2. Save encrypted challenge in `recoveryStore` (`"phone_change_challenge"`) and start 60s resend `countdown()`.
       3. Update `MobileState` with `phoneChangeTarget = normalized` and pending profile fields, opening `PhoneChangeOtpDialog`.
   - Implement `verifyPhoneChange(code: String)`:
     1. Validate `code.matches(Regex("[0-9]{6,8}"))` and active `phoneChangeTarget`.
     2. Call `client.auth.verifyPhoneOtp(type = OtpType.Phone.PHONE_CHANGE, phone = targetPhone, token = code)`.
     3. Once Auth phone is verified, immediately execute `CustomerProfileRules.saveProfile` (`mobile_save_profile`) with the pending `name`, `targetPhone`, `address`, and `city` so `auth.users`, `public.users`, and `public.customers` stay 100% synchronized!
     4. Clear `"phone_change_challenge"`, dismiss dialog, and display `"Phone number verified and profile updated. Use this number for sign-in and SMS alerts."`
   - Implement `resendPhoneChange()` (calls `client.auth.resendPhone(OtpType.Phone.PHONE_CHANGE, targetPhone)` when `resendSeconds == 0`) and `cancelPhoneChange()`.
3. **Create `PhoneChangeDialog.kt` & Wire into `CustomerProfile.kt` / `MobileWorkspaceApp.kt`:**
   - Create `PhoneChangeDialog` composable displaying:
     - Target phone number being verified (`+639...`)
     - Explanation: *"Enter the 6-digit SMS code sent to your new number. Your login and SMS alert number will update once verified."*
     - 6-digit numeric OTP field (`KeyboardType.NumberPassword` / `KeyboardType.Number`)
     - `"Verify & Save Number"` primary button, `"Resend code in Xs"` button, and `"Cancel"` button.
   - Update `MobileErrorClassifier.kt` to ensure phone-change specific errors (e.g., `phone_exists` / `"This phone number is already registered to another account"`) produce clear user-friendly messages.

#### Acceptance Criteria & Tests
- **Unit Tests (`PhoneChangePolicyTest.kt` & `MobileErrorClassifierTest.kt`):**
  - Test detection of unchanged vs. changed phone numbers across `09...` and `+639...` formats.
  - Test challenge serialization/expiration and cancellation state transitions.
  - Test classification of duplicate phone number (`phone_exists`) and invalid/expired OTP errors.

---

### Module C12: Small-Screen Layouts, Keyboards, Accessibility & Field Errors
* **Branch Strategy:** Execute screen-by-screen in 4 focused sub-branches (`feat/C12a-auth-ux-a11y`, `feat/C12b-customer-booking-ux-a11y`, `feat/C12c-customer-profile-history-ux-a11y`, `feat/C12d-driver-workspace-ux-a11y`) as required by `docs/BACKLOG.md` (*"Do screen by screen, not in one pass"*).
* **Reference Docs:** `docs/BACKLOG.md` (C12)

#### Screen-by-Screen Implementation Breakdown

##### Pass C12a: Auth & Recovery Screens (`LoginScreen.kt`, `CreateAccountScreen.kt`, `MobileWorkspaceApp.kt` auth pages, `PasswordChangeDialog.kt`)
1. **Keyboard Options & IME Actions:**
   - Add explicit `imeAction = ImeAction.Next` on intermediate inputs and `ImeAction.Done` on final fields with `KeyboardActions` so users on small screens don't have to manually dismiss the keyboard to see the submit button.
   - In `MobileWorkspaceApp.kt` (`verify_phone`, `recover`, `recover_code`), pass `KeyboardType.Number` for 6-digit OTP fields, `KeyboardType.Phone`/`Email` for recovery targets, and `KeyboardType.Password` for password fields.
2. **Inline Field Errors & Accessibility:**
   - Show inline `supportingText` / `isError` on `LoginScreen` when phone/email format is invalid after interaction, and on `recover_code` when passwords are `< 8` characters or mismatched.
   - Fix `CreateAccountScreen.kt` Terms `Checkbox` touch target: currently `Modifier.size(20.dp)` removes the 48dp minimum accessibility touch target. Use `LocalMinimumInteractiveComponentSize` or a `Row` with `Modifier.toggleable(...)` and `Role.Checkbox` so the entire row is easily tappable on small phones.
   - Add `imePadding()` to `LoginScreen` and `CreateAccountScreen` scroll containers so the soft keyboard never obscures the bottom password fields.

##### Pass C12b: Customer Booking Wizard & Tracking (`CustomerBookingFlow.kt`, `ShipmentMap.kt`)
1. **Small-Screen (320dp–360dp width) Responsiveness:**
   - Ensure vehicle cards, schedule pills, and summary rows wrap or use `weight(1f)` + `TextOverflow.Ellipsis` without clipping on 320dp screens or at `1.3x` font scale.
   - Ensure `AddMoreDetailsScreen` and `PickupTimeBottomSheet` scroll cleanly when the soft keyboard is open (`imePadding()` + `verticalScroll`).
2. **Field Validation & Keyboard Types:**
   - Ensure `weight` uses `KeyboardType.Decimal` with inline error feedback when `weight <= 0` or exceeds the selected vehicle's `default_capacity_kg` (explaining the vehicle's max kg limit).
   - Ensure `contact` uses `KeyboardType.Phone` with inline validation message if not a valid PH mobile number.
   - Ensure `cargo` shows character count / minimum 3-char inline hint before enabling the next step.
3. **Accessibility Semantics:**
   - Provide `contentDescription` / `semantics(mergeDescendants = true)` on vehicle selection cards and route summary cards.

##### Pass C12c: Customer Profile, Active/History & Alerts (`CustomerProfile.kt`, `MobileWorkspaceApp.kt`)
1. **Small-Screen & Font-Scale Layout:**
   - In `CustomerProfile.kt`, replace tiny raw clickable areas (e.g., notification bell `padding(4.dp)` and avatar camera button `size(32.dp)`) with minimum `48.dp` accessible hit targets (`sizeIn(minWidth = 48.dp, minHeight = 48.dp)`).
   - In `CustomerProfile` edit dialogs, add appropriate `KeyboardOptions` (`KeyboardType.Phone` for contact, `ImeAction.Next`/`Done` for address/barangay/city/province) and `isError` states on `OutlinedTextField`.
2. **Bottom Navigation & Filter Chips:**
   - Ensure `NavigationBarItem` labels and `FilterChip` rows handle large font scales gracefully.

##### Pass C12d: Driver Workspace & Dialogs (`DriverWorkspace.kt`, `DriverFlowDialogs.kt`, `DriverFuelForm.kt`, `AddTripExpenseSheet.kt`, `DriverConversation.kt`)
1. **Small-Screen Headers & Cards:**
   - In `DriverWorkspace.kt` top bar and `DriverAssignment` / `DriverTrips` cards, prevent text overlap when tracking numbers, status badges, or unread counts wrap on narrow screens (`320dp`).
2. **Driver Forms (`DriverFlowDialogs.kt`, `DriverFuelForm.kt`, `AddTripExpenseSheet.kt`):**
   - Add inline field-level error messages (`isError` + `supportingText`) for:
     - `DriverIncidentForm`: description `< 5` chars, delay minutes out of `0..1440` range.
     - `DriverFuelForm`: liters, price per liter, and odometer decimal bounds.
     - `DriverProofForm`: recipient name `< 2` chars, missing photo or signature indicator.
     - `AddTripExpenseSheet`: category blank or amount `<= 0` / `> 1,000,000`.
   - Ensure all dialogs/sheets scroll properly with `imePadding()` when the keyboard is open on small landscape/portrait screens.
3. **Accessibility:**
   - Add meaningful `contentDescription` to informative `DriverAsset` icons and minimum `48.dp` touch targets to `DriverPageHeading` back buttons (currently `Modifier.size(32.dp)`).

#### Acceptance Criteria & Tests
- **Unit & UI Tests:**
  - Add form validation unit tests for inline error states.
  - Add Compose UI tests verifying small-screen (`360dp x 640dp`) rendering, keyboard actions, and accessible semantics across modified screens.

---

## 4. Execution Workflow for Wave 2 Sessions

When implementing any Wave 2 item from this plan:
1. Checkout `JBranch` and create the item's dedicated branch:
   ```bash
   git checkout JBranch
   git checkout -b feat/<ITEM_ID>-<slug>
   ```
2. Implement the module following the exact file scope and instructions in Section 3.
3. Run the verification suite:
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ./gradlew lintDebug
   ```
4. Update `docs/BACKLOG.md` (mark item `[x]`), `docs/FEATURES.md`, and this plan (`docs/WAVE2_IMPLEMENTATION_PLAN.md`).
5. Merge into `JBranch`:
   ```bash
   git checkout JBranch
   git merge feat/<ITEM_ID>-<slug> --no-edit
   ```
