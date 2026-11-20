# Backend Requests for `mabutol-trucking-react`

This document tracks requested database migrations, RPC signature extensions, and backend enhancements identified during Tanaw Mobile development. As specified in `AGENTS.md`, the backend source of truth lives in the external repository `mabutol-trucking-react`. All client code in Tanaw Mobile implements forward-compatible and backward-compatible fallbacks so that these requests are non-blocking enhancements.

---

## 1. Request C03: Native Structured Barangay & Province in `public.customers` and `mobile_save_profile`

### Motivation
Customer addresses in the Philippines consist of four essential hierarchical components:
1. House / Building / Street Address
2. Barangay (`Brgy.`)
3. City / Municipality
4. Province

Currently:
- `public.customers` has `address text`, `city text`, and `province text DEFAULT 'Nueva Ecija'` (note: `barangay` has no dedicated column).
- `mobile_workspace()` (migration `20261024155504_mobile_workable_system.sql`) only selects `id, name, phone, address, city` from `public.customers`, omitting `province` and `barangay`.
- `mobile_save_profile(p_name text, p_phone text, p_address text, p_city text)` only accepts 4 parameters and writes `address = p_address, city = p_city`.

The Android client currently uses `CustomerAddressRules` to format structured components cleanly into `p_address` (`"<street>, Brgy. <barangay>"`) and `p_city` (`"<city>, <province>"`) without violating any database length constraints (`address <= 500`, `city <= 100`).

To support first-class column storage and queryability across dispatch dashboards and driver manifests, the following backend changes are requested.

### Proposed Schema Changes

```sql
-- 1. Add dedicated barangay column to public.customers if not exists
ALTER TABLE public.customers
    ADD COLUMN IF NOT EXISTS barangay text;

-- 2. Update mobile_workspace RPC to return barangay and province in customer JSON
-- Inside mobile_workspace():
-- 'customer', (
--     SELECT json_build_object(
--         'id', c.id,
--         'name', c.name,
--         'phone', c.phone,
--         'address', c.address,
--         'barangay', c.barangay,
--         'city', c.city,
--         'province', c.province
--     )
--     FROM public.customers c
--     WHERE c.id = public.my_customer_id()
-- )

-- 3. Extend mobile_save_profile with optional p_barangay and p_province parameters
CREATE OR REPLACE FUNCTION public.mobile_save_profile(
    p_name text,
    p_phone text,
    p_address text,
    p_city text,
    p_barangay text DEFAULT NULL,
    p_province text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_customer_id uuid;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED';
    END IF;

    UPDATE public.users
    SET full_name = trim(p_name)
    WHERE id = v_user_id;

    SELECT id INTO v_customer_id
    FROM public.customers
    WHERE user_id = v_user_id;

    IF v_customer_id IS NOT NULL THEN
        UPDATE public.customers
        SET
            name = trim(p_name),
            address = trim(p_address),
            city = trim(p_city),
            barangay = COALESCE(trim(p_barangay), barangay),
            province = COALESCE(trim(p_province), province),
            updated_at = now()
        WHERE id = v_customer_id;
    END IF;

    RETURN jsonb_build_object('success', true);
END;
$$;
```

### Client Compatibility
The Android client's `CustomerAddressRules.parseFromSaved` checks for `explicitBarangay` and `explicitProvince` in the returned JSON object and falls back gracefully to parsing `"Brgy. ..."` and `", <Province>"` from `address` and `city` strings, ensuring 100% interoperability before and after this migration.

---

## 2. Request D05: HGV Physical Truck Measurements, Tare Weight & Truck-Ban Metadata in `public.trucks`, `mobile_workspace`, and `delivery-routing`

### Motivation
Commercial heavy goods vehicles (HGVs) in the Philippines operate under strict infrastructure limits and municipal regulations:
1. **Low Bridges & Flyovers:** Underpass clearances across Metro Manila and Luzon arterial routes (e.g. EDSA, C-5, Quirino Highway) require height and width clearance enforcement.
2. **Gross Vehicle Weight & Axle Limits:** Anti-overloading enforcement (DPWH / LTO) regulates gross vehicle weight (`tare_weight_kg + cargo_weight_kg`) and per-axle loading.
3. **UVVRP Number Coding & Truck Bans:** Peak-hour restrictions (06:00–10:00 and 17:00–21:00 PHT) and day-of-week number coding apply to trucks based on plate numbers.
4. **Routing Optimization:** The Mapbox/OSRM routing engines in the Edge Function `delivery-routing` need vehicle dimensions (`height_m`, `width_m`, `length_m`, `gross_weight_kg`, `axle_count`) to avoid impassable bridges, narrow barangay roads, and illegal turn restrictions.

Currently:
- `public.trucks` has `plate_number`, `model`, `capacity_kg`, and `status`, but lacks physical HGV dimensions, tare weight, and axle counts.
- `public.vehicle_types` lacks default physical dimensions.
- `mobile_workspace()` subqueries for `shipments` omit route geometry, pickup/dropoff coordinates, and distance metrics (`pickup_lat, pickup_lng, dropoff_lat, dropoff_lng, distance_km, route_duration_minutes, route_geometry`), and `deliveries` omit `truck_id`.

### Proposed Schema Changes

```sql
-- 1. Add physical HGV dimension and weight columns to public.trucks
ALTER TABLE public.trucks
    ADD COLUMN IF NOT EXISTS height_m numeric(4,2),
    ADD COLUMN IF NOT EXISTS width_m numeric(4,2),
    ADD COLUMN IF NOT EXISTS length_m numeric(5,2),
    ADD COLUMN IF NOT EXISTS tare_weight_kg numeric(10,2),
    ADD COLUMN IF NOT EXISTS gross_weight_kg numeric(10,2),
    ADD COLUMN IF NOT EXISTS axle_count smallint DEFAULT 2;

-- 2. Add default dimensional specifications to public.vehicle_types
ALTER TABLE public.vehicle_types
    ADD COLUMN IF NOT EXISTS default_height_m numeric(4,2),
    ADD COLUMN IF NOT EXISTS default_width_m numeric(4,2),
    ADD COLUMN IF NOT EXISTS default_length_m numeric(5,2);

-- 3. Update mobile_workspace() RPC:
-- a) Include pickup/dropoff coordinates, distance, and route geometry in shipments subquery:
--    SELECT json_agg(
--        json_build_object(
--            'id', s.id,
--            'tracking_number', s.tracking_number,
--            'status', s.status,
--            'weight_kg', s.weight_kg,
--            'pickup_address', s.pickup_address,
--            'dropoff_address', s.dropoff_address,
--            'pickup_lat', s.pickup_lat,
--            'pickup_lng', s.pickup_lng,
--            'dropoff_lat', s.dropoff_lat,
--            'dropoff_lng', s.dropoff_lng,
--            'distance_km', s.distance_km,
--            'route_duration_minutes', s.route_duration_minutes,
--            'route_geometry', s.route_geometry
--        )
--    )
-- b) Include truck_id in deliveries subquery:
--    SELECT json_agg(
--        json_build_object(
--            'id', d.id,
--            'status', d.status,
--            'truck_id', d.truck_id,
--            'assigned_at', d.assigned_at
--        )
--    )
-- c) Include height_m, width_m, length_m, tare_weight_kg, gross_weight_kg, axle_count in driver_trucks subquery.
```

### Client Compatibility
The Android client implements `TruckRestrictionRules.kt`:
- Evaluates truck capacity, cargo weight, gross weight calculation (`tare + cargo`), physical dimensions (`height_m`, `width_m`, `length_m`), and axle counts when available.
- When dimensional data is absent, marks `dimensionsVerified = false` and provides safe fallback advisories without breaking the UI.
- Evaluates UVVRP Number Coding (last plate digit to day-of-week) and Metro Manila peak truck-ban windows (06:00–10:00 and 17:00–21:00 PHT, Mon–Sat) purely on-device.

---

## 3. Request C10: Customer Self-Service Shipment Cancellation Table Policy & RLS

### Motivation
Customers need to be able to cancel shipments directly from the mobile app (and web client) when plans change, provided the driver is not yet on the way.
In PostgreSQL, `public.shipments` enforces a check constraint:
```sql
CONSTRAINT "shipments_check" CHECK ((("status" <> 'cancelled'::"public"."shipment_status") OR ("cancelled_reason" IS NOT NULL)))
```
Furthermore:
1. Customers must only be allowed to cancel their own shipments (`customer_id = my_customer_id()`).
2. Cancellation must be restricted to pre-transit states (`pending`, `booked`, or `assigned` before the driver has departed or started their route).
3. If an `assigned` shipment has an active `deliveries` record, setting the shipment to `cancelled` should also cancel the delivery (`deliveries.status = 'cancelled'`) so the truck/driver are freed back to `available` and the one-active-delivery-per-shipment index remains consistent.

### Proposed Schema / RLS Changes

```sql
-- Option A: Direct RLS UPDATE policy on public.shipments for customers
CREATE POLICY "customer cancels own eligible shipment"
ON public.shipments
FOR UPDATE
TO authenticated
USING (
    customer_id = public.my_customer_id()
    AND status IN ('pending', 'booked', 'assigned')
    AND NOT EXISTS (
        SELECT 1 FROM public.deliveries d
        WHERE d.shipment_id = public.shipments.id
          AND d.status <> 'cancelled'
          AND (
              d.route_started_at IS NOT NULL
              OR d.pickup_arrived_at IS NOT NULL
              OR d.departed_at IS NOT NULL
              OR d.status IN ('loading', 'in_transit', 'delivered')
          )
    )
)
WITH CHECK (
    status = 'cancelled'
    AND cancelled_reason IS NOT NULL
    AND length(trim(cancelled_reason)) >= 3
);

-- Option B (Recommended): Atomic RPC for customer cancellation
CREATE OR REPLACE FUNCTION public.mobile_cancel_shipment(
    p_shipment uuid,
    p_reason text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_customer_id uuid := public.my_customer_id();
    v_shipment record;
    v_delivery record;
    v_reason text := trim(p_reason);
BEGIN
    IF v_customer_id IS NULL THEN
        RAISE EXCEPTION 'ACTIVE_ACCOUNT_REQUIRED';
    END IF;

    IF v_reason IS NULL OR length(v_reason) < 3 THEN
        RAISE EXCEPTION 'CANCELLATION_REASON_REQUIRED';
    END IF;

    SELECT * INTO v_shipment
    FROM public.shipments
    WHERE id = p_shipment AND customer_id = v_customer_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'SHIPMENT_NOT_FOUND';
    END IF;

    IF v_shipment.status NOT IN ('pending', 'booked', 'assigned') THEN
        RAISE EXCEPTION 'SHIPMENT_NOT_CANCELLABLE';
    END IF;

    -- Check if any active delivery has started route
    SELECT * INTO v_delivery
    FROM public.deliveries
    WHERE shipment_id = p_shipment AND status <> 'cancelled'
    FOR UPDATE;

    IF FOUND THEN
        IF v_delivery.route_started_at IS NOT NULL
           OR v_delivery.pickup_arrived_at IS NOT NULL
           OR v_delivery.departed_at IS NOT NULL
           OR v_delivery.status IN ('loading', 'in_transit', 'delivered') THEN
            RAISE EXCEPTION 'DRIVER_ALREADY_EN_ROUTE';
        END IF;

        UPDATE public.deliveries
        SET status = 'cancelled', updated_at = now()
        WHERE id = v_delivery.id;
    END IF;

    UPDATE public.shipments
    SET status = 'cancelled',
        cancelled_reason = v_reason,
        cancelled_at = now(),
        updated_at = now()
    WHERE id = p_shipment;

    INSERT INTO public.shipment_events (shipment_id, event_type, metadata, occurred_at)
    VALUES (p_shipment, 'cancelled', jsonb_build_object('reason', v_reason, 'cancelled_by', 'customer'), now());

    RETURN jsonb_build_object('success', true, 'status', 'cancelled');
END;
$$;
```

### Client Compatibility
The Android client implements `CustomerCancellationRules.kt` to enforce pre-transit rules and reason requirements on-device, and executes the cancellation via direct PostgREST table update (matching `mabutol-trucking-react` web behavior) with optimistic state updates in [MobileWorkspaceViewModel.kt](file:///C:/Users/Soriano/AndroidStudioProjects/Tanaw_Mobile/app/src/main/java/com/tanaw/app/feature/workspace/viewmodel/MobileWorkspaceViewModel.kt).

---

## 4. Request FTL-01: Scheduled Full Truckload (FTL) Booking, Itemized Quotation, Quote Acceptance & Driver Telemetry Alignment

### Motivation
TANAW operates strictly for **scheduled Full Truckload (FTL)** deliveries (no LTL consolidation and no instant on-demand `"Pick-up now"` dispatch). Bookings require advance scheduling (`min_lead_time_hours` of `24` for standard trucks and `48` for Refrigerated Van / 10-Wheeler Flatbed, up to `14` days ahead), an office quotation with itemized fare components (`linehaul_amount`, `toll_amount`, `mobilization_fee`, `quote_expires_at`), customer quotation confirmation before dispatch, and driver telemetry/status hooks that feed backend chained-dispatch triggers.

### Expected Schema & RPC Contract

1. **`public.vehicle_types`:**
   - `min_lead_time_hours integer NOT NULL DEFAULT 24` (`48` for Refrigerated Van and 10-Wheeler Flatbed)
   - `loading_buffer_minutes integer NOT NULL DEFAULT 180`
2. **`public.pricing_rates`:**
   - `vehicle_type_id uuid`, `base_rate numeric`, `per_km_rate numeric`, `per_kg_rate numeric`, `min_charge numeric`, `free_deadhead_km numeric`, `deadhead_per_km_rate numeric`
3. **`public.shipments`:**
   - `scheduled_departure timestamptz` (required on booking creation)
   - `estimated_arrival timestamptz` (`scheduled_departure + route_duration_minutes + vehicle_types.loading_buffer_minutes`)
   - `linehaul_amount numeric` (strictly `pickup -> dropoff`)
   - `toll_amount numeric`
   - `mobilization_fee numeric` (optional surcharge when deadhead exceeds `free_deadhead_km`)
   - `deadhead_km numeric` (internal-only; **never** returned to or rendered in customer UI)
   - `quoted_amount numeric`, `override_amount numeric`, `final_amount numeric`, `quote_expires_at timestamptz`
4. **`mobile_workspace()` & Live Table Enrichment:**
   - The mobile app queries live `public.vehicle_types` (`min_lead_time_hours`, `loading_buffer_minutes`), `public.pricing_rates` (`base_rate`, `per_km_rate`, `per_kg_rate`, `min_charge`, `free_deadhead_km`, `deadhead_per_km_rate`), and `public.shipments` (`linehaul_amount`, `toll_amount`, `mobilization_fee`, `quoted_amount`, `override_amount`, `final_amount`, `quote_expires_at`, `distance_km`, `route_duration_minutes`, omitting `deadhead_km`) directly under RLS while falling back gracefully to `mobile_workspace()` fields.
5. **`mobile_create_request(p_data jsonb)` & `stamp_shipment_estimated_arrival` Trigger:**
   - Accepts `scheduled_departure` (and `departure` alias), `estimated_arrival`, `distance_km` (and `route_distance_km` alias), `route_duration_minutes`, and optional `estimated_linehaul`.
   - Enforces `scheduled_departure` within `[now() + min_lead_time_hours, now() + interval '14 days']` and initializes shipment status to `pending` (live flow: `pending` → `quoted` → `booked`).
   - `trg_stamp_shipment_estimated_arrival` computes `estimated_arrival = scheduled_departure + route_duration_minutes + vehicle_types.loading_buffer_minutes` when `estimated_arrival` is null.
6. **Quote Acceptance & Office Review (`pending` → `quoted` → `booked`):**
   - `public.shipment_status` includes `'quoted'` (`ALTER TYPE public.shipment_status ADD VALUE IF NOT EXISTS 'quoted'`).
   - `review_booking_request` approves `'pending'` or `'quoted'` requests into `status = 'booked'`.
   - `mobile_accept_shipment_quote(p_shipment uuid)` transitions customer-accepted quotes from `'quoted'` to `'booked'`.
7. **Driver Tracking & Chained-Dispatch Triggers (`20261120120000_ftl_alignment_chained_dispatch.sql`):**
   - `driver_record_position(p_delivery, p_sample, p_data)` inserts `{truck_id, delivery_id, latitude, longitude, recorded_at}` into `public.tracking_logs`, firing `trg_sync_truck_gps_from_tracking` to sync `trucks.current_lat`, `current_lng`, and `last_location_at`.
   - `mobile_driver_action`, `driver_arrive_at_pickup`, `driver_submit_pod`, and `/functions/v1/delivery-routing` (`mode = 'eta'`) update `public.deliveries` (`delivered`, `completed`, etc.) and `public.delivery_route_eta` (`leg = 'destination'`), firing `trg_sync_truck_availability_on_delivery` and `trg_sync_truck_eta_from_route` to maintain `trucks.next_available_address`, `next_available_lat`, `next_available_lng`, and `next_available_at`.

---

## 5. Request C11: Return `avatar_url` in `mobile_workspace()` Profile Object & Optional FCM Push Webhook for `public.notifications`

### Motivation
1. **Customer Profile Photo (`public.users.avatar_url`):**
   - `public.users` already defines `avatar_url text` and allows authenticated users to update their own profile row under RLS (`"update own profile"`).
   - However, `mobile_workspace()` builds `'profile', jsonb_build_object('id', me.id, 'full_name', me.full_name, 'email', me.email, 'phone', me.phone, 'role', me.role, 'created_at', me.created_at)` without including `'avatar_url', me.avatar_url`.
   - The Android client currently queries `public.users(avatar_url)` in `readCustomerFtlData` and caches the 256x256 JPEG in `CustomerAvatarStore`. Returning `'avatar_url', me.avatar_url` directly in `mobile_workspace()` eliminates the extra query round-trip.
2. **Device Push Notifications When App Process Is Terminated (Optional FCM Extension):**
   - The Android client now posts heads-up Android system notifications (`WorkspaceDeviceNotifier`, channel `tanaw_workspace_alerts`) whenever new unread `public.notifications` rows arrive over Supabase Realtime or workspace polling, and deep-links notification taps directly into the shipment or Alerts screen.
   - If the business later requires wake-from-killed-process push delivery when the app is completely swiped away, the backend can add a `public.user_push_tokens` table and a database webhook on `public.notifications` `AFTER INSERT` that dispatches an FCM data payload containing `notification_id`, `shipment_id`, `title`, and `body`.




