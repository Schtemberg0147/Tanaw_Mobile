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
| C02 | Show profile-save success even if the following refresh fails | #7 | client | `MobileWorkspaceViewModel` profile flow |
| D02 | Durable drafts for incident and expense forms (incl. attachments) | #20, #21, #22 | client | Reuse the `ProofDraftStore` pattern (encrypted draft persistence) |
| C04 | Restore booking drafts and uncertain submissions after cold restart | #9 | client | Reuse `WorkspaceRecoveryStore` pattern |
| C07 | Keep signed-in password change inside the customer Profile | #5 | client | `PasswordChangeDialog.kt`, `ProfileForm` |

## Wave 2: needs a plan and a backend check

| ID | Item | FEATURES row | Type | Notes |
|----|------|--------------|------|-------|
| C01 | Paginate history and alerts; search beyond latest 200 shipments | #11, #13 | backend? | Limit may come from the `mobile_workspace` RPC |
| C03 | Add Barangay and province to customer addresses | #7, #9 | backend? | Likely new fields in profile/booking RPCs |
| C05 | Verify changes to login/SMS phone number | #3, #7 | backend? | OTP flow, TextBee gateway |
| C06 | Tracking freshness; separate scheduled arrival from live ETA | #11, #17 | client + backend? | Vehicle marker currently renders as the red Pickup pin |
| D07 | Open recovery links directly in the app | #6 | client + config | Manifest `VIEW`/`BROWSABLE` filter + Supabase redirect URL settings |
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
- One item per branch, named by ID (e.g. `feat/D04-image-off-ui-thread`).
- Plan first (files, acceptance criteria, tests), approve, then implement.
- When an item is done, update `docs/FEATURES.md` and tick it off here.