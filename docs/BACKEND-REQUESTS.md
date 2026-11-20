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
3. The customer signs a physical paper receipt after paying (for all payment methods: Cash, GCash, Bank Transfer, Check).
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

    IF p_method IN ('gcash', 'bank_transfer', 'check') AND v_ref IS NULL THEN
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





