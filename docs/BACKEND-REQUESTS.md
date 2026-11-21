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
   - `mobile_driver_action`, `driver_arrive_at_pickup`, `driver_submit_pod`, and `/functions/v1/delivery-routing` (`mode = 'eta'`) update `public.deliveries` (`delivered`, etc.) and `public.delivery_route_eta` (`leg = 'destination'`), firing `trg_sync_truck_availability_on_delivery` and `trg_sync_truck_eta_from_route` to maintain `trucks.next_available_address`, `next_available_lat`, `next_available_lng`, and `next_available_at`.

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

---

## 6. Request D06: Driver Face-to-Face Full Payment Collection & Strict Admin Verification Gate

### Motivation
Mabutol Trucking operates a strict **payment-first** workflow after cargo loading:
1. Final shipment charges (`public.shipments.final_amount`) are always finalized by the office before dispatch.
2. Right after cargo is loaded onto the truck (`deliveries.status = 'loading'`), the driver and helpers bill the customer face-to-face for the **full remaining balance** (no partial settlements from the driver).
3. The customer signs a physical paper receipt after paying (for all payment methods: Cash, GCash).
4. To prevent collusion or fake payment proofs, every driver-submitted payment starts as `status = 'pending'` and requires **manual office verification** before the driver is allowed to depart (`loading` → `in_transit`).
5. Once an admin clicks **Verify** in the web Payment Ledger, the driver's **Start Transit** button unlocks in real time so the driver can depart and record an accurate `departed_at`. If the customer already prepaid at the office and the payment is `verified` (`balance <= 0`), the gate is already satisfied and the driver does not double-bill the customer.

Currently:
- `public.payments` exists with `amount`, `method`, `reference_number`, `proof_path`, `status` (`pending`, `verified`, `rejected`, `refunded`), `paid_at`, `verified_by`, `verified_at`, `notes`, and `created_by`, plus the `payments_check` constraint requiring `verified_by` and `verified_at` when `status = 'verified'`.
- `public.payments` has no `client_request_id` (for idempotent mobile retries) and no dedicated `rejection_reason` column (separate from driver `notes`).
- Drivers have no RLS `SELECT` policy on `public.payments` and no `driver_record_payment` RPC.
- `driver_confirm_cargo_loaded` / `mobile_driver_action('loaded')` does not yet enforce server-side payment verification before transitioning `loading` → `in_transit`.
- The web Payment Ledger currently supports manual **Record Payment** only, and needs **View Receipt**, **Verify**, and **Reject (with reason)** actions for `pending` rows.

### Proposed Schema, RLS & Realtime Changes

```sql
-- 1. Add idempotency key and separate rejection_reason to public.payments
ALTER TABLE public.payments
    ADD COLUMN IF NOT EXISTS client_request_id uuid,
    ADD COLUMN IF NOT EXISTS rejection_reason text;

CREATE UNIQUE INDEX IF NOT EXISTS payments_shipment_client_request_uidx
    ON public.payments (shipment_id, client_request_id)
    WHERE client_request_id IS NOT NULL;

-- 2. Allow assigned drivers to read payment rows for their assigned shipments (needed for gate status & Realtime)
DROP POLICY IF EXISTS "driver reads assigned shipment payments" ON public.payments;
CREATE POLICY "driver reads assigned shipment payments"
ON public.payments
FOR SELECT
TO authenticated
USING (
    shipment_id IN (SELECT public.my_driver_shipment_ids())
);

-- 3. Ensure public.payments is in the Realtime publication so Verify/Reject pushes immediately to mobile
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = 'payments'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.payments;
    END IF;
END $$;

-- 4. Storage policies on delivery-documents for driver payment receipt uploads & reads
DROP POLICY IF EXISTS "driver uploads payment receipt for loading delivery" ON storage.objects;
CREATE POLICY "driver uploads payment receipt for loading delivery"
ON storage.objects
FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'delivery-documents'
    AND name ~ '^[0-9a-f-]{36}/payment-[0-9a-f-]{36}\.jpg$'
    AND EXISTS (
        SELECT 1
        FROM public.deliveries d
        WHERE d.id::text = split_part(name, '/', 1)
          AND d.driver_id = public.my_driver_id()
          AND d.status = 'loading'
    )
);

DROP POLICY IF EXISTS "authorized users read recorded payment receipts" ON storage.objects;
CREATE POLICY "authorized users read recorded payment receipts"
ON storage.objects
FOR SELECT
TO authenticated
USING (
    bucket_id = 'delivery-documents'
    AND EXISTS (
        SELECT 1
        FROM public.payments p
        WHERE p.proof_path = storage.objects.name
          AND (
              public.is_admin()
              OR p.shipment_id IN (SELECT public.my_driver_shipment_ids())
          )
    )
);
```

### Proposed RPC Contracts

```sql
-- 1. Driver records full payment received after loading (always status = 'pending')
CREATE OR REPLACE FUNCTION public.driver_record_payment(
    p_delivery_id uuid,
    p_request_id uuid,
    p_method public.payment_method,
    p_reference_number text DEFAULT NULL,
    p_proof_path text DEFAULT NULL,
    p_notes text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_driver_id uuid;
    v_delivery public.deliveries%ROWTYPE;
    v_shipment public.shipments%ROWTYPE;
    v_existing public.payments%ROWTYPE;
    v_verified_paid numeric(12,2);
    v_balance numeric(12,2);
    v_ref text := nullif(btrim(coalesce(p_reference_number, '')), '');
    v_notes text := nullif(btrim(coalesce(p_notes, '')), '');
    v_proof text := nullif(btrim(coalesce(p_proof_path, '')), '');
    v_payment public.payments%ROWTYPE;
    v_customer_user_id uuid;
BEGIN
    IF v_uid IS NULL OR NOT EXISTS (
        SELECT 1 FROM public.users WHERE id = v_uid AND is_active AND role = 'driver'
    ) THEN
        RAISE EXCEPTION 'ACTIVE_DRIVER_REQUIRED' USING ERRCODE = '42501';
    END IF;

    SELECT id INTO v_driver_id FROM public.drivers WHERE user_id = v_uid LIMIT 1;
    IF v_driver_id IS NULL THEN
        RAISE EXCEPTION 'ACTIVE_DRIVER_REQUIRED' USING ERRCODE = '42501';
    END IF;

    IF p_delivery_id IS NULL OR p_request_id IS NULL THEN
        RAISE EXCEPTION 'INVALID_PAYMENT_REQUEST';
    END IF;

    -- Serialize concurrent retries for the same delivery
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_delivery_id::text, 0));

    SELECT * INTO v_delivery
    FROM public.deliveries
    WHERE id = p_delivery_id AND driver_id = v_driver_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'DELIVERY_NOT_FOUND';
    END IF;

    IF v_delivery.status <> 'loading' THEN
        RAISE EXCEPTION 'INVALID_PAYMENT_STATE';
    END IF;

    SELECT * INTO v_shipment
    FROM public.shipments
    WHERE id = v_delivery.shipment_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'SHIPMENT_NOT_FOUND';
    END IF;

    -- Idempotent replay check on client_request_id
    SELECT * INTO v_existing
    FROM public.payments
    WHERE shipment_id = v_shipment.id AND client_request_id = p_request_id;

    IF FOUND THEN
        IF v_existing.created_by IS DISTINCT FROM v_uid
           OR v_existing.method IS DISTINCT FROM p_method
           OR v_existing.reference_number IS DISTINCT FROM v_ref
           OR v_existing.proof_path IS DISTINCT FROM v_proof THEN
            RAISE EXCEPTION 'REQUEST_ALREADY_SAVED_REFRESH';
        END IF;
        RETURN jsonb_build_object(
            'id', v_existing.id,
            'shipment_id', v_existing.shipment_id,
            'amount', v_existing.amount,
            'method', v_existing.method,
            'status', v_existing.status,
            'paid_at', v_existing.paid_at
        );
    END IF;

    IF v_shipment.final_amount IS NULL OR v_shipment.final_amount <= 0 THEN
        RAISE EXCEPTION 'FINAL_AMOUNT_REQUIRED';
    END IF;

    SELECT coalesce(sum(amount), 0) INTO v_verified_paid
    FROM public.payments
    WHERE shipment_id = v_shipment.id AND status = 'verified';

    v_balance := v_shipment.final_amount - v_verified_paid;
    IF v_balance <= 0 THEN
        RAISE EXCEPTION 'PAYMENT_ALREADY_PAID';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.payments
        WHERE shipment_id = v_shipment.id AND status = 'pending'
    ) THEN
        RAISE EXCEPTION 'PAYMENT_ALREADY_PENDING';
    END IF;

    IF v_proof IS NULL OR v_proof <> (p_delivery_id::text || '/payment-' || p_request_id::text || '.jpg') THEN
        RAISE EXCEPTION 'PAYMENT_PROOF_REQUIRED';
    END IF;

    IF p_method = 'gcash' AND v_ref IS NULL THEN
        RAISE EXCEPTION 'PAYMENT_REFERENCE_REQUIRED';
    END IF;

    INSERT INTO public.payments (
        shipment_id, amount, method, reference_number, proof_path,
        status, paid_at, notes, created_by, client_request_id
    )
    VALUES (
        v_shipment.id, v_balance, p_method, v_ref, v_proof,
        'pending', now(), v_notes, v_uid, p_request_id
    )
    RETURNING * INTO v_payment;

    -- Notify active admin/staff users for immediate review before truck departs
    INSERT INTO public.notifications (user_id, type, title, body, shipment_id)
    SELECT
        u.id,
        'payment'::public.notification_type,
        'Payment verification required',
        'Driver submitted ₱' || to_char(v_balance, 'FM999,999,990.00') || ' (' || p_method::text ||
        ') for ' || coalesce(v_shipment.tracking_number, 'shipment') || '. Review signed receipt before departure.',
        v_shipment.id
    FROM public.users u
    WHERE u.is_active AND u.role IN ('admin', 'staff');

    -- Notify customer for transparency (anti-collusion audit)
    SELECT c.user_id INTO v_customer_user_id
    FROM public.customers c
    WHERE c.id = v_shipment.customer_id;

    IF v_customer_user_id IS NOT NULL THEN
        INSERT INTO public.notifications (user_id, type, title, body, shipment_id)
        VALUES (
            v_customer_user_id,
            'payment'::public.notification_type,
            'Payment submitted for office review',
            'Your driver recorded a full payment of ₱' || to_char(v_balance, 'FM999,999,990.00') ||
            ' (' || p_method::text || ') for ' || coalesce(v_shipment.tracking_number, 'your shipment') ||
            '. It is pending office verification.',
            v_shipment.id
        );
    END IF;

    RETURN jsonb_build_object(
        'id', v_payment.id,
        'shipment_id', v_payment.shipment_id,
        'amount', v_payment.amount,
        'method', v_payment.method,
        'status', v_payment.status,
        'paid_at', v_payment.paid_at
    );
END $$;

REVOKE ALL ON FUNCTION public.driver_record_payment(uuid, uuid, public.payment_method, text, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.driver_record_payment(uuid, uuid, public.payment_method, text, text, text) TO authenticated;

-- 2. Admin/Staff verifies or rejects a pending payment from the Web Payment Ledger
CREATE OR REPLACE FUNCTION public.review_shipment_payment(
    p_payment_id uuid,
    p_decision text,
    p_reason text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_payment public.payments%ROWTYPE;
    v_shipment public.shipments%ROWTYPE;
    v_driver_user_id uuid;
    v_customer_user_id uuid;
    v_reason text := nullif(btrim(coalesce(p_reason, '')), '');
BEGIN
    IF v_uid IS NULL OR NOT EXISTS (
        SELECT 1 FROM public.users WHERE id = v_uid AND is_active AND role IN ('admin', 'staff')
    ) THEN
        RAISE EXCEPTION 'OFFICE_ACCESS_REQUIRED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_payment
    FROM public.payments
    WHERE id = p_payment_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'PAYMENT_NOT_FOUND';
    END IF;

    IF v_payment.status <> 'pending' THEN
        RAISE EXCEPTION 'PAYMENT_ALREADY_REVIEWED';
    END IF;

    SELECT * INTO v_shipment FROM public.shipments WHERE id = v_payment.shipment_id;

    SELECT dr.user_id INTO v_driver_user_id
    FROM public.deliveries d
    JOIN public.drivers dr ON dr.id = d.driver_id
    WHERE d.shipment_id = v_shipment.id AND d.status <> 'cancelled'
    ORDER BY d.assigned_at DESC
    LIMIT 1;

    SELECT c.user_id INTO v_customer_user_id
    FROM public.customers c
    WHERE c.id = v_shipment.customer_id;

    IF p_decision = 'verify' THEN
        UPDATE public.payments
        SET status = 'verified',
            verified_by = v_uid,
            verified_at = now(),
            rejection_reason = NULL,
            updated_at = now()
        WHERE id = v_payment.id;

        IF v_driver_user_id IS NOT NULL THEN
            INSERT INTO public.notifications (user_id, type, title, body, shipment_id)
            VALUES (
                v_driver_user_id,
                'payment'::public.notification_type,
                'Payment verified — ready to depart',
                'Office verified ₱' || to_char(v_payment.amount, 'FM999,999,990.00') || ' for ' ||
                coalesce(v_shipment.tracking_number, 'your trip') || '. You may now start transit.',
                v_shipment.id
            );
        END IF;

        IF v_customer_user_id IS NOT NULL THEN
            INSERT INTO public.notifications (user_id, type, title, body, shipment_id)
            VALUES (
                v_customer_user_id,
                'payment'::public.notification_type,
                'Payment verified',
                'Your payment of ₱' || to_char(v_payment.amount, 'FM999,999,990.00') || ' for ' ||
                coalesce(v_shipment.tracking_number, 'your shipment') || ' has been verified by the office.',
                v_shipment.id
            );
        END IF;

        RETURN jsonb_build_object('id', v_payment.id, 'status', 'verified');

    ELSIF p_decision = 'reject' THEN
        IF v_reason IS NULL OR length(v_reason) < 3 THEN
            RAISE EXCEPTION 'REJECTION_REASON_REQUIRED';
        END IF;

        UPDATE public.payments
        SET status = 'rejected',
            verified_by = v_uid,
            verified_at = now(),
            rejection_reason = v_reason,
            updated_at = now()
        WHERE id = v_payment.id;

        IF v_driver_user_id IS NOT NULL THEN
            INSERT INTO public.notifications (user_id, type, title, body, shipment_id)
            VALUES (
                v_driver_user_id,
                'payment'::public.notification_type,
                'Payment proof rejected',
                'Office rejected payment proof for ' || coalesce(v_shipment.tracking_number, 'your trip') ||
                ': ' || v_reason || '. Resolve and resubmit before departing.',
                v_shipment.id
            );
        END IF;

        IF v_customer_user_id IS NOT NULL THEN
            INSERT INTO public.notifications (user_id, type, title, body, shipment_id)
            VALUES (
                v_customer_user_id,
                'payment'::public.notification_type,
                'Payment verification issue',
                'The office could not verify the submitted payment for ' ||
                coalesce(v_shipment.tracking_number, 'your shipment') || '. Please coordinate with your driver.',
                v_shipment.id
            );
        END IF;

        RETURN jsonb_build_object('id', v_payment.id, 'status', 'rejected');
    ELSE
        RAISE EXCEPTION 'INVALID_PAYMENT_DECISION';
    END IF;
END $$;

REVOKE ALL ON FUNCTION public.review_shipment_payment(uuid, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.review_shipment_payment(uuid, text, text) TO authenticated;

-- 3. Server-side strict departure gate inside driver_confirm_cargo_loaded (called by mobile_driver_action('loaded'))
-- Inside driver_confirm_cargo_loaded(p_delivery uuid), BEFORE transitioning loading -> in_transit:
--
--   SELECT s.final_amount,
--          coalesce((SELECT sum(p.amount) FROM public.payments p WHERE p.shipment_id = s.id AND p.status = 'verified'), 0)
--   INTO v_final_amount, v_verified_paid
--   FROM public.shipments s
--   WHERE s.id = v_delivery.shipment_id;
--
--   IF v_final_amount IS NULL OR v_final_amount <= 0 OR v_verified_paid < v_final_amount THEN
--       RAISE EXCEPTION 'PAYMENT_VERIFICATION_REQUIRED';
--   END IF;
```

### Additional Backend & Web Admin Notes
1. **Evidence file resolution (`mobile_delivery_evidence_file`):** Extend `p_kind = 'payment'` to look up `payments.proof_path` where `payments.id = p_record::uuid AND payments.shipment_id = p_shipment` so drivers can view their uploaded receipt through the same reauthorized endpoint used for expenses and incidents.
2. **Web Admin Payment Ledger (`mabutol-trucking-react`):**
   - Keep the existing **Record Payment** modal for office/walk-in settlements.
   - For rows with `status = 'pending'` in the Payment Ledger table, add **View Receipt** (signed URL from `delivery-documents`), **Verify** (`review_shipment_payment(id, 'verify')`), and **Reject** (prompt for mandatory reason → `review_shipment_payment(id, 'reject', reason)`).
   - Surface real-time notifications for `type = 'payment'` prominently so dispatchers verify promptly while trucks are waiting at pickup.

### Client Compatibility (`Tanaw_Mobile`)
- The Android client reads `public.payments` directly under RLS (best-effort, non-blocking if the driver SELECT policy is not yet applied) AND supports enriched payment summary fields if added to `mobile_workspace()` / `mobile_shipment_details()`.
- The Android client subscribes to Realtime on `public.payments` so Admin Verify/Reject unlocks `START TRANSIT` or displays the rejection banner immediately without forcing manual reloads.
- All payment submission retries use a stable encrypted local draft UUID (`PaymentDraftStore`) and idempotent storage upload at `<delivery_id>/payment-<request_id>.jpg`.

---

## 7. Request C12: Customer No-Show Flow, 3-Strike Standing, Booking Cooldowns, Permanent Suspension & In-App Appeals

### Motivation
When a driver arrives at a customer's pickup location and the customer fails to show up after the grace window, the platform needs a transparent, enforceable no-show and progressive strike system shared across **Tanaw Mobile** (Customer & Driver apps) and the **Web Admin Console (`mabutol-trucking-react`)**:
1. **Grace & Final-Warning Windows:** After `pickup_arrived_at`, the customer has a standard grace period (`grace_ends_at`, default 15 min) followed by an extended wait window (`auto_cancel_at`, default 30 min total wait time). During the final 5 minutes before `auto_cancel_at`, the customer enters **Booking At Risk** with a live countdown.
2. **Customer "I'm On My Way" Signal:** Customers in the post-grace window can tap **"I'm On My Way — Notify Driver"** to record `customer_on_way_at` and immediately alert the waiting driver.
3. **Dual Cancellation Authority (Driver/Auto + Office):** Once `now() >= auto_cancel_at`, **both** the assigned driver (via mobile) and office/admin staff (via web) — as well as an optional scheduled auto-cancel job at `0:00` — can confirm the no-show cancellation atomically.
4. **Progressive 3-Strike Penalties:**
   - **Strike 1 of 3:** Shipment cancelled as `no_show` + **48-hour** booking cooldown (`booking_cooldown_until = now() + interval '48 hours'`).
   - **Strike 2 of 3 (Final Warning):** Shipment cancelled as `no_show` + **72-hour** booking cooldown (`booking_cooldown_until = now() + interval '72 hours'`).
   - **Strike 3 of 3:** **Permanent Account Suspension** (`standing_status = 'suspended'`, `suspended_at = now()`). Crucially, `public.users.is_active` remains `true` so `mobile_workspace()` can still load the customer's strike history and render the in-app **Account Suspended** screen & Appeal form, while `mobile_create_request()` hard-blocks new bookings.
   - **Post-Reinstatement Rule:** Strikes do **not** reset after reinstatement (`standing_status = 'reinstated'`); a 4th confirmed no-show triggers permanent account review / re-suspension.
5. **In-App Suspension Appeal Form:** Suspended customers can submit an appeal directly in the mobile app (`public.customer_appeals`), reviewed by admins within 3–5 business days.
6. **Web Admin `STRIKE STATUS` Column:** The Customers table in `mabutol-trucking-react` displays a 3-circle strike status indicator (`0/3`, `1/3`, `2/3`, `3/3`) per customer, alongside strike history details and appeal review actions.

### Proposed Schema Changes

```sql
-- 1. Standing & suspension fields on public.customers (keep public.users.is_active = true for suspended customers)
ALTER TABLE public.customers
    ADD COLUMN IF NOT EXISTS no_show_strike_count smallint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS booking_cooldown_until timestamptz,
    ADD COLUMN IF NOT EXISTS standing_status text NOT NULL DEFAULT 'active'
        CHECK (standing_status IN ('active', 'suspended', 'reinstated')),
    ADD COLUMN IF NOT EXISTS suspended_at timestamptz,
    ADD COLUMN IF NOT EXISTS suspension_reason text,
    ADD COLUMN IF NOT EXISTS reinstated_at timestamptz;

-- 2. Delivery & shipment no-show tracking fields
ALTER TABLE public.deliveries
    ADD COLUMN IF NOT EXISTS customer_on_way_at timestamptz,
    ADD COLUMN IF NOT EXISTS no_show_confirmed_at timestamptz;

ALTER TABLE public.shipments
    ADD COLUMN IF NOT EXISTS cancellation_type text
        CHECK (cancellation_type IN ('customer', 'office', 'no_show', 'system'));

-- 3. Immutable strike history ledger
CREATE TABLE IF NOT EXISTS public.customer_no_show_strikes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id uuid NOT NULL REFERENCES public.customers(id) ON DELETE CASCADE,
    shipment_id uuid NOT NULL UNIQUE REFERENCES public.shipments(id) ON DELETE CASCADE,
    delivery_id uuid REFERENCES public.deliveries(id) ON DELETE SET NULL,
    strike_number smallint NOT NULL CHECK (strike_number >= 1),
    confirmed_by uuid REFERENCES public.users(id),
    confirmed_source text NOT NULL DEFAULT 'office'
        CHECK (confirmed_source IN ('driver', 'office', 'system')),
    confirmed_at timestamptz NOT NULL DEFAULT now(),
    reason text NOT NULL DEFAULT 'Customer no-show at pick-up location',
    attempt_notes text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_customer_no_show_strikes_customer
    ON public.customer_no_show_strikes (customer_id, confirmed_at ASC);

ALTER TABLE public.customer_no_show_strikes ENABLE ROW LEVEL SECURITY;

CREATE POLICY "customers read own no-show strikes"
ON public.customer_no_show_strikes
FOR SELECT
TO authenticated
USING (customer_id = public.my_customer_id());

CREATE POLICY "staff full access on no-show strikes"
ON public.customer_no_show_strikes
FOR ALL
TO authenticated
USING (public.get_user_role() IN ('admin', 'staff'));

-- 4. In-app suspension appeals table
CREATE TABLE IF NOT EXISTS public.customer_appeals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id uuid NOT NULL REFERENCES public.customers(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    strike_count_at_appeal smallint NOT NULL,
    reason text NOT NULL CHECK (length(trim(reason)) BETWEEN 10 AND 1500),
    contact_number text,
    status text NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'approved', 'rejected')),
    admin_notes text,
    reviewed_by uuid REFERENCES public.users(id),
    reviewed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_customer_appeals_customer
    ON public.customer_appeals (customer_id, created_at DESC);

ALTER TABLE public.customer_appeals ENABLE ROW LEVEL SECURITY;

CREATE POLICY "customers read own appeals"
ON public.customer_appeals
FOR SELECT
TO authenticated
USING (customer_id = public.my_customer_id());

CREATE POLICY "staff full access on customer appeals"
ON public.customer_appeals
FOR ALL
TO authenticated
USING (public.get_user_role() IN ('admin', 'staff'));
```

### Proposed RPC Changes

```sql
-- 1. Customer taps "I'm On My Way — Notify Driver" during grace/at-risk wait window
CREATE OR REPLACE FUNCTION public.customer_notify_on_the_way(p_delivery_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_customer_id uuid := public.my_customer_id();
    v_delivery public.deliveries%ROWTYPE;
    v_shipment public.shipments%ROWTYPE;
    v_driver_user_id uuid;
    v_now timestamptz := now();
BEGIN
    IF v_customer_id IS NULL THEN
        RAISE EXCEPTION 'ACTIVE_ACCOUNT_REQUIRED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_delivery FROM public.deliveries WHERE id = p_delivery_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'DELIVERY_NOT_FOUND';
    END IF;

    SELECT * INTO v_shipment
    FROM public.shipments
    WHERE id = v_delivery.shipment_id AND customer_id = v_customer_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'SHIPMENT_NOT_FOUND';
    END IF;

    IF v_delivery.status <> 'assigned'
       OR v_delivery.pickup_arrived_at IS NULL
       OR v_delivery.customer_showed_at IS NOT NULL THEN
        RAISE EXCEPTION 'NOT_IN_GRACE_WINDOW';
    END IF;

    IF v_delivery.customer_on_way_at IS NOT NULL THEN
        RETURN jsonb_build_object(
            'delivery_id', v_delivery.id,
            'customer_on_way_at', v_delivery.customer_on_way_at,
            'already_notified', true
        );
    END IF;

    UPDATE public.deliveries
    SET customer_on_way_at = v_now,
        updated_at = v_now
    WHERE id = v_delivery.id;

    SELECT dr.user_id INTO v_driver_user_id
    FROM public.drivers dr
    WHERE dr.id = v_delivery.driver_id;

    IF v_driver_user_id IS NOT NULL THEN
        INSERT INTO public.notifications (user_id, type, title, body, shipment_id)
        VALUES (
            v_driver_user_id,
            'delivery'::public.notification_type,
            'Customer is on the way',
            'The customer for ' || coalesce(v_shipment.tracking_number, 'your trip') ||
            ' notified that they are heading to the pick-up location now.',
            v_shipment.id
        );
    END IF;

    RETURN jsonb_build_object(
        'delivery_id', v_delivery.id,
        'customer_on_way_at', v_now,
        'already_notified', false
    );
END $$;

REVOKE ALL ON FUNCTION public.customer_notify_on_the_way(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.customer_notify_on_the_way(uuid) TO authenticated;

-- 2. Atomic No-Show Cancellation & Strike Issuance (callable by BOTH assigned driver after auto_cancel_at AND office admin/staff)
CREATE OR REPLACE FUNCTION public.confirm_no_show_cancellation(
    p_delivery_id uuid,
    p_reason text DEFAULT NULL,
    p_attempt_notes text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_role text;
    v_delivery public.deliveries%ROWTYPE;
    v_shipment public.shipments%ROWTYPE;
    v_customer public.customers%ROWTYPE;
    v_now timestamptz := now();
    v_new_strikes smallint;
    v_cooldown_until timestamptz;
    v_new_standing text;
    v_source text;
    v_reason text := coalesce(nullif(btrim(p_reason), ''), 'Confirmed customer no-show after pickup wait period');
BEGIN
    SELECT role::text INTO v_role FROM public.users WHERE id = v_uid AND is_active;
    IF v_role IS NULL THEN
        RAISE EXCEPTION 'ACTIVE_ACCOUNT_REQUIRED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_delivery FROM public.deliveries WHERE id = p_delivery_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'DELIVERY_NOT_FOUND';
    END IF;

    IF v_delivery.status <> 'assigned' OR v_delivery.pickup_arrived_at IS NULL OR v_delivery.customer_showed_at IS NOT NULL THEN
        RAISE EXCEPTION 'DELIVERY_CLOSED';
    END IF;

    IF v_role = 'driver' THEN
        IF NOT EXISTS (SELECT 1 FROM public.drivers d WHERE d.id = v_delivery.driver_id AND d.user_id = v_uid) THEN
            RAISE EXCEPTION 'ACTIVE_DRIVER_REQUIRED' USING ERRCODE = '42501';
        END IF;
        IF v_delivery.auto_cancel_at IS NULL OR v_now < v_delivery.auto_cancel_at THEN
            RAISE EXCEPTION 'WAIT_PERIOD_NOT_FINISHED';
        END IF;
        v_source := 'driver';
    ELSIF v_role IN ('admin', 'staff') THEN
        v_source := 'office';
    ELSE
        RAISE EXCEPTION 'OFFICE_OR_DRIVER_REQUIRED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_shipment FROM public.shipments WHERE id = v_delivery.shipment_id FOR UPDATE;
    SELECT * INTO v_customer FROM public.customers WHERE id = v_shipment.customer_id FOR UPDATE;

    -- Cancel delivery & shipment
    UPDATE public.deliveries
    SET status = 'cancelled',
        no_show_confirmed_at = v_now,
        updated_at = v_now
    WHERE id = v_delivery.id;

    UPDATE public.shipments
    SET status = 'cancelled',
        cancellation_type = 'no_show',
        cancelled_reason = v_reason,
        cancelled_at = v_now,
        updated_at = v_now
    WHERE id = v_shipment.id;

    -- Issue immutable strike record
    v_new_strikes := coalesce(v_customer.no_show_strike_count, 0) + 1;

    INSERT INTO public.customer_no_show_strikes (
        customer_id, shipment_id, delivery_id, strike_number,
        confirmed_by, confirmed_source, confirmed_at, reason, attempt_notes
    )
    VALUES (
        v_customer.id, v_shipment.id, v_delivery.id, v_new_strikes,
        v_uid, v_source, v_now, v_reason, nullif(btrim(coalesce(p_attempt_notes, '')), '')
    )
    ON CONFLICT (shipment_id) DO NOTHING;

    IF v_new_strikes = 1 THEN
        v_cooldown_until := v_now + interval '48 hours';
        v_new_standing := 'active';
    ELSIF v_new_strikes = 2 THEN
        v_cooldown_until := v_now + interval '72 hours';
        v_new_standing := 'active';
    ELSE
        -- Strike 3 (or 4th strike after reinstatement) => permanent suspension / admin review
        v_cooldown_until := NULL;
        v_new_standing := 'suspended';
    END IF;

    UPDATE public.customers
    SET no_show_strike_count = v_new_strikes,
        booking_cooldown_until = v_cooldown_until,
        standing_status = v_new_standing,
        suspended_at = CASE WHEN v_new_standing = 'suspended' THEN v_now ELSE suspended_at END,
        suspension_reason = CASE
            WHEN v_new_standing = 'suspended' AND v_new_strikes >= 4 THEN v_new_strikes || ' confirmed no-shows (post-reinstatement review)'
            WHEN v_new_standing = 'suspended' THEN '3 confirmed no-shows'
            ELSE suspension_reason
        END,
        updated_at = v_now
    WHERE id = v_customer.id;

    -- Notify customer
    IF v_customer.user_id IS NOT NULL THEN
        INSERT INTO public.notifications (user_id, type, title, body, shipment_id)
        VALUES (
            v_customer.user_id,
            'shipment_status'::public.notification_type,
            CASE
                WHEN v_new_standing = 'suspended' THEN 'Account Suspended — Strike ' || v_new_strikes || ' of 3'
                ELSE 'Booking Cancelled (No-Show) — Strike ' || v_new_strikes || ' of 3'
            END,
            CASE
                WHEN v_new_strikes = 1 THEN 'Shipment ' || coalesce(v_shipment.tracking_number, '') || ' was cancelled due to no-show. Booking is paused for 48 hours.'
                WHEN v_new_strikes = 2 THEN 'Final warning: Shipment ' || coalesce(v_shipment.tracking_number, '') || ' was cancelled due to no-show. Booking is paused for 72 hours.'
                ELSE 'Your account has been suspended after ' || v_new_strikes || ' confirmed no-shows. Open the app to submit an appeal.'
            END,
            v_shipment.id
        );
    END IF;

    RETURN jsonb_build_object(
        'shipment_id', v_shipment.id,
        'strike_number', v_new_strikes,
        'standing_status', v_new_standing,
        'booking_cooldown_until', v_cooldown_until
    );
END $$;

REVOKE ALL ON FUNCTION public.confirm_no_show_cancellation(uuid, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.confirm_no_show_cancellation(uuid, text, text) TO authenticated;

-- Note: Update driver_milestone(p_delivery, 'no_show') to delegate to confirm_no_show_cancellation(p_delivery)
-- when driver cancellation is enabled, and optionally schedule a pg_cron job calling
-- confirm_no_show_cancellation for expired deliveries where now() >= auto_cancel_at.

-- 3. In-app Suspension Appeal Submission RPC
CREATE OR REPLACE FUNCTION public.customer_submit_suspension_appeal(
    p_reason text,
    p_contact_number text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_customer public.customers%ROWTYPE;
    v_reason text := btrim(coalesce(p_reason, ''));
    v_contact text := nullif(btrim(coalesce(p_contact_number, '')), '');
    v_appeal public.customer_appeals%ROWTYPE;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'ACTIVE_ACCOUNT_REQUIRED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_customer FROM public.customers WHERE user_id = v_uid FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'CUSTOMER_PROFILE_REQUIRED';
    END IF;

    IF v_customer.standing_status <> 'suspended' THEN
        RAISE EXCEPTION 'ACCOUNT_NOT_SUSPENDED';
    END IF;

    IF length(v_reason) < 10 OR length(v_reason) > 1500 THEN
        RAISE EXCEPTION 'APPEAL_REASON_REQUIRED';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.customer_appeals
        WHERE customer_id = v_customer.id AND status = 'pending'
    ) THEN
        RAISE EXCEPTION 'APPEAL_ALREADY_PENDING';
    END IF;

    INSERT INTO public.customer_appeals (
        customer_id, user_id, strike_count_at_appeal, reason, contact_number
    )
    VALUES (
        v_customer.id, v_uid, coalesce(v_customer.no_show_strike_count, 3),
        v_reason, coalesce(v_contact, v_customer.phone)
    )
    RETURNING * INTO v_appeal;

    INSERT INTO public.notifications (user_id, type, title, body)
    SELECT
        u.id,
        'system'::public.notification_type,
        'New suspension appeal submitted',
        coalesce(v_customer.name, 'A customer') || ' submitted a suspension appeal (' ||
        v_appeal.strike_count_at_appeal || ' strikes). Review within 3-5 business days.'
    FROM public.users u
    WHERE u.is_active AND u.role IN ('admin', 'staff');

    RETURN jsonb_build_object(
        'id', v_appeal.id,
        'status', v_appeal.status,
        'created_at', v_appeal.created_at
    );
END $$;

REVOKE ALL ON FUNCTION public.customer_submit_suspension_appeal(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.customer_submit_suspension_appeal(text, text) TO authenticated;

-- 4. Office Appeal Review & Reinstatement (preserves strike history; strikes do NOT reset)
CREATE OR REPLACE FUNCTION public.office_review_customer_appeal(
    p_appeal_id uuid,
    p_decision text,
    p_admin_notes text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_appeal public.customer_appeals%ROWTYPE;
    v_customer public.customers%ROWTYPE;
    v_notes text := nullif(btrim(coalesce(p_admin_notes, '')), '');
BEGIN
    IF v_uid IS NULL OR NOT EXISTS (
        SELECT 1 FROM public.users WHERE id = v_uid AND is_active AND role IN ('admin', 'staff')
    ) THEN
        RAISE EXCEPTION 'OFFICE_ACCESS_REQUIRED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_appeal FROM public.customer_appeals WHERE id = p_appeal_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'APPEAL_NOT_FOUND'; END IF;
    IF v_appeal.status <> 'pending' THEN RAISE EXCEPTION 'APPEAL_ALREADY_REVIEWED'; END IF;

    SELECT * INTO v_customer FROM public.customers WHERE id = v_appeal.customer_id FOR UPDATE;

    IF p_decision = 'approve' THEN
        UPDATE public.customer_appeals
        SET status = 'approved', admin_notes = v_notes, reviewed_by = v_uid, reviewed_at = now(), updated_at = now()
        WHERE id = v_appeal.id;

        UPDATE public.customers
        SET standing_status = 'reinstated',
            booking_cooldown_until = NULL,
            reinstated_at = now(),
            updated_at = now()
        WHERE id = v_customer.id;

        INSERT INTO public.notifications (user_id, type, title, body)
        VALUES (
            v_customer.user_id,
            'system'::public.notification_type,
            'Account reinstated',
            'Your suspension appeal was approved. Note: prior strikes remain on record; a subsequent no-show triggers permanent account review.'
        );
    ELSIF p_decision = 'reject' THEN
        UPDATE public.customer_appeals
        SET status = 'rejected', admin_notes = v_notes, reviewed_by = v_uid, reviewed_at = now(), updated_at = now()
        WHERE id = v_appeal.id;

        INSERT INTO public.notifications (user_id, type, title, body)
        VALUES (
            v_customer.user_id,
            'system'::public.notification_type,
            'Suspension appeal declined',
            coalesce('Your appeal was not approved: ' || v_notes, 'Your suspension appeal was declined by administration.')
        );
    ELSE
        RAISE EXCEPTION 'INVALID_APPEAL_DECISION';
    END IF;

    RETURN jsonb_build_object('id', v_appeal.id, 'status', CASE WHEN p_decision = 'approve' THEN 'approved' ELSE 'rejected' END);
END $$;

REVOKE ALL ON FUNCTION public.office_review_customer_appeal(uuid, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.office_review_customer_appeal(uuid, text, text) TO authenticated;

-- 5. Booking gate inside mobile_create_request(p_request_id, p_data)
-- Immediately after loading customer row:
--   IF v_customer.standing_status = 'suspended' THEN
--       RAISE EXCEPTION 'ACCOUNT_SUSPENDED';
--   END IF;
--   IF v_customer.booking_cooldown_until IS NOT NULL AND v_customer.booking_cooldown_until > now() THEN
--       RAISE EXCEPTION 'BOOKING_COOLDOWN_ACTIVE';
--   END IF;

-- 6. mobile_workspace() enrichment
-- - Include in customer JSON: no_show_strike_count, booking_cooldown_until, standing_status, suspended_at, suspension_reason, reinstated_at
-- - Include in deliveries JSON: grace_ends_at, auto_cancel_at, customer_on_way_at, no_show_confirmed_at
-- - Include in shipments JSON: cancellation_type, cancelled_at
-- - Add top-level arrays:
--     'customer_strikes' (from public.customer_no_show_strikes joined with shipments.tracking_number, ORDER BY confirmed_at ASC)
--     'customer_appeals' (from public.customer_appeals, ORDER BY created_at DESC LIMIT 5)
```

### Web Admin Specification (`mabutol-trucking-react`)
1. **Customers Table — `STRIKE STATUS` Column:**
   - Render a reusable `<StrikeStatusIndicator count={customer.no_show_strike_count} max={3} />` component matching the 3-circle indicator (`○ ○ ○` for `0/3`, `(!) ○ ○` for `1/3`, `(!) (!) ○` for `2/3`, `(!) (!) (!)` for `3/3`).
   - Filled circles use `#B91C1C` with crisp white `!` glyph (`aria-hidden="true"`); hollow circles use `1.5px solid #64748B`; wrapper provides `aria-label="${count} of 3 no-show strikes"` and tooltip with strike dates & cooldown/suspension status.
   - Add table filter chips: `All` · `Has Strikes (1–2)` · `Suspended (3/3)`.
2. **Customer Detail Drawer & Appeals Queue:**
   - Show chronological `STRIKE HISTORY` (Strike 1, Strike 2, Strike 3 with dates & shipment tracking numbers), active booking cooldown timer, and pending `customer_appeals` with **Approve & Reinstate** (`office_review_customer_appeal(id, 'approve', notes)`) and **Decline** (`office_review_customer_appeal(id, 'reject', notes)`) buttons.
3. **Dispatch / Active Deliveries No-Show Action:**
   - Surface a **"Confirm No-Show & Issue Strike"** action (`confirm_no_show_cancellation(delivery_id, reason, attempt_notes)`) when a delivery is waiting past grace/wait threshold or flagged by the driver.

### Client Compatibility (`Tanaw_Mobile`)
- `MobileWorkspaceViewModel.readCustomerFtlData` fetches standing fields (`customers`), `customer_no_show_strikes`, `customer_appeals`, and pickup wait timestamps (`deliveries`) with non-blocking fallbacks so older backend revisions continue working seamlessly until this migration is applied.

---

## 8. Request S01–S06: Database Privilege, RLS, Atomic Mutation & Edge Function Security Hardening

### Motivation
A comprehensive defensive security audit of the bundled Supabase schema snapshot (`supabase/migrations/live_schema_dump.sql`), early migrations (`07_driver_locations.sql`), and admin Edge Function (`supabase/functions/create-driver-user/index.ts`) identified six server-side privilege, RLS, and atomicity gaps that should be hardened in `mabutol-trucking-react`:

1. **`S01` — Unauthenticated Role Check in `driver_set_delivery_status` (`live_schema_dump.sql` L271–286 & L4785):**
   - `public.driver_set_delivery_status(p_delivery_id uuid, p_status text)` is `SECURITY DEFINER` and granted to `authenticated`, but does not check `auth.uid()` or verify that the caller owns the delivery (`driver_id = public.my_driver_id()`). Any authenticated user (including a customer) could invoke it directly to change arbitrary delivery statuses and by trigger mark shipments `completed` or `in_transit`.
2. **`S02` — Direct Customer `INSERT` on `public.shipments` Bypassing `mobile_create_request` (`live_schema_dump.sql` L2095):**
   - The RLS policy `"customer books own shipment"` allows any authenticated customer to `INSERT` directly into `public.shipments` with only `WITH CHECK (customer_id = public.my_customer_id())`, bypassing `mobile_create_request()` validation, booking cooldowns (`booking_cooldown_until`), and account suspension checks (`standing_status = 'suspended'`), and allowing caller-supplied `final_amount`, `quoted_amount`, or `payment_state = 'paid'`.
3. **`S03` — Unrestricted Column Updates on `public.users` (`live_schema_dump.sql` L2062 & L2423):**
   - The `"update own profile"` policy and trigger `prevent_users_privilege_escalation` block changes to `role` and `is_active`, but allow direct `UPDATE` to `phone`, `email`, and unbounded `avatar_url` strings—allowing a user to desynchronize `public.users.phone`/`email` from verified `auth.users` identity or store multi-megabyte strings in `avatar_url`.
4. **`S04` — Missing Delivery Ownership Verification in `driver_locations` Direct `INSERT` Policy (`07_driver_locations.sql` L24–26):**
   - Policy `"Drivers can insert their own location"` checks `driver_id IN (SELECT id FROM public.drivers WHERE user_id = auth.uid())` without verifying that `delivery_id` is assigned to that driver and currently active (telemetry should flow exclusively through `driver_record_position`).
5. **`S05` — Atomic `mobile_cancel_shipment` (Including `'quoted'`) and `mobile_accept_shipment_quote` With Direct `UPDATE` Lockdown:**
   - Ensure `mobile_cancel_shipment(p_shipment, p_reason)` permits `'quoted'` in addition to `'pending'`, `'booked'`, and `'assigned'` (matching `CustomerCancellationRules.CANCELLABLE_SHIPMENT_STATUSES`), and ensure `mobile_accept_shipment_quote(p_shipment)` enforces `quote_expires_at > now()` atomically on the server using the database clock (`now()`). Once both RPCs are deployed, revoke direct customer `UPDATE` privileges on `public.shipments`.
6. **`S06` — Edge Function Hardening (`create-driver-user` & `mobile-email-login`):**
   - In `create-driver-user/index.ts`: pin `@supabase/supabase-js` to an exact semver version (e.g., `2.49.1`), restrict `Access-Control-Allow-Origin` to the configured admin console origin, enforce `req.method === 'POST'`, require password complexity (`>= 8` chars) and valid email/phone formats, roll back (`supabaseAdmin.auth.admin.deleteUser(newUserId)`) if `public.users` or `public.drivers` upsert fails so no orphaned auth account remains, and return sanitized error codes instead of raw database messages.
   - In `mobile-email-login`: verify per-IP and per-account rate limiting and uniform error responses so invalid emails and wrong passwords cannot be distinguished for account enumeration.

### Proposed Hardening Migration (`mabutol-trucking-react`)

```sql
-- 1. S01: Revoke direct authenticated execution on internal/legacy driver_set_delivery_status
-- (All mobile driver transitions go through mobile_driver_action / driver_milestone / driver_submit_pod)
REVOKE ALL ON FUNCTION public.driver_set_delivery_status(uuid, text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.driver_set_delivery_status(uuid, text) TO service_role;

-- 2. S02 & S05: Remove direct customer INSERT and UPDATE policies on public.shipments
-- (Bookings must go through mobile_create_request; cancellations and quote acceptance go through RPCs)
DROP POLICY IF EXISTS "customer books own shipment" ON public.shipments;
DROP POLICY IF EXISTS "customer cancels own pending shipment" ON public.shipments;

-- Ensure mobile_cancel_shipment includes 'quoted' alongside 'pending', 'booked', 'assigned'
-- (Update status check in Request C10's mobile_cancel_shipment):
--   IF v_shipment.status NOT IN ('pending', 'quoted', 'booked', 'assigned') THEN
--       RAISE EXCEPTION 'SHIPMENT_NOT_CANCELLABLE';
--   END IF;

-- Atomic server-side quote acceptance RPC
CREATE OR REPLACE FUNCTION public.mobile_accept_shipment_quote(p_shipment uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_customer_id uuid := public.my_customer_id();
    v_shipment public.shipments%ROWTYPE;
BEGIN
    IF v_customer_id IS NULL THEN
        RAISE EXCEPTION 'ACTIVE_ACCOUNT_REQUIRED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_shipment
    FROM public.shipments
    WHERE id = p_shipment AND customer_id = v_customer_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'SHIPMENT_NOT_FOUND';
    END IF;

    IF v_shipment.status IN ('booked', 'confirmed') THEN
        RAISE EXCEPTION 'ALREADY_ACCEPTED';
    END IF;

    IF v_shipment.status <> 'quoted' THEN
        RAISE EXCEPTION 'QUOTE_NOT_AVAILABLE';
    END IF;

    IF v_shipment.quote_expires_at IS NOT NULL AND now() >= v_shipment.quote_expires_at THEN
        RAISE EXCEPTION 'QUOTE_EXPIRED';
    END IF;

    UPDATE public.shipments
    SET status = 'booked',
        updated_at = now()
    WHERE id = p_shipment;

    RETURN jsonb_build_object(
        'shipment_id', p_shipment,
        'status', 'booked',
        'updated_at', now()
    );
END $$;

REVOKE ALL ON FUNCTION public.mobile_accept_shipment_quote(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobile_accept_shipment_quote(uuid) TO authenticated;

-- 3. S03: Bound avatar_url length on public.users and prevent unverified email/phone spoofing
ALTER TABLE public.users
    DROP CONSTRAINT IF EXISTS users_avatar_url_max_length,
    ADD CONSTRAINT users_avatar_url_max_length CHECK (avatar_url IS NULL OR length(avatar_url) <= 750000);

-- 4. S04: Tighten driver_locations direct INSERT policy (or require driver_record_position RPC)
DROP POLICY IF EXISTS "Drivers can insert their own location" ON public.driver_locations;
CREATE POLICY "Drivers can insert their own location"
ON public.driver_locations
FOR INSERT
TO authenticated
WITH CHECK (
    driver_id = public.my_driver_id()
    AND EXISTS (
        SELECT 1 FROM public.deliveries d
        WHERE d.id = delivery_id
          AND d.driver_id = public.my_driver_id()
          AND d.status IN ('assigned', 'loading', 'in_transit')
    )
);
```

### Client Compatibility (`Tanaw_Mobile`)
- `MobileWorkspaceViewModel.cancelShipment` and `MobileWorkspaceViewModel.acceptQuote` now invoke `mobile_cancel_shipment` and `mobile_accept_shipment_quote` first, falling back to a direct PostgREST `UPDATE` **only** on `PGRST202` (function not yet deployed). Once the migration above is applied in `mabutol-trucking-react`, all mutations execute atomically through the `SECURITY DEFINER` RPCs.

---

## 9. Request C13: Verify-First Customer Phone Signup (No Account Created Until OTP Verified)

### Motivation
Currently, `CreateAccountScreen` calls Supabase `signUpWith(Phone)` as soon as the user submits the registration form. In Supabase GoTrue, `signUpWith(Phone)` immediately inserts an unconfirmed `auth.users` row (`phone_confirmed_at = NULL`, firing `handle_new_user()` to insert `public.users`) before the user enters their SMS OTP.

If the user taps **Back** or cancels on `verify_phone` (or closes the app), an unconfirmed account remains in the database:
- Attempting to log in fails with `phone_not_confirmed` (`"Verify your phone number with the SMS code before signing in."`).
- Cancelled registrations pollute `auth.users` and `public.users`.

**Desired Product Rule:** No `auth.users` or `public.users` account should be created if SMS OTP verification is cancelled or abandoned.

### Proposed Backend Architecture (`mabutol-trucking-react`)

1. **Pending OTP Verification Table (Service-Role Only, No Public RLS):**
   ```sql
   CREATE TABLE IF NOT EXISTS public.pending_phone_signups (
       phone text PRIMARY KEY,
       otp_hash text NOT NULL,
       attempts smallint NOT NULL DEFAULT 0,
       expires_at timestamptz NOT NULL,
       resend_available_at timestamptz NOT NULL,
       created_at timestamptz NOT NULL DEFAULT now()
   );
   ALTER TABLE public.pending_phone_signups ENABLE ROW LEVEL SECURITY;
   -- No policies for anon/authenticated; accessed strictly via service_role Edge Functions.
   ```

2. **Edge Function 1 — `mobile-send-signup-otp` (`POST`):**
   - Validates `phone` (`^\+639[0-9]{9}$`) and checks that no confirmed account already owns `phone` in `auth.users` / `public.users` (returning `409 phone_exists` if already registered).
   - Enforces cooldown (`resend_available_at`, e.g., 60s) and rate limiting (`429 over_sms_send_rate_limit`).
   - Generates a cryptographically random 6-digit OTP, stores `sha256(otp + secret)` with `expires_at = now() + interval '5 minutes'` in `pending_phone_signups`, and dispatches the SMS via the configured TextBee gateway.
   - Does **not** create any row in `auth.users`, `public.users`, or `public.customers`.

3. **Edge Function 2 — `mobile-complete-signup` (`POST`):**
   - Accepts `{ phone, code, first_name, last_name, email, password, address }`.
   - Validates `code` against `pending_phone_signups` (`expires_at > now()`, max 5 attempts), deleting the pending OTP row on success.
   - Calls `adminClient.auth.admin.createUser({ phone, phone_confirm: true, password, app_metadata: { provisioned_role: "customer" }, user_metadata: { full_name, phone, contact_email, address } })`.
   - Signs in / mints a session for the newly confirmed user and returns `{ access_token, refresh_token }`, allowing the mobile app to call `client.auth.importAuthToken(...)` and enter the workspace immediately.
   - If the user taps **Back** on `verify_phone`, no `auth.users` or `public.users` row ever exists.

### Client Compatibility (`Tanaw_Mobile`)
- As an immediate bridge for any existing unconfirmed `auth.users` phone accounts, `MobileWorkspaceViewModel.login` catches `phone_not_confirmed`, automatically requests/resumes SMS verification, and routes the user directly to `verify_phone` so they can complete verification and sign in.
- Once `mobile-send-signup-otp` and `mobile-complete-signup` are deployed in `mabutol-trucking-react`, `MobileWorkspaceViewModel.signup` and `verifyPhoneSignup` can switch to calling those endpoints while retaining `phone_not_confirmed` fallback for legacy unconfirmed rows.

---

## 10. Request AUTH-01: Gmail OTP Change-Password Endpoints (Node.js / Express + Nodemailer)

### Motivation
When signed-in users with a `@gmail.com` address use the Change Password flow (`PasswordChangeDialog`), the mobile client (`MobileWorkspaceViewModel.sendOtp()` / `verifyAndChangePassword()`) supports calling a Node.js/Express password-reset service (`PASSWORD_API_BASE_URL`) so Gmail OTP verification and password hashing run server-side without embedding SMTP credentials in the Android APK.

### Required Endpoints

1. **`POST /api/password/send-otp`**
   - Body: `{ "email": "user@gmail.com" }`
   - Validates `@gmail.com` format and checks that the user exists.
   - Generates a 6-digit OTP (`crypto.randomInt(100000, 1000000)`), stores its salted HMAC-SHA256 hash with a 10-minute expiry and `attempts = 0` (max 5 attempts).
   - Sends the OTP via `smtp.gmail.com` (port `587`, STARTTLS) using `process.env.DEV_GMAIL_USER` and `process.env.DEV_GMAIL_APP_PASS`.
   - Returns `{ "success": true, "message": "Verification code sent to your Gmail address." }`.

2. **`POST /api/password/verify-otp`**
   - Body: `{ "email": "user@gmail.com", "otp": "123456" }`
   - Validates `@gmail.com` format, 6-digit OTP, expiry (`<= 10 min`), and attempt limit (`< 5` attempts, timing-safe hash comparison) **without** consuming the OTP yet (or mints a short-lived reset token for `/api/password/reset`).
   - Required so the mobile client's two-step recovery flow (`VerifyRecoveryCodeScreen` -> `ResetPasswordScreen`) can verify the OTP server-side before revealing password entry fields.
   - Returns `{ "success": true, "message": "Verification code confirmed." }`.

3. **`POST /api/password/reset`**
   - Body: `{ "email": "user@gmail.com", "otp": "123456", "newPassword": "..." }`
   - Verifies `@gmail.com` format, 6-digit OTP, `newPassword.length >= 8`, expiry (`<= 10 min`), and attempt limit (`< 5` attempts, timing-safe hash comparison).
   - Hashes `newPassword` (`bcrypt`), updates the user record, and deletes/invalidates the used OTP.
   - Returns `{ "success": true, "message": "Password updated successfully." }`.



