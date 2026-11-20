# Tanaw Mobile Design System & HCI Architecture

Single source of truth for the Human-Computer Interaction (HCI) rules, UX flows, visual identity, layout architecture, component standards, interaction ergonomics, and accessibility standards of the Tanaw Android application (`:app`).

> **Why this document exists:** The initial `feature/workspace/` screens were built functions-first to complete and verify all backend RPCs, offline draft stores, geofence gates, and realtime contracts. Several screens currently use temporary developer-scaffold layouts that violate core HCI heuristics (information overload, disappearing navigation chrome, competing primary actions, and raw key-value dumps). This design system defines both the **visual token system** and the **structural UX/HCI refactoring rules** required to turn those functional screens into an ergonomic, production-grade mobile product while preserving 100% of the underlying business logic and test tags.

---

## A. Identity, Mood & Core HCI Principles

### 1. Product Purpose & Target Users
Tanaw is the native Android logistics, freight booking, and fleet operations companion for **Mabutol Trucking** across Luzon, Philippines. It serves two distinct roles inside a single state-driven workspace (`MobileWorkspaceApp`):

1. **Customers (Shippers & Business Owners)**
   - **Context of use:** Offices, warehouses, loading docks, and on the move.
   - **Core jobs:** Pin pickup and destination locations on a map, select a truck class that fits their cargo weight, request a shipment for office quotation, track live truck GPS and ETA freshness, view Proof of Delivery (POD) documents, and manage verified contact details and SMS preferences.
2. **Drivers (Heavy Goods Vehicle Operators)**
   - **Context of use:** Truck cabs, outdoor loading bays, direct sunlight, cab mounts, and spotty cellular coverage along provincial highways.
   - **Core jobs:** Inspect assigned trips and vehicle specs (including MMDA/provincial truck ban advisories), start foreground GPS route tracking, verify 50m geofence arrival at pickup and destination, record cargo loading, capture camera POD photos and recipient signatures, log fuel purchases and trip expenses with receipts, report road incidents, and message dispatch.

### 2. Visual Personality & Atmosphere
- **Archetype:** Field-ready logistics instrument. Clear, high-contrast, structured, and dependable.
- **Atmosphere:** Grounded industrial utility. The interface prioritizes scannable tracking numbers, unambiguous status badges, verifiable timestamps in Philippine Time (PHT), and high-contrast action targets that remain legible in direct sunlight.
- **What Tanaw is NOT:** It is not a raw database inspector, a consumer social app, or a decorative marketing landing page. It avoids unformatted `key: value` text dumps, frosted glass, glowing neon accents, floating decorative orbs, and invented metrics.

### 3. Foundational HCI Principles Governing Every Screen
Every screen and flow in Tanaw Mobile must obey these six HCI laws:

1. **Progressive Disclosure & Aesthetic Minimalism (Nielsen H8):**
   - Never dump all database columns onto a single scrolling card. Show primary decision-making context first (stage, destination, status, primary CTA), and group secondary operational records (timelines, expense logs, HGV route metadata, POD files) into structured, collapsible, or tabbed sections.
2. **Hick's Law (One Primary Action Per Viewport):**
   - The time required to make a decision increases with the number and complexity of choices. Every screen or cockpit card must highlight **at most one solid Primary CTA** representing the next logical workflow step (for example, `"I've Arrived at Pick-up"` or `"Continue"`). Secondary utilities (`Record Fuel`, `Add Expense`, `Report Issue`, secondary map previews) must use secondary/chip affordances so they never compete visually with the primary milestone button.
3. **Fitts's Law & Thumb-Zone Ergonomics:**
   - Primary commitments live in anchored bottom bars or in the primary cockpit card within easy thumb reach, sized at `48.dp` to `54.dp` height with at least `8.dp` clear separation from adjacent controls. Destructive actions (`Cancel Shipment`, `Sign Out`, `Discard Draft`) are visually separated from primary progression buttons and gated by confirmation dialogs.
4. **Visibility of System Status Without Manual Refresh Clutter (Nielsen H1):**
   - Users must always know whether telemetry is live (`Fresh GPS`), stale (`Stale GPS`), offline-cached, or syncing (`LinearProgressIndicator` in the top bar). Eliminate scattered manual `"Refresh ..."` text buttons inside individual cards; provide a single top-bar sync control (or pull-to-refresh) and show inline `"Retry"` buttons only when a fetch actually fails.
5. **User Control, Freedom & Persistent Wayfinding (Nielsen H3 & H4):**
   - Top app bars and bottom navigation bars must remain anchored in `Scaffold(topBar = ..., bottomBar = ...)` and must never scroll off-screen or disappear mid-workflow. Every multi-step screen, OTP verification gate, and detail view must provide a visible, top-left Back affordance in addition to hardware back handling.
6. **Natural Mapping & Honest Affordances (Norman's Principles):**
   - Controls must match the data type they manipulate: binary preferences (such as SMS notifications) use a single `Switch` showing current state, never two competing `"Enable"` / `"Disable"` buttons; fixed category filters use `FilterChip` rows; OTP entry uses 6-digit code cells; and human-readable labels replace raw database identifiers (`pickup_arrived_at`, `OpenRouteService HGV`).

### 4. Relationship to the Web Application (`mabutol-trucking-react`)
- **Shared Brand Core:** Both platforms share the Mabutol Trucking maritime navy (`#0B2A4A`), safety orange (`#E87722` / `#C2410C`), and semantic status vocabulary (`pending`, `booked`, `assigned`, `loading`, `in_transit`, `delivered`, `completed`, `cancelled`, `correction_requested`).
- **Mobile-Specific Adaptation:** The web application is a multi-column dispatch, quotation, and fleet administration console. The mobile app is a focused field tool: it replaces wide data tables and multi-panel dashboards with a step-gated driver cockpit, vertical card stacks, full-screen map pickers, and modal bottom sheets.

---

## B. Color Palette

All color tokens are centralized in `ui/theme/Color.kt` and `ui/theme/Theme.kt`. Both Light and Dark themes are supported in production and follow system appearance (`isSystemInDarkTheme()`).

### 1. Core Surface & Brand Tokens

| Token Role | Light HEX | Dark HEX | WCAG Contrast & Usage Notes |
|------------|-----------|----------|-----------------------------|
| **Background (`background`)** | `#F8FAFC` | `#10141D` | App canvas behind cards and lists. |
| **Surface (`surface`)** | `#FFFFFF` | `#181D2A` | Primary card, sheet, top bar, and bottom navigation surface. |
| **Elevated / Input Surface (`surfaceContainerHigh`)** | `#F1F5F9` | `#222938` | Text field fills, unselected chips, secondary info blocks, and subtle section wells. |
| **Surface Variant (`surfaceVariant`)** | `#E2E8F0` | `#1E2536` | Neutral badge fills, skeleton placeholders, and inactive track backgrounds. |
| **Primary (`primary`)** | `#0B2A4A` | `#8DA3C4` | Brand Navy. Primary buttons, active navigation indicators, key headings, and selected card borders. (Light `#0B2A4A` on `#FFFFFF` = 14.54:1; Dark `#8DA3C4` on `#10141D` = 7.12:1). |
| **On Primary (`onPrimary`)** | `#FFFFFF` | `#091526` | Text and icons inside primary buttons and badges. |
| **Primary Container (`primaryContainer`)** | `#DCE6F2` | `#163354` | Tinted navy container for active route banners, default-address shortcut chips, and selected states. |
| **On Primary Container (`onPrimaryContainer`)** | `#0B2A4A` | `#D6E4F7` | Text inside `primaryContainer` (Light = 11.4:1; Dark = 10.2:1). |
| **Secondary / Brand Accent (`secondary`)** | `#C2410C` | `#FFA056` | Accessible safety orange for interactive links, unread badges, and secondary CTAs. (`#C2410C` on `#FFFFFF` = 5.18:1, fixing the 2.96:1 contrast failure of `#E87722` when used as text or with white foreground). |
| **On Secondary (`onSecondary`)** | `#FFFFFF` | `#2E1500` | Text/icons on `secondary` fills. |
| **Brand Decorative Accent (`OrangeAccent`)** | `#E87722` | `#FFA056` | Used only for non-text accents (4dp card status strips, map destination ring, or fills paired with dark `#0F172A` text at 6.03:1). Never pair `#E87722` with white text. |
| **Secondary Container (`secondaryContainer`)** | `#FFEDD5` | `#3E2314` | Warm amber-orange fill for unfinished booking draft cards and special handling tags. |
| **On Secondary Container (`onSecondaryContainer`)** | `#7C2D12` | `#FFEDD5` | High-contrast text inside `secondaryContainer` (Light = 7.45:1; Dark = 10.8:1). |

### 2. Text & Border Tokens

| Token Role | Light HEX | Dark HEX | WCAG Contrast & Usage Notes |
|------------|-----------|----------|-----------------------------|
| **Primary Text (`onBackground` / `onSurface`)** | `#0F172A` | `#F0F3F8` | Body copy, titles, form values, and addresses. (Light on `#FFFFFF` = 17.85:1; Dark on `#181D2A` = 15.1:1). |
| **Secondary Text (`onSurfaceVariant`)** | `#475569` | `#94A3B8` | Supporting text, timestamps, field labels, and captions. (`#475569` on `#F1F5F9` = 6.75:1, replacing `#64748B` which dropped to 4.36:1 on tinted inputs). |
| **Interactive Outline (`outline`)** | `#64748B` | `#526077` | Input field borders, outlined button borders, and unselected filter chip strokes. Meets 3:1 non-text contrast (WCAG 1.4.11) on both `#FFFFFF` (4.76:1) and `#F1F5F9` (4.36:1). |
| **Divider / Hairline (`outlineVariant`)** | `#E2E8F0` | `#2A3346` | Non-interactive card borders and `HorizontalDivider` lines. |

### 3. Semantic Status & Map Tokens

| Semantic State | Foreground (Light / Dark) | Background (Light / Dark) | Application |
|----------------|---------------------------|---------------------------|-------------|
| **Success / Completed / Valid** | `#166534` / `#4ADE80` | `#DCFCE7` / `#143823` | `completed`, `delivered`, `approved`, valid compliance docs, fresh GPS badge, driver `available` status. |
| **Warning / Expiring / Pending** | `#9A3412` / `#FDBA74` | `#FEF3C7` / `#3B2510` | `pending`, `loading`, expiring compliance docs, stale GPS warning, truck ban advisories, special handling. |
| **Error / Cancelled / Expired** | `#B91C1C` / `#FFB4AB` | `#FEE2E2` / `#3B1C1D` | `cancelled`, `delayed`, `correction_requested`, expired/rejected docs, geofence violations, form errors. |
| **Info / In Transit / Active** | `#0369A1` / `#7DD3FC` | `#E0F2FE` / `#0F2942` | `booked`, `assigned`, `in_transit`, active route indicators. |
| **Map Pin: Pickup** | `#DC2626` | `#FFFFFF` (inner dot) | Pickup marker on `ShipmentMap` and `FullScreenLocationPicker`. |
| **Map Pin: Destination** | `#2563EB` | `#FFFFFF` (inner dot) | Destination marker and `#2563EB` route polyline on `ShipmentMap`. |
| **Map Pin: Live Vehicle** | `#16A34A` | `#FFFFFF` (truck glyph) | Last recorded truck GPS position marker (`shipment-pin-Vehicle`). |

### 4. Interactive State Opacities
- **Pressed / Ripple:** `primary` or `onSurface` at `12%` alpha (`0.12f`).
- **Focused:** `2.dp` solid border using `primary` (`#0B2A4A` light / `#8DA3C4` dark) with at least 3:1 contrast against adjacent surface.
- **Disabled Container:** `onSurface` at `12%` alpha (`0.12f`).
- **Disabled Content:** `onSurface` at `38%` alpha (`0.38f`).

---

## C. Typography

### 1. Font Family & Fallbacks
- **Primary Font Family:** `Manrope` (`R.font.manrope`, variable weight `400` to `800`), with `FontFamily.SansSerif` (Roboto) as system fallback.
- **Unification Rule:** Previously, `DriverWorkspace` loaded `Manrope` locally while Customer and Auth screens used default Roboto (`FontFamily.Default`). Both Customer and Driver workspaces must share the unified `Manrope` `Typography` in `ui/theme/Type.kt` so numbers, tracking codes, peso amounts (`₱`), and headings render identically across roles.

### 2. Typographic Scale

| Material 3 Slot | Role in Tanaw | Size | Line Height | Weight | Letter Spacing |
|-----------------|---------------|------|-------------|--------|----------------|
| `headlineLarge` | Auth brand title (`TANAW`) | `28.sp` | `34.sp` | `ExtraBold (800)` | `1.5.sp` |
| `headlineMedium` | Top-level section header (`My Trips`) | `24.sp` | `30.sp` | `Bold (700)` | `0.sp` |
| `headlineSmall` | Screen title (`Active shipments`, `My Profile`, `TNW-...`) | `20.sp` | `26.sp` | `Bold (700)` | `0.sp` |
| `titleLarge` | Hero metric, modal title, driver stage heading | `18.sp` | `24.sp` | `Bold (700)` | `0.sp` |
| `titleMedium` | Card title (`Delivery overview`, `Personal Information`) | `16.sp` | `22.sp` | `Bold (700)` | `0.sp` |
| `titleSmall` | Sub-card header, vehicle name, inline total | `14.sp` | `20.sp` | `Bold (700)` | `0.sp` |
| `bodyLarge` | Primary reading text, chat message body, prominent values | `16.sp` | `24.sp` | `Normal (400)` | `0.sp` |
| `bodyMedium` | Standard card body, addresses, form input text | `14.sp` | `20.sp` | `Normal (400)` | `0.sp` |
| `bodySmall` | Secondary metadata, timestamps, helper notices, legal footers | `12.sp` | `17.sp` | `Normal (400)` | `0.1.sp` |
| `labelLarge` | Primary & secondary button labels | `15.sp` | `20.sp` | `Bold (700)` | `0.1.sp` |
| `labelMedium` | Status badge text, filter chips, action links | `13.sp` | `18.sp` | `SemiBold (600)` | `0.1.sp` |
| `labelSmall` | Field captions (`PICKUP`, `CARGO`), bottom nav labels | `11.sp` | `15.sp` | `Bold (700)` | `0.5.sp` |

### 3. Truncation, Wrapping & Formatting Rules
- **Tracking Numbers & Plates:** Single line (`maxLines = 1`, `overflow = TextOverflow.Ellipsis`), `FontWeight.Bold`, placed in a `Modifier.weight(1f, fill = false)` container so adjacent status badges are never pushed off-screen.
- **Addresses:** Up to 2 lines (`maxLines = 2`, `overflow = TextOverflow.Ellipsis`) in list cards; full wrapped text (`maxLines = Int.MAX_VALUE`) inside detail screens, booking summaries, and driver navigation cards so drivers never miss building or barangay details.
- **Currency & Units:** Always format via `MobilePresentation.money()` (`₱1,250.00`), `MobilePresentation.number(val, "kg")`, and `MobilePresentation.time()` (`MMM d, yyyy · h:mm a PHT`). Never display raw database column names (`pickup_arrived_at`, `customer_showed_at`) or raw unformatted ISO timestamps.
- **Capitalization:** Use sentence case for all buttons, headings, empty states, and status badges. Reserve uppercase (`labelSmall` with `0.5.sp` letter spacing) strictly for short 1-2 word field eyebrows (`PICKUP`, `DESTINATION`, `CARGO`, `LICENSE NO.`).

---

## D. Layout, Spacing & Radii

### 1. Spacing Scale (4dp Base Grid)
- `4.dp` (`xs`): Tight inline icon-to-label gap, vertical gap between eyebrow label and value.
- `8.dp` (`sm`): Minimum gap between adjacent touch targets, chip rows, and form label-to-input spacing.
- `12.dp` (`md`): Internal item spacing inside compact cards and list rows.
- `16.dp` (`lg`): Standard screen horizontal padding, internal card padding, and vertical spacing between cards in a scrolling list.
- `20.dp` (`xl`): Prominent hero card padding and section separation.
- `24.dp` (`xxl`): Auth screen horizontal margin and modal bottom sheet padding.
- `32.dp` (`xxxl`): Bottom scroll clearance above anchored bars.

### 2. Screen Content Margins & Density
- **Standard Workspace Horizontal Padding:** `16.dp` across both Customer and Driver tabs (unifying the current split between `20.dp` in Customer and `16.dp` in Driver).
- **Auth & Standalone Form Horizontal Padding:** `24.dp`.
- **Vertical Card Rhythm:** `16.dp` between top-level section cards; `12.dp` between items in a homogeneous list (`ShipmentList`, `DriverTrips`, `Alerts`).
- **Content Density & Progressive Disclosure Rule:**
  - Never stack more than **4 data points** inside a single summary card.
  - Never stack more than **3 distinct functional cards** above the fold without grouping secondary records into tabs or collapsible accordions.
  - Avoid fixed-height empty containers (`heightIn(min = 480.dp)`) that force scrolling on compact screens.

### 3. Border-Radius Tokens
Unify the current mismatch between Customer (`14.dp`-`16.dp`) and Driver (`2.dp`-`4.dp` mixed with `16.dp`-`20.dp`) into a single purposeful scale:
- **XS (`6.dp`):** Status badges, inline tags (`StatusBadge`, `DriverBadge`, compliance tags).
- **SM (`10.dp`):** Buttons (`TanawPrimaryButton`, `TanawOutlinedButton`, `DriverButton`) and filter chips.
- **MD (`12.dp`):** Text fields (`TanawTextField`), location selector rows, and nested sub-cards (fuel log entries, delivery step rows).
- **LG (`16.dp`):** Primary content cards (`WorkspaceCard`, `DriverCard`, `DriverTripSection`, `VehicleSelectionCard`, `RouteSummaryCard`).
- **XL (`24.dp` top corners):** Modal bottom sheets (`PickupTimeBottomSheet`, `AddTripExpenseSheet`) and dialogs.
- **Full (`CircleShape`):** Avatar initials badges, map recenter FAB, and timeline/route stop dots.

### 4. Elevation & Shadow Rules
- **Dose Cap:** Most cards sit flat (`0.dp` shadow) on `#F8FAFC` / `#10141D` using a `1.dp` `outlineVariant` border for crisp structure.
- **Elevated Surfaces (`2.dp` to `4.dp`):** Reserved for sticky top bars, anchored bottom action bars, floating map controls, and selected `VehicleSelectionCard` items.
- **Modals & Sheets (`6.dp` to `8.dp`):** Reserved for `ModalBottomSheet`, `Dialog`, and `ForgotPasswordCardScreen`.

### 5. Scroll Behavior
- Prefer `LazyColumn` with stable keys (`key = { it.value("id") }`) for unbounded or paginated lists (`Active`, `History`, `Trips`, `Alerts`).
- Never nest two vertically scrollable containers in the same direction without a bounded height (`heightIn(max = ...)`). Map views (`ShipmentMap`) inside scrollable detail pages must maintain a fixed `220.dp` height and intercept touch only when explicitly interacted with.

---

## E. Mobile Component Standards

### 1. Primary & Secondary Buttons (`TanawPrimaryButton`, `TanawOutlinedButton`)
1. **Purpose:** Trigger the primary screen commitment or secondary alternative action.
2. **Visual Appearance:**
   - **Primary:** Solid `primary` (`#0B2A4A` light / `#8DA3C4` dark) fill, `onPrimary` text (`labelLarge`, `15.sp` Bold), `10.dp` corner radius, no decorative trailing arrows.
   - **Secondary (Outlined):** `surface` fill, `1.5.dp` `primary` border (or `error` border for destructive actions like Sign Out / Cancel Shipment), `primary` text, `10.dp` radius.
   - **Tertiary (TextButton):** Borderless text action (`13.sp`-`14.sp` SemiBold) for low-priority inline actions ("View recorded proof", "Switch trip").
3. **Dimensions & Spacing:** `52.dp` height (`48.dp` minimum in compact bottom bars), `fillMaxWidth()` in forms and bottom bars, minimum `8.dp` vertical/horizontal gap from adjacent buttons.
4. **Interaction Behavior:** Immediate ripple feedback; disables pointer input while `isLoading == true` or `busy == true`.
5. **States:** Default, Pressed, Focused (`2.dp` ring), Disabled (`0.38f` alpha), Loading (`20.dp` `CircularProgressIndicator` with `2.dp` stroke + optional status label such as "Checking pick-up location…").
6. **When to use / not use:** Use at most **one** solid Primary Button per viewport or card group (Hick's Law). Never use solid primary buttons for "Refresh", binary toggles, or secondary route previews.

### 2. Text Fields & Forms (`TanawTextField`, `OtpCodeInputRow`)
1. **Purpose:** Capture structured user input (credentials, Philippine phone numbers, 6-digit OTPs, addresses, cargo weight, fuel liters, expense amounts, chat messages).
2. **Visual Appearance:** Top-aligned persistent label (`TanawInputLabel`, `labelSmall`) or Material `OutlinedTextField` floating label; `surfaceContainerHigh` fill; `12.dp` radius; `1.dp` `outline` border at rest, `2.dp` `primary` border when focused, `2.dp` `error` border on validation failure. For 6-digit verification codes (`verify`, `verify_phone`, `recover_code`), use `OtpCodeInputRow` (6 distinct `46.dp x 54.dp` cells with `KeyboardType.NumberPassword`).
3. **Dimensions & Spacing:** Minimum height `56.dp`; `6.dp` gap between label and field; `16.dp` vertical spacing between form fields.
4. **Interaction Behavior:** Explicit `KeyboardOptions` (`KeyboardType.Phone`, `Email`, `Decimal`, `Number`, `Text`) and `ImeAction` (`Next` moves focus downward; `Done`/`Search` clears focus and submits if valid).
5. **States:** Empty (with honest format placeholder like `0912 345 6789`, never fake names once focused), Focused, Filled, Error (red border + inline `supportingText` naming the exact rule, e.g., `"Enter a Philippine mobile number, e.g. 0912 345 6789."`), Disabled/Read-only.
6. **When to use / not use:** Use for free-form or numeric entry. Do not use a plain text field when choices are fixed (use `FilterChip` or a selector sheet instead).

### 3. Cards & Information Panels (`WorkspaceCard`)
1. **Purpose:** Group related shipment, route, vehicle, or profile attributes into a scannable unit.
2. **Visual Appearance:** `surface` fill, `16.dp` corner radius, `1.dp` `outlineVariant` border, optional `4.dp` top or left semantic status bar **only** when the card represents a live stateful entity (active assignment, completed trip, or submitted booking reference).
3. **Dimensions & Spacing:** `16.dp` internal padding, `12.dp` internal vertical item spacing.
4. **Interaction Behavior:** Static container by default; when clickable (e.g., `ShipmentList` item or `DriverAssignment` card), the entire card surface uses `Surface(onClick = ...)` with bounded ripple and an explicit trailing affordance (`StatusBadge` or chevron).
5. **States:** Default, Pressed (if interactive), Selected (`2.dp` `primary` border on `VehicleSelectionCard`).
6. **When to use / not use:** Use to separate distinct domain concepts (e.g., Route Card vs. Pricing Card vs. Timeline Card). Never dump 15+ unrelated fields into one giant card, and do not nest bordered cards more than one level deep.

### 4. Top App Bars (`TanawTopBar`, `TanawSubScreenTopBar`)
1. **Purpose:** Establish brand identity and global actions on root tabs, or provide persistent Back navigation + context on detail/step screens.
2. **Visual Appearance:**
   - **Root Tab Bar (`TanawTopBar`):** `surface` background, `1.dp` bottom `outlineVariant` divider, left-aligned Tanaw logo (`36.dp`) + `"TANAW"` title (or role title), right-aligned compact sync icon button (`48.dp`), optional Driver Messages icon button with unread badge, and Notification bell `IconButton` with `BadgedBox`.
   - **Detail / Step / Cockpit Top Bar (`TanawSubScreenTopBar`):** `48.dp` Back `IconButton` (`ArrowBack`), `titleMedium` (`16.sp`-`18.sp` Bold) single-line title (e.g., `TNW-2026-0042`), optional trailing `StatusBadge` or quick-action icons (Trip Chat / Alerts on Driver `Navigate`), and a `3.dp` bottom `LinearProgressIndicator` for multi-step flows or active background sync.
3. **Dimensions & Spacing:** `64.dp` height below `statusBarsPadding()`, `16.dp` horizontal padding.
4. **Interaction Behavior:** Back button pops the current detail, sub-panel, or step; notification icon navigates to `Alerts` tab.
5. **States:** Idle, Busy/Syncing (shows `LinearProgressIndicator` along the bottom edge of the top bar).
6. **When to use / not use:** Render **once** inside `Scaffold(topBar = ...)` so it stays pinned at the top of the screen. **Never** place the top bar inside a `verticalScroll` column where it scrolls off-screen, and **never** hide the top bar completely during Driver navigation.

### 5. Bottom Navigation (`NavigationBar` / `DriverBottomBar`)
1. **Purpose:** Switch between top-level role destinations (`Home`, `Active`, `History`, `Alerts`, `Profile` for Customers; `Home`, `Navigate`, `Trips`, `Profile` for Drivers).
2. **Visual Appearance:** Material 3 `NavigationBar` on `surface` with `1.dp` top `outlineVariant` divider, pill indicator in `primaryContainer`, `primary` selected icon/label, `onSurfaceVariant` unselected icon/label, `labelSmall` (`11.sp`) single-line labels (`alwaysShowLabel = true`).
3. **Dimensions & Spacing:** `68.dp`-`80.dp` height above `navigationBarsPadding()`, minimum `48.dp x 48.dp` touch target per tab.
4. **Interaction Behavior:** Tapping a tab switches the active tab and clears any child `detail` selection; hidden during full-screen map picking, multi-step booking sub-screens (`fullScreenBookingStep == true`), or full-screen detail views (`ShipmentDetails`).
5. **States:** Unselected, Selected (filled/tinted icon + pill indicator + bold label), Badged (unread count on `Alerts`), Disabled (when `state.busy` blocks navigation during critical mutations).
6. **When to use / not use:** Use only for 4 to 5 root destinations. Do not place secondary actions (like "Refresh" or "Sign out") in the bottom navigation bar.

### 6. Navigation Drawers
- **Standard:** **Not used in Tanaw Mobile.** All primary destinations fit in the 4-tab (Driver) or 5-tab (Customer) Bottom Navigation Bar, and secondary account links live inside the `Profile` tab. Do not introduce a hamburger drawer.

### 7. Tabs, Segmented Controls & Quick Action Strips (`FilterChip`, `SingleChoiceSegmentedButtonRow`)
1. **Purpose:**
   - Filter datasets (`All`, `Completed`, `Cancelled` in Customer History; `This month`, `Last month`, `All records` in Driver Trips).
   - Switch between secondary information panels below the fold on the Driver Cockpit (`Route & Map` | `Checkpoints` | `Trip Logs`) to prevent endless vertical scrolling.
2. **Visual Appearance:** Material 3 `FilterChip` or `SegmentedButton` with `10.dp` corner radius, `primaryContainer` fill and `onPrimaryContainer` text when selected, `surface` fill and `outline` border when unselected.
3. **Dimensions & Spacing:** `36.dp`-`40.dp` visual height (`48.dp` minimum touch height via `minimumInteractiveComponentSize()`), `8.dp` horizontal gap.
4. **Interaction Behavior:** Single-select immediate view/filter update without losing scroll position.
5. **States:** Unselected, Selected, Disabled.
6. **When to use / not use:** Replace custom underlined text columns (`DriverTrips` ad-hoc tab strip) and unlabeled filter toggle icons with explicit `FilterChip` rows.

### 8. Dialogs & Bottom Sheets
1. **Purpose:**
   - **ModalBottomSheet:** Contextual parameter selection or short form entry (`PickupTimeBottomSheet`, `AddTripExpenseSheet`, Driver Trip Chat sheet).
   - **Full-Screen Dialog (`DriverTransaction`):** Multi-field operational capture requiring camera/signature (`DriverProofDialog`, `DriverIncidentDialog`, `DriverFuelForm`).
   - **AlertDialog:** Destructive action confirmation (`CustomerCancellationDialog`, discard unconfirmed fuel/POD draft) or concise policy/security challenge (`InfoDialog`, `PhoneChangeDialog`, `PasswordChangeDialog`).
2. **Visual Appearance:** `surface` container, `24.dp` top corners on sheets / `16.dp` on alert dialogs, anchored bottom CTA in full-screen transaction dialogs. All dialogs must read colors from `MaterialTheme.colorScheme` (never hardcode light-mode `InputGray` or `NavyPrimary`).
3. **Dimensions & Spacing:** `24.dp` horizontal padding in sheets, `16.dp` in full-screen dialogs, `imePadding()` + `navigationBarsPadding()` on all bottom content.
4. **Interaction Behavior:** Dismissible via Back press or top-right close button unless a network mutation is actively in flight (`!busy`), or gated by a discard confirmation dialog when unsaved/unconfirmed changes exist.
5. **States:** Editing, Preparing Media (encoding photo/signature off UI thread), Submitting, Unconfirmed Retry.
6. **When to use / not use:** Use `AlertDialog` only for short confirmations or 1-2 field OTP/password prompts. Use `ModalBottomSheet` or `DriverTransaction` for anything with 3+ inputs or media attachments.

### 9. Dropdowns & Selection Controls
1. **Purpose:** Choose a vehicle class (`VehicleSelectionCard`), toggle SMS notifications (`Switch`), or pick a date/time (`PickupTimeBottomSheet`).
2. **Visual Appearance:** Prefer visible radio-cards (`VehicleSelectionCard` with `Role.RadioButton` semantics), `Switch` rows, and `FilterChip` groups over hidden dropdown menus on mobile.
3. **Dimensions & Spacing:** Minimum `48.dp` height on all switch rows and selection cards.
4. **Interaction Behavior:** Whole row/card is tappable (`Modifier.toggleable` or `Surface(onClick)`).
5. **States:** Unchecked/Unselected, Checked/Selected, Disabled (e.g., when offline or missing a verified phone number).
6. **When to use / not use:** Never represent a boolean toggle (such as SMS notifications) as two separate `"Enable"` / `"Disable"` buttons; always use a single `Switch` control with clear state feedback.

### 10. Lists, Timelines & Route Displays (`ShipmentList`, `DriverTrips`, `MilestoneStepper`)
1. **Purpose:** Present chronological shipments, vertical pickup-to-destination route connectors, recorded milestone checkpoints, fuel logs, and compliance documents.
2. **Visual Appearance:**
   - **Shipment List Card:** Outlined card (`16.dp` radius) with top row `[Tracking Number (Bold) | StatusBadge]`, middle visual route summary `[Pickup → Destination]`, and bottom metadata row `[Timestamp PHT · Quoted/Final Amount]`.
   - **Milestone Stepper (`MilestoneStepper`):** Vertical timeline with connected `CircleShape` nodes (`12.dp` dot + `2.dp` vertical connector line) showing Completed (`SuccessGreen`), Current (`Primary`), and Upcoming (`outlineVariant`) stages with human-readable labels and formatted PHT timestamps.
3. **Dimensions & Spacing:** `12.dp` vertical gap between list cards; explicit `"Load more..."` `OutlinedButton` at the tail when `hasMore == true`.
4. **Interaction Behavior:** Tapping a shipment card opens `ShipmentDetails` or `Trip details`; pagination loads in ranges of 100-200 items without losing scroll position.
5. **States:** Populated, Loading More, Empty, Section Error.
6. **When to use / not use:** Always use structured key-value rows (`ProfileField`) or `MilestoneStepper` rather than raw `Text("$label: $value")` dumps.

### 11. Status Indicators & Badges (`StatusBadge`, `GpsFreshnessBadge`)
1. **Purpose:** Communicate shipment lifecycle state, compliance validity, or GPS telemetry freshness at a glance.
2. **Visual Appearance:** `6.dp` rounded pill/tag with semantic background tint and high-contrast semantic text (`12.sp`-`13.sp` SemiBold), paired with a text label (never color alone).
3. **Dimensions & Spacing:** `horizontal = 10.dp, vertical = 4.dp` padding.
4. **Interaction Behavior:** Non-interactive status indicator.
5. **States:** Semantic mapping (`Success`, `Warning`, `Error`, `Info`, `Neutral`).
6. **When to use / not use:** Never add glowing or pulsing dots unless marking a genuinely live, under-120-second GPS telemetry stream.

### 12. Banners & Inline Notices (`Notices`, `DriverNotice`, `SuccessBanner`)
1. **Purpose:** Surface workspace-wide offline status, mutation success, or classified error messages (`MobileErrorClassifier`).
2. **Visual Appearance:** `10.dp` rounded surface with `14.dp` padding, semantic container color (`errorContainer`, `successBannerBackground`, or warning amber for offline state), leading semantic icon, message text, and `48.dp x 48.dp` dismiss or retry target.
3. **Dimensions & Spacing:** Full content width at the top of the screen below the app bar.
4. **Interaction Behavior:** Dismissible for transient success messages; persistent with a `"Retry"` action while offline or when a section read fails.
5. **States:** Offline Notice, Auth Error, Server/Contract Error, Success Confirmation.
6. **When to use / not use:** Place inline at the top of the workspace so it never covers bottom action buttons.

### 13. Loading, Empty, Error & Success States
Every data-driven screen and section must implement all four states explicitly:
- **Loading:** Show a contextual label (`"Checking your session…"`, `"Loading pick-up coordinates…"`, `"Preparing receipt…"`) paired with a non-blocking `LinearProgressIndicator` when cached data is already visible, or a clean centered card when initial data is absent.
- **Empty:** Name why the view is empty and provide a direct action when applicable (e.g., `"No active shipments. Book a pickup from the Home tab to request an office quotation."` with a `"Book a shipment"` button, or `"No trips assigned. You'll be notified when the office assigns your next delivery."`). Compact padding (`32.dp`-`48.dp` vertical), never a `480.dp` tall blank box.
- **Error:** State what failed, whether cached records are still shown, and provide an immediate recovery button (`"Trip details unavailable. Refresh to confirm current assignments."` + `"Retry assignments"`).
- **Success:** Confirm the exact outcome and next stage (`"Booking submitted! Reference TNW-..."` or `"Proof submitted. Office review determines final completion."`).

---

## F. Touch Interaction & Ergonomics

1. **Minimum Touch Target Size:** Every interactive element (`Button`, `IconButton`, `TextButton`, `FilterChip`, `Switch`, `TanawTextLink`, dismiss `'×'` buttons, and camera badge buttons) must have a minimum touch area of **`48.dp x 48.dp`** (Android accessibility standard, exceeding the 44px web baseline). Use `Modifier.minimumInteractiveComponentSize()` or `sizeIn(minWidth = 48.dp, minHeight = 48.dp)` on compact visual icons.
2. **Target Spacing:** Maintain at least `8.dp` clear space between adjacent touch targets (such as `"Discard"` and `"Resume Draft"`, or direction controls).
3. **Thumb-Friendly Placement:**
   - Primary workflow commitments (`"Continue"`, `"Confirm Booking"`, `"START ROUTE AND SHARE LOCATION"`, `"I'VE ARRIVED AT PICK-UP"`, `"MARK AS DELIVERED"`, `"Confirm Location"`) sit in anchored bottom bars or at the bottom of the primary cockpit card within easy one-handed thumb reach.
   - Frequent navigation tabs sit in the bottom navigation bar.
4. **Gesture Interactions:**
   - **Map Pan/Zoom:** Confined to `FullScreenLocationPicker` and bounded `220.dp` `ShipmentMap` cards. Provide explicit `"Show route map"` / `"Hide route map"` controls or keep the map inside a dedicated tab/card so vertical page scrolling never gets trapped inside the map canvas.
   - **Signature Pad (`DriverProofForm`):** Uses `pointerInput` drag capture inside a dedicated bordered canvas with an explicit `"Clear signature"` button and scroll-lock while drawing.
5. **Touch Feedback:** All clickable surfaces must use Material 3 ripple (`Surface(onClick = ...)` or `Modifier.clickable`) and distinct disabled visuals (`0.38f` alpha) so taps never feel ignored.
6. **Keyboard & Input Handling:**
   - Every scrollable form screen and sheet must apply `.imePadding()` so the on-screen keyboard never covers the focused input or the submit button.
   - Use `BringIntoViewRequester` when validation errors or sub-panels open dynamically.
7. **Destructive & Irreversible Action Confirmation:**
   - **Customer Shipment Cancellation:** Requires opening `CustomerCancellationDialog` and selecting/entering a required cancellation reason before mutating `public.shipments`.
   - **Driver Unsaved/Unconfirmed Drafts:** Closing `DriverProofForm` or `DriverFuelForm` with unsaved or unconfirmed data triggers an explicit confirmation `AlertDialog` (`"Discard proof draft?"` / `"Close unconfirmed fuel record?"`).
   - **Driver Milestone Actions:** Geofence-gated (`50m` radius with fresh GPS) for pickup arrival and delivery completion so accidental taps in transit cannot prematurely advance the trip stage.

---

## G. Navigation, Information Architecture & HCI Screen Blueprints

### 1. Primary Navigation Pattern
Active navigation is state-driven via `MobileWorkspaceViewModel` (`state.page`) and role-scoped workspaces:
- **Unauthenticated Flow (`state.page`):**
  - `"login"` → `LoginScreen`
  - `"signup"` → `CreateAccountScreen`
  - `"verify_phone"` / `"verify"` → Unified OTP verification card screen (replacing raw `FormPage`)
  - `"recover"` → `ForgotPasswordCardScreen`
  - `"recover_code"` → `VerifyRecoveryCodeScreen`
- **Customer Workspace (`role == "customer"`):**
  - Anchored `TanawTopBar` + 5-tab `NavigationBar`: `Home` (Booking Flow) · `Active` (Live Shipments) · `History` (Completed/Cancelled) · `Alerts` (Notifications) · `Profile` (Account, Addresses, SMS, Password).
- **Driver Workspace (`role == "driver"`):**
  - Anchored `DriverTopBar` (never hidden on `Navigate`) + 4-tab `NavigationBar`: `Home` (Roster Status & Active Assignments) · `Navigate` (Active Trip Cockpit) · `Trips` (Period Summary & Trip History) · `Profile` (Credentials, Assigned Truck, Compliance & Support).

### 2. HCI Screen Redesign Blueprints (Fixing Current UX Violations)

#### Blueprint 1: Customer `ShipmentDetails` (Replacing the Single-Card Raw Field Dump)
- **Current HCI Violation:** `ShipmentDetails` in `MobileWorkspaceApp.kt` dumps 19+ raw fields, raw coordinates (`latitude, longitude`), cancellation controls, `ShipmentMap`, raw delivery timestamps, timeline strings, and POD links inside **one giant `WorkspaceCard`** with an inline scrolling `"← Back"` text button.
- **Target HCI Architecture:**
  1. **Pinned `TanawSubScreenTopBar`:** Left Back button (`48.dp`), tracking number title (`TNW-...`), and trailing `StatusBadge`. Bottom navigation bar is hidden while viewing details to maximize vertical space.
  2. **Card 1: Live Tracking & ETA Hero Card:** `ShipmentMap` (`220.dp`), `GpsFreshnessBadge` (`Fresh GPS` / `Stale GPS`), human-readable last update time, and Scheduled vs. Live ETA comparison (hiding raw decimal `latitude, longitude` strings).
  3. **Card 2: Route & Cargo Summary Card:** Visual two-stop route connector (`PICKUP` → `DESTINATION` with full wrapped addresses), cargo description, weight (`kg`), and driver notes/delay reason if present.
  4. **Card 3: Quotation & Pricing Card:** Clearly highlights `Quoted Amount` and `Final Amount` (or `"Awaiting office quotation"` badge when pending).
  5. **Card 4: Delivery Milestone Stepper (Progressive Disclosure):** Consolidates the duplicate `"Delivery progress"` and `"Timeline"` sections into a single visual vertical stepper showing completed and upcoming timestamps in PHT, with an expandable `"View full event log"` toggle if additional timeline events exist.
  6. **Card 5: Proof of Delivery & Cancellation Footer:** Displays recorded POD document action rows (`View / download`) when delivered, or the outlined destructive `"Cancel shipment"` button when `canCancelShipment(shipment)` is true.

#### Blueprint 2: Driver `Navigate` Active Trip Cockpit (Replacing the 9-Section Endless Scroll)
- **Current HCI Violation:** `DriverNavigateOverview.kt` and `DriverWorkspace.kt` hide the top bar completely (`if (!(tab == "Navigate" && shipment != null))`), force redundant trip re-selection when switching tabs, display 3 competing full-width external map buttons simultaneously, expose technical jargon (`"OpenRouteService HGV · vehicle limits applied"`), and stack 9 vertical sections (Stage card, Milestone actions, 3 Direction buttons, Route status, Truck ban advisory, Map, 6-step list, Expenses/Incidents/Fuel/POD records, and the full Chat thread) on a single scrolling page.
- **Target HCI Architecture:**
  1. **Auto-Bind Active Trip:** When the driver taps the `Navigate` tab, automatically select the currently active in-progress trip (or the sole assigned trip) instead of showing a duplicate `"Choose a trip to navigate"` list. If multiple trips are assigned, provide a compact `"Switch trip"` pill in the top bar.
  2. **Pinned Cockpit Top Bar:** Remains visible at all times on `Navigate`, displaying `TNW-...`, current `StatusBadge`, and `48.dp` icon buttons for **Office Chat** (with unread badge, opening the chat sheet/view) and **Alerts**.
  3. **Primary Stage Cockpit Card (Above the Fold):**
     - Current stage header (`Stage 2 of 4 · En route to pick-up`) and target address (Pickup address during `assigned`/`en_route_to_pickup`/`arrived_at_pickup`/`loading`; Destination address during `in_transit`/`arrived_at_destination`).
     - GPS tracking status chip + geofence readiness banner (`"Within 50m geofence"` or distance remaining).
     - **Single Primary Milestone CTA** (`START ROUTE AND SHARE LOCATION`, `I'VE ARRIVED AT PICK-UP`, `CARGO LOADED - START TRANSIT`, or `MARK AS DELIVERED`).
     - **Single Contextual External Navigation Button** (`OutlinedButton`: `"Open pickup in Google Maps"` or `"Open destination in Google Maps"` based on current stage), with a compact `"More route options"` expander for the secondary stops.
  4. **Quick Operational Action Strip:** A compact 2x2 or 4-item action row below the cockpit card for secondary field tasks:
     - `Record Fuel` · `Add Expense` · `Report Issue` · `Message Office` (each opening its dedicated dialog/bottom sheet instead of rendering full forms inline).
  5. **Segmented Below-the-Fold Panels (`FilterChip` / Segmented Strip):**
     - **Tab 1: `Route & Advisory`** (Map preview, plain-language HGV truck route status, and MMDA/provincial truck ban advisories).
     - **Tab 2: `Checkpoints`** (6-step delivery progress checklist with PHT timestamps).
     - **Tab 3: `Trip Logs`** (Recorded fuel logs, expenses, incidents, and POD attachments with a single header sync icon).

#### Blueprint 3: Unified Auth, OTP Verification & Onboarding Profile Gate
- **Current HCI Violation:** `verify` (Email OTP), `verify_phone` (Phone OTP), `loading`, and `"Complete your profile"` in `MobileWorkspaceApp.kt` use the raw developer `FormPage` scaffold with plain text inputs, no back button, and two competing full-width buttons (`"Enable shipment SMS"` / `"Disable shipment SMS"`).
- **Target HCI Architecture:**
  1. **Unified Verification Screen (`verify` & `verify_phone`):** Uses the same branded card hierarchy and 6-cell `OtpCodeInputRow` as `VerifyRecoveryCodeScreen`, showing the masked target email/phone, a primary `"Verify code"` button, a secondary `"Resend code"` action, and a clear `"← Back to sign in"` button.
  2. **Streamlined `"Complete your profile"` Onboarding Gate:** Focused card asking only for required onboarding fields (`Full name` and `Phone number` if missing) plus a single optional Material `Switch` row for `"Receive shipment SMS updates"`, followed by one primary `"Save and continue"` CTA and a secondary `"Sign out"` escape hatch.

#### Blueprint 4: Centralized Sync vs. Manual Refresh Proliferation
- **Current HCI Violation:** Every section (`CustomerTopBar`, `ShipmentDetails`, `DriverWorkspace`, `DriverProfile`, `DriverTrips`, `DriverRecords`, `DriverConversation`) renders its own manual `"Refresh ..."` text button.
- **Target HCI Architecture:**
  - Root tabs provide a single compact sync `IconButton` in the anchored top bar (with a `LinearProgressIndicator` along the bottom edge of the top bar while `state.busy` is true).
  - Section-level `"Retry"` buttons appear **only** inside error banners when a network fetch fails (`sectionError != null`).

---

## H. Liveliness Dials

- **ENERGY: `2 / 5` (Calm, High-Contrast Operational Focus)**
  - **Practical implication:** Visual weight comes from crisp typography (`Manrope` Bold/ExtraBold), clear card borders, and high-contrast Navy (`#0B2A4A`) + Safety Orange (`#C2410C`) accents at decision points. No decorative gradients, background grids, or glowing containers.
- **RHYTHM: `3 / 5` (Structured Functional Variation)**
  - **Practical implication:** Screens alternate purposefully between hero summary banners (Driver stage cockpit card, Customer route summary, Trip period metrics), interactive map canvases, and compact list cards. Spacing differentiates primary sections (`16.dp`-`20.dp`) from internal card rows (`8.dp`-`12.dp`).
- **MOTION: `2 / 5` (Purposeful State & Feedback Transitions Only)**
  - **Practical implication:** Motion is restricted to functional feedback: button loading spinners, top linear progress bars during network sync, bottom sheet slide-ins, and camera pan animations when selecting a map pin. **Zero endless pulsing dots or decorative looping animations.**

---

## I. Responsive Adaptability

1. **Edge-to-Edge & System Insets:**
   - `MainActivity` calls `enableEdgeToEdge()`.
   - Every root `Scaffold`, `FullScreenLocationPicker`, and `DriverTransaction` dialog must apply `statusBarsPadding()`, `navigationBarsPadding()`, and `imePadding()` so content never collides with camera cutouts, status bars, 3-button/gesture navigation bars, or the soft keyboard.
   - `WindowCompat.getInsetsController` updates status and navigation bar icon contrast (`!darkTheme`).
2. **Small & Large Phones (320dp to 480dp+ width):**
   - Never hardcode pixel widths on cards, buttons, or text fields. Use `fillMaxWidth()`, `weight(1f)`, and `FlowRow` (for filter chips) so layouts reflow cleanly on narrow 320dp-360dp devices without horizontal clipping.
   - Replace fixed tall empty states (`heightIn(min = 480.dp)`) with content-wrapped padding so small phones and landscape viewports do not require scrolling past blank space.
3. **Orientation & Foldable Continuity:**
   - All screens must remain scrollable (`verticalScroll` or `LazyColumn`) in landscape orientation.
   - Map pickers and signature pads dynamically measure their available container (`onSizeChanged` / `doOnLayout`) rather than assuming portrait dimensions.
4. **System Font Scaling (Up to 200%):**
   - All font sizes use scalable pixels (`sp`).
   - Containers holding text must use `heightIn(min = ...)` instead of fixed `.height(...)` wherever text may wrap at 200% font scale, and rows pairing a label with a badge must allow text to wrap or ellipsize gracefully without overlapping the badge.

---

## J. Accessibility

1. **Contrast Compliance (WCAG 2.2 AA):**
   - **Normal Text (< 18sp / < 14sp bold):** Minimum **4.5:1** against its immediate background (`#0F172A` on `#FFFFFF` = 17.85:1; `#475569` on `#F1F5F9` = 6.75:1; `#C2410C` on `#FFFFFF` = 5.18:1).
   - **Large Text & UI Boundaries (WCAG 1.4.11):** Minimum **3.0:1** for input borders (`#64748B` on `#FFFFFF` = 4.76:1), button outlines, and focus indicators.
2. **Non-Color-Only Status Communication:**
   - Every semantic state (`StatusBadge`, `GpsFreshnessBadge`, compliance status, password strength, form validation error) pairs its color tint with an explicit text label (`"Fresh GPS"`, `"Correction requested"`, `"EXPIRED"`, `"WEAK"`) and, where applicable, a distinct icon shape (such as the inner truck glyph on the green vehicle map marker vs. circular dots on Pickup/Destination pins).
3. **Screen Reader & TalkBack Semantics:**
   - Interactive composite cards (`LocationSelectorField`, `VehicleSelectionCard`, `RouteSummaryCard`) use `Modifier.semantics(mergeDescendants = true)` with explicit `Role.Button` or `Role.RadioButton` and a complete `contentDescription`.
   - Icon-only controls (Back arrow, Notification bell, Password visibility toggle, Dismiss banner, Map recenter FAB, Trip filter) carry a descriptive `contentDescription`. Purely decorative icons next to text labels pass `contentDescription = null`.
4. **Touch Target Accessibility:**
   - Minimum `48.dp x 48.dp` hit target on all interactive controls with at least `8.dp` separation.
5. **Keyboard & Focus Order:**
   - All form fields define logical `ImeAction.Next` / `ImeAction.Done` focus traversal matching visual top-to-bottom order, and dialogs/sheets support hardware Back / Escape dismissal.
6. **Reduced Motion:**
   - Because Tanaw avoids decorative looping animations (`MOTION: 2/5`), users with motion sensitivity experience no parallax, auto-playing carousels, or perpetual pulses.
