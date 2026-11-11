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
The Android client implements `CustomerCancellationRules.kt` to enforce pre-transit rules and reason requirements on-device, and executes the cancellation via direct PostgREST table update (matching `mabutol-trucking-react` web behavior) with optimistic state updates in [MobileWorkspaceViewModel.kt](file:///C:/Users/Soriano/AndroidStudioProjects/Tanaw_Mobile/app/src/main/java/com/tanaw/app/feature/workspace/MobileWorkspaceViewModel.kt).


