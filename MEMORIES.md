# MEMORIES.md — TANAW Project Memory & Implementation Blueprint

**Project**: TANAW Mobile App (Logistics & Freight Shipping for Mabutol Trucking, Luzon, PH)  
**Tech Stack**: Native Android (Kotlin, Jetpack Compose, Material 3, Hilt DI), Supabase (Auth, Postgrest, Realtime, RPCs), PostGIS, MapLibre Native SDK, Google Fused Location  
**Target Region**: Luzon, Philippines (SW: 11.8°N, 119.5°E to NE: 18.8°N, 124.5°E)  

---

## 1. Executive Summary & Current Status

### Completed Milestones
1. **Authentication & Session Management (100%)**:
   - Login, Create Account, Multi-step OTP verification (SMS & Email), password changes.
   - Role-based routing in `MainActivity.kt`: `customer` -> Customer Home, `driver` -> Driver Home.
2. **Driver "My Trips" Screen (100%)**:
   - `MyTripsScreen.kt` & `MyTripsViewModel.kt` connected to Supabase PostgREST & Realtime via `SupabaseDriverRepositoryImpl`.
   - Server-side stats card (Monthly Completed Trips, Total Allowance, On-Time Rate %) via PostgreSQL Security Invoker RPC `get_driver_monthly_stats` with local Manila boundary fallback.
   - Period filter bottom sheet (Asia/Manila monthly selector & "All Time").
   - Tab Filtering (`All`, `Completed`, `Ongoing`, `Cancelled`) mapped to DB statuses with status-driven card styling (`TripStatus`, `TripCardStyle`).
   - Keyset/range pagination (20 items/page) with pull-to-refresh and embedded location joins.
   - Realtime updates via Supabase Realtime channel (`deliveries`).
3. **Driver "Profile" & Compliance Engine (100%)**:
   - `DriverProfileScreen.kt` & `DriverProfileViewModel.kt` connected to Supabase PostgREST.
   - Header with dynamic avatar initials (`RC`), full name, assigned truck make/model & plate (or "No vehicle assigned"), and "Driver since Month Year".
   - Dynamic Compliance Status section querying `public.compliance_documents`.
   - Pure on-device status badge calculation (`ComplianceStatus.from(expiryDate, today)` with 60-day threshold constant): `EXPIRED` (Red), `EXPIRING SOON` (Orange), `VALID` (Green).
   - Dynamic document sorting (urgent items first) and Info Banner.
   - Personal Information section & Supabase Auth sign-out clearing backstack.
4. **Unit Tests (29 Passing)**:
   - Full unit test coverage across `ComplianceStatusTest`, `MyTripsViewModelTest`, `DriverProfileViewModelTest`, `DriverHomeViewModelTest`, `BookingMapperTest`, `DriverMapperTest`.

---

## 2. Key Architectural Decisions & Database Ground Rules

1. **Driver & Customer Identity Chains**:
   - `auth.uid()` -> `public.users.id` (1:1 link).
   - `auth.uid()` -> `public.customers.user_id` -> `public.customers.id` (`my_customer_id()`).
   - `auth.uid()` -> `public.drivers.user_id` -> `public.drivers.id` (`my_driver_id()`).
   - No driver ID or customer ID is ever accepted from or hardcoded in the client layer.
2. **Timezone & Dates**:
   - All server timestamps stored as `timestamptz`. Display/boundary logic calculated in `Asia/Manila` (UTC+8).
3. **Database Security & Permissions**:
   - Table-level grants for `authenticated` are **never revoked** (required by React staff web app).
   - Security is strictly enforced through Row Level Security (RLS) policies and `SECURITY DEFINER` RPCs with explicit `SET search_path = public, extensions`.
   - RPC function execution is revoked from `PUBLIC` and `anon`, and granted to `authenticated`.
4. **Target Lifecycle Stage Mapping**:

| Stage | `deliveries.status` | `shipments.status` |
| :--- | :--- | :--- |
| Customer booked | none yet | `pending` |
| Staff approved | none yet | `booked` |
| Driver assigned / route to pickup | `assigned` | `assigned` |
| Driver arrived at pickup (timer runs) | `assigned` + `pickup_arrived_at` | `assigned` |
| Customer showed / waiting to load | `loading` | `loading` |
| Cargo loaded / heading to destination | `in_transit` | `in_transit` |
| POD submitted | `delivered` | `review` |
| Staff confirm | none | `completed` |
| Incident during trip | unchanged | `delayed` (via trigger) |
| Customer no-show / cancelled | `cancelled` | `cancelled` (explicit) |

---

## 3. Database Migration Files

### Migration File 1: Core Main Migration (`20250224_phase_a1_main.sql`)

```sql
BEGIN;

-- Pre-check: Abort if double active deliveries exist
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.deliveries 
        WHERE status IN ('assigned'::delivery_status, 'loading'::delivery_status, 'in_transit'::delivery_status) 
        GROUP BY driver_id HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'MIGRATION_ABORTED: Existing active delivery violations found for one or more drivers';
    END IF;
END $$;

-- Helper Functions Hardening
CREATE OR REPLACE FUNCTION public.get_user_role()
 RETURNS text LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$ SELECT role::text FROM public.users WHERE id = auth.uid() AND is_active = true LIMIT 1; $function$;

CREATE OR REPLACE FUNCTION public.is_admin()
 RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$ SELECT EXISTS (SELECT 1 FROM public.users WHERE id = auth.uid() AND role IN ('admin', 'staff') AND is_active = true); $function$;

CREATE OR REPLACE FUNCTION public.my_customer_id()
 RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$ SELECT c.id FROM public.customers c JOIN public.users u ON u.id = c.user_id WHERE c.user_id = auth.uid() AND c.is_active = true AND u.is_active = true LIMIT 1; $function$;

CREATE OR REPLACE FUNCTION public.my_driver_id()
 RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO 'public'
AS $function$ SELECT d.id FROM public.drivers d JOIN public.users u ON u.id = d.user_id WHERE d.user_id = auth.uid() AND u.is_active = true AND d.status <> 'inactive'::driver_status LIMIT 1; $function$;

-- Fix protect_user_privileges(): Only admins can change role/is_active
CREATE OR REPLACE FUNCTION public.protect_user_privileges()
 RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public'
AS $function$
BEGIN
  IF (NEW.role IS DISTINCT FROM OLD.role OR NEW.is_active IS DISTINCT FROM OLD.is_active)
     AND auth.uid() IS NOT NULL 
     AND COALESCE(public.get_user_role(), '') <> 'admin' THEN
    RAISE EXCEPTION 'PERMISSION_DENIED: Only admins can change role or active status';
  END IF;
  RETURN NEW;
END; $function$;

-- Idempotency column
ALTER TABLE public.shipments ADD COLUMN IF NOT EXISTS client_request_id uuid NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_shipments_customer_client_req ON public.shipments (customer_id, client_request_id) WHERE client_request_id IS NOT NULL;

-- System Settings entries
INSERT INTO public.system_settings (key, value, description)
VALUES 
    ('road_distance_factor', '1.3'::jsonb, 'Road distance estimation factor for straight-line coordinates'),
    ('default_driver_allowance', '500.00'::jsonb, 'Default driver allowance per trip'),
    ('buffered_tracking_window_minutes', '5'::jsonb, 'Window in minutes to allow buffered GPS logs after trip arrival')
ON CONFLICT (key) DO NOTHING;

-- Driver Allowance Column & Trigger
ALTER TABLE public.deliveries ADD COLUMN IF NOT EXISTS driver_allowance numeric(10,2) NULL;

CREATE OR REPLACE FUNCTION public.stamp_delivery_allowance()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_default_allowance numeric;
BEGIN
    IF NEW.driver_allowance IS NULL THEN
        SELECT (value #>> '{}')::numeric INTO v_default_allowance FROM public.system_settings WHERE key = 'default_driver_allowance';
        NEW.driver_allowance := COALESCE(v_default_allowance, 500.00);
    END IF;
    RETURN NEW;
END; $$;

DROP TRIGGER IF EXISTS trg_stamp_delivery_allowance ON public.deliveries;
CREATE TRIGGER trg_stamp_delivery_allowance BEFORE INSERT ON public.deliveries FOR EACH ROW EXECUTE FUNCTION public.stamp_delivery_allowance();

-- Partial Unique Index for Driver Single Active Delivery
CREATE UNIQUE INDEX IF NOT EXISTS deliveries_one_active_per_driver ON public.deliveries (driver_id) WHERE status IN ('assigned'::delivery_status, 'loading'::delivery_status, 'in_transit'::delivery_status);

-- create_shipment RPC (Revised)
CREATE OR REPLACE FUNCTION public.create_shipment(
    p_client_request_id uuid DEFAULT NULL,
    p_pickup_location_id uuid DEFAULT NULL, p_pickup_address text DEFAULT NULL, p_pickup_lat numeric DEFAULT NULL, p_pickup_lng numeric DEFAULT NULL,
    p_dropoff_location_id uuid DEFAULT NULL, p_dropoff_address text DEFAULT NULL, p_dropoff_lat numeric DEFAULT NULL, p_dropoff_lng numeric DEFAULT NULL,
    p_requested_vehicle_type_id uuid DEFAULT NULL, p_cargo_category_id uuid DEFAULT NULL, p_cargo_description text DEFAULT NULL,
    p_weight_kg numeric DEFAULT NULL, p_special_handling text DEFAULT NULL, p_scheduled_departure timestamptz DEFAULT NULL
) RETURNS SETOF public.shipments LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, extensions AS $$
DECLARE
    v_customer_id uuid; v_has_cust_row boolean; v_existing_shipment public.shipments%ROWTYPE;
    v_pickup_addr text; v_pickup_lat numeric; v_pickup_lng numeric;
    v_dropoff_addr text; v_dropoff_lat numeric; v_dropoff_lng numeric;
    v_rate record; v_distance_km numeric(8,2) := 0.0; v_quoted_amount numeric(12,2) := 0.0;
    v_pricing_rate_id uuid := NULL; v_road_factor numeric := 1.3; v_new_shipment public.shipments%ROWTYPE;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED: User must be authenticated'; END IF;

    SELECT EXISTS(SELECT 1 FROM public.customers WHERE user_id = auth.uid()) INTO v_has_cust_row;
    IF NOT v_has_cust_row THEN RAISE EXCEPTION 'NO_CUSTOMER_PROFILE: No customer record linked to user account'; END IF;

    v_customer_id := public.my_customer_id();
    IF v_customer_id IS NULL THEN RAISE EXCEPTION 'CUSTOMER_INACTIVE: Account or customer profile is inactive'; END IF;

    IF p_client_request_id IS NOT NULL THEN
        SELECT * INTO v_existing_shipment FROM public.shipments WHERE customer_id = v_customer_id AND client_request_id = p_client_request_id LIMIT 1;
        IF FOUND THEN RETURN NEXT v_existing_shipment; RETURN; END IF;
    END IF;

    IF p_requested_vehicle_type_id IS NULL THEN RAISE EXCEPTION 'INVALID_VEHICLE_TYPE: Requested vehicle type is required'; END IF;
    IF NOT EXISTS (SELECT 1 FROM public.vehicle_types WHERE id = p_requested_vehicle_type_id AND is_active = true) THEN RAISE EXCEPTION 'INVALID_VEHICLE_TYPE: Selected vehicle type is invalid or inactive'; END IF;
    IF p_weight_kg IS NULL OR p_weight_kg <= 0 THEN RAISE EXCEPTION 'INVALID_WEIGHT: Cargo weight must be greater than 0 kg'; END IF;
    IF p_scheduled_departure IS NOT NULL AND p_scheduled_departure < (now() - interval '5 minutes') THEN RAISE EXCEPTION 'INVALID_SCHEDULE: Scheduled departure must be in the future'; END IF;

    -- Pickup Location Resolution
    IF p_pickup_location_id IS NOT NULL THEN
        SELECT COALESCE(address, name), latitude, longitude INTO v_pickup_addr, v_pickup_lat, v_pickup_lng FROM public.locations WHERE id = p_pickup_location_id AND is_active = true;
        IF NOT FOUND THEN RAISE EXCEPTION 'INVALID_PICKUP_LOCATION: Pickup location not found or inactive'; END IF;
    ELSE
        v_pickup_addr := p_pickup_address; v_pickup_lat := p_pickup_lat; v_pickup_lng := p_pickup_lng;
    END IF;

    IF v_pickup_addr IS NULL OR v_pickup_lat IS NULL OR v_pickup_lng IS NULL THEN RAISE EXCEPTION 'MISSING_COORDINATES: Pickup address and coordinates are required'; END IF;
    IF v_pickup_lat < 11.8 OR v_pickup_lat > 18.8 OR v_pickup_lng < 119.5 OR v_pickup_lng > 124.5 THEN RAISE EXCEPTION 'OUTSIDE_SERVICE_AREA: Pickup coordinates are outside Luzon service area'; END IF;

    -- Dropoff Location Resolution
    IF p_dropoff_location_id IS NOT NULL THEN
        SELECT COALESCE(address, name), latitude, longitude INTO v_dropoff_addr, v_dropoff_lat, v_dropoff_lng FROM public.locations WHERE id = p_dropoff_location_id AND is_active = true;
        IF NOT FOUND THEN RAISE EXCEPTION 'INVALID_DROPOFF_LOCATION: Dropoff location not found or inactive'; END IF;
    ELSE
        v_dropoff_addr := p_dropoff_address; v_dropoff_lat := p_dropoff_lat; v_dropoff_lng := p_dropoff_lng;
    END IF;

    IF v_dropoff_addr IS NULL OR v_dropoff_lat IS NULL OR v_dropoff_lng IS NULL THEN RAISE EXCEPTION 'MISSING_COORDINATES: Dropoff address and coordinates are required'; END IF;
    IF v_dropoff_lat < 11.8 OR v_dropoff_lat > 18.8 OR v_dropoff_lng < 119.5 OR v_dropoff_lng > 124.5 THEN RAISE EXCEPTION 'OUTSIDE_SERVICE_AREA: Dropoff coordinates are outside Luzon service area'; END IF;

    -- Location Reachability
    IF p_pickup_location_id IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM public.location_vehicle_types WHERE location_id = p_pickup_location_id) THEN
            IF NOT EXISTS (SELECT 1 FROM public.location_vehicle_types WHERE location_id = p_pickup_location_id AND vehicle_type_id = p_requested_vehicle_type_id) THEN
                RAISE EXCEPTION 'VEHICLE_NOT_ALLOWED_AT_PICKUP: Selected vehicle type is not permitted at pickup location';
            END IF;
        END IF;
    END IF;

    IF p_dropoff_location_id IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM public.location_vehicle_types WHERE location_id = p_dropoff_location_id) THEN
            IF NOT EXISTS (SELECT 1 FROM public.location_vehicle_types WHERE location_id = p_dropoff_location_id AND vehicle_type_id = p_requested_vehicle_type_id) THEN
                RAISE EXCEPTION 'VEHICLE_NOT_ALLOWED_AT_DROPOFF: Selected vehicle type is not permitted at dropoff location';
            END IF;
        END IF;
    END IF;

    -- Distance & Pricing
    SELECT (value #>> '{}')::numeric INTO v_road_factor FROM public.system_settings WHERE key = 'road_distance_factor';
    IF v_road_factor IS NULL THEN v_road_factor := 1.3; END IF;

    v_distance_km := ROUND((
        extensions.ST_Distance(
            extensions.ST_SetSRID(extensions.ST_MakePoint(v_pickup_lng::double precision, v_pickup_lat::double precision), 4326)::extensions.geography,
            extensions.ST_SetSRID(extensions.ST_MakePoint(v_dropoff_lng::double precision, v_dropoff_lat::double precision), 4326)::extensions.geography
        ) / 1000.0 * v_road_factor
    )::numeric, 2);

    SELECT id, base_rate, per_km_rate, per_kg_rate, min_charge INTO v_rate
    FROM public.pricing_rates
    WHERE (vehicle_type_id = p_requested_vehicle_type_id OR vehicle_type_id IS NULL)
      AND is_active = true AND effective_from <= CURRENT_DATE AND (effective_to IS NULL OR effective_to >= CURRENT_DATE)
    ORDER BY (CASE WHEN vehicle_type_id IS NOT NULL THEN 1 ELSE 2 END), effective_from DESC LIMIT 1;

    IF v_rate.id IS NULL THEN RAISE EXCEPTION 'NO_PRICING_RATE: No active pricing rate found for requested vehicle type'; END IF;

    v_pricing_rate_id := v_rate.id;
    v_quoted_amount := GREATEST(v_rate.min_charge, v_rate.base_rate + (v_distance_km * v_rate.per_km_rate) + (p_weight_kg * v_rate.per_kg_rate));

    BEGIN
        INSERT INTO public.shipments (
            customer_id, status, booking_source, cargo_category_id, cargo_description, weight_kg, special_handling,
            requested_vehicle_type_id, pickup_location_id, pickup_address, pickup_lat, pickup_lng,
            dropoff_location_id, dropoff_address, dropoff_lat, dropoff_lng, distance_km, scheduled_departure,
            pricing_rate_id, quoted_amount, created_by, client_request_id
        ) VALUES (
            v_customer_id, 'pending', 'mobile', p_cargo_category_id, p_cargo_description, p_weight_kg, p_special_handling,
            p_requested_vehicle_type_id, p_pickup_location_id, v_pickup_addr, v_pickup_lat, v_pickup_lng,
            p_dropoff_location_id, v_dropoff_addr, v_dropoff_lat, v_dropoff_lng, v_distance_km,
            COALESCE(p_scheduled_departure, now() + interval '1 hour'), v_pricing_rate_id, v_quoted_amount, auth.uid(), p_client_request_id
        ) RETURNING * INTO v_new_shipment;

        RETURN NEXT v_new_shipment;
    EXCEPTION WHEN unique_violation THEN
        SELECT * INTO v_existing_shipment FROM public.shipments WHERE customer_id = v_customer_id AND client_request_id = p_client_request_id LIMIT 1;
        IF FOUND THEN RETURN NEXT v_existing_shipment; RETURN; ELSE RAISE; END IF;
    END;
END; $$;

REVOKE EXECUTE ON FUNCTION public.create_shipment FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_shipment TO authenticated;

-- Hardened Tracking Logs Policy
DROP POLICY IF EXISTS "driver writes own tracking" ON public.tracking_logs;
CREATE POLICY "driver writes own tracking" ON public.tracking_logs FOR INSERT TO authenticated WITH CHECK (
    driver_id = my_driver_id() AND recorded_at <= (now() + interval '1 minute') AND
    recorded_at >= (SELECT assigned_at FROM public.deliveries WHERE id = tracking_logs.delivery_id) AND
    (truck_id IS NULL OR truck_id = (SELECT truck_id FROM public.deliveries WHERE id = tracking_logs.delivery_id)) AND
    delivery_id IN (
        SELECT id FROM public.deliveries WHERE driver_id = my_driver_id() AND (
            status = ANY (ARRAY['assigned'::delivery_status, 'loading'::delivery_status, 'in_transit'::delivery_status]) OR
            (status = 'delivered'::delivery_status AND arrived_at >= (now() - ((COALESCE((SELECT (value #>> '{}')::integer FROM public.system_settings WHERE key = 'buffered_tracking_window_minutes'), 5)) || ' minutes')::interval))
        )
    )
);

-- Bucket Limits & Storage Policies
UPDATE storage.buckets SET file_size_limit = 5242880, allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp'] WHERE id = 'delivery-documents';

DROP POLICY IF EXISTS "drivers upload pod files" ON storage.objects;
CREATE POLICY "drivers upload pod files" ON storage.objects FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'delivery-documents' AND (storage.foldername(name))[1] IN (SELECT id::text FROM public.deliveries WHERE driver_id = my_driver_id() AND status = 'in_transit'::delivery_status) AND (storage.filename(name)) NOT LIKE 'receipt-%'
);

DROP POLICY IF EXISTS "drivers upload expense receipts" ON storage.objects;
CREATE POLICY "drivers upload expense receipts" ON storage.objects FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'delivery-documents' AND (storage.foldername(name))[1] IN (SELECT id::text FROM public.deliveries WHERE driver_id = my_driver_id() AND status IN ('in_transit'::delivery_status, 'delivered'::delivery_status)) AND (storage.filename(name)) LIKE 'receipt-%'
);

DROP POLICY IF EXISTS "customers read own pod files" ON storage.objects;
CREATE POLICY "customers read own pod files" ON storage.objects FOR SELECT TO authenticated USING (
    bucket_id = 'delivery-documents' AND (storage.foldername(name))[1] IN (SELECT d.id::text FROM public.deliveries d JOIN public.shipments s ON s.id = d.shipment_id WHERE s.customer_id = my_customer_id()) AND ((storage.filename(name)) LIKE 'pod-%' OR (storage.filename(name)) LIKE 'signature-%')
);

-- Delivery Expenses Table & RLS
CREATE TABLE IF NOT EXISTS public.delivery_expenses (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    delivery_id uuid NOT NULL REFERENCES public.deliveries(id) ON DELETE CASCADE,
    driver_id uuid NOT NULL REFERENCES public.drivers(id) ON DELETE CASCADE,
    category text NOT NULL CHECK (category IN ('fuel', 'toll', 'parking', 'repair', 'other')),
    amount numeric(10,2) NOT NULL CHECK (amount > 0),
    receipt_path text NULL, status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
    reviewed_by uuid NULL REFERENCES public.users(id), reviewed_at timestamptz NULL,
    CONSTRAINT delivery_expenses_pkey PRIMARY KEY (id)
);

ALTER TABLE public.delivery_expenses ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS driver_insert_own_expenses ON public.delivery_expenses;
CREATE POLICY driver_insert_own_expenses ON public.delivery_expenses FOR INSERT TO authenticated WITH CHECK (
    driver_id = my_driver_id() AND status = 'pending' AND reviewed_by IS NULL AND reviewed_at IS NULL AND
    (receipt_path IS NULL OR receipt_path LIKE (delivery_id::text || '/receipt-%')) AND delivery_id IN (SELECT id FROM deliveries WHERE driver_id = my_driver_id())
);

DROP POLICY IF EXISTS driver_read_own_expenses ON public.delivery_expenses;
CREATE POLICY driver_read_own_expenses ON public.delivery_expenses FOR SELECT TO authenticated USING (driver_id = my_driver_id());

DROP POLICY IF EXISTS staff_full_access_expenses ON public.delivery_expenses;
CREATE POLICY staff_full_access_expenses ON public.delivery_expenses FOR ALL TO authenticated USING (get_user_role() = ANY (ARRAY['admin'::text, 'staff'::text])) WITH CHECK (get_user_role() = ANY (ARRAY['admin'::text, 'staff'::text]));

DROP TRIGGER IF EXISTS trg_delivery_expenses_audit ON public.delivery_expenses;
CREATE TRIGGER trg_delivery_expenses_audit AFTER INSERT OR DELETE OR UPDATE ON delivery_expenses FOR EACH ROW EXECUTE FUNCTION log_activity();

DROP TRIGGER IF EXISTS trg_delivery_expenses_updated ON public.delivery_expenses;
CREATE TRIGGER trg_delivery_expenses_updated BEFORE UPDATE ON delivery_expenses FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Driver Trip Stats RPC
CREATE OR REPLACE FUNCTION public.driver_trip_stats(p_period_start timestamptz DEFAULT NULL, p_period_end timestamptz DEFAULT NULL)
RETURNS TABLE (completed_trips_count integer, total_allowance numeric, on_time_rate numeric) LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_driver_id uuid; v_completed_count integer; v_total_allowance numeric; v_eligible_count integer; v_on_time_count integer; v_on_time_rate numeric;
BEGIN
    v_driver_id := public.my_driver_id();
    IF v_driver_id IS NULL THEN RETURN QUERY SELECT 0::integer, 0.0::numeric, NULL::numeric; RETURN; END IF;

    SELECT COUNT(*)::integer, COALESCE(SUM(d.driver_allowance), 0.0)::numeric,
        COUNT(CASE WHEN s.estimated_arrival IS NOT NULL THEN 1 END)::integer,
        COUNT(CASE WHEN s.estimated_arrival IS NOT NULL AND d.arrived_at <= s.estimated_arrival THEN 1 END)::integer
    INTO v_completed_count, v_total_allowance, v_eligible_count, v_on_time_count
    FROM public.deliveries d JOIN public.shipments s ON s.id = d.shipment_id
    WHERE d.driver_id = v_driver_id AND d.arrived_at IS NOT NULL AND (p_period_start IS NULL OR d.arrived_at >= p_period_start) AND (p_period_end IS NULL OR d.arrived_at < p_period_end);

    IF v_eligible_count = 0 THEN v_on_time_rate := NULL; ELSE v_on_time_rate := (v_on_time_count::numeric / v_eligible_count::numeric); END IF;
    RETURN QUERY SELECT v_completed_count, v_total_allowance, v_on_time_rate;
END; $$;

REVOKE EXECUTE ON FUNCTION public.driver_trip_stats FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.driver_trip_stats TO authenticated;

COMMIT;
```

---

### Migration File 2: Policy Drop (`20250224_phase_a1_drop_customer_insert_policy.sql`)

```sql
BEGIN;
DROP POLICY IF EXISTS "customer books own shipment" ON public.shipments;
COMMIT;
```

---

## 4. Next Steps on New Machine

1. **Database Setup**: Execute Migration File 1 and Migration File 3 (`20250224_phase_a1c_estimated_arrival_and_tests.sql`) in the Supabase SQL Editor. Run the combined test suite (`20250224_phase_a_combined_tests.sql`) to confirm 100% test pass rate.
2. **Android Client Integration**:
   - Upgrade `BookingViewModel` with Hilt DI (`@HiltViewModel`) and scope it to the booking navigation graph in `MainActivity.kt`.
   - Update `BookingSummaryScreen` to trigger `create_shipment` via `BookingsRepository`.
   - Handle `BookingError` domain types with localized error messages.
3. **Execute Migration File 2**: Drop the legacy `customer books own shipment` policy after client migration is complete.
