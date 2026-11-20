# FTL Business Process Realignment & System Refactoring Plan

## 1. Executive Summary & Core Business Shift

### The Misalignment
Our previous system architecture assumed an on-demand, instant-dispatch model (similar to Lalamove or courier services). However, operating a **Full Truckload (FTL)** platform featuring heavy equipment (6-Wheelers, 10-Wheeler Wing Vans, Flatbeds, Refrigerated Vans) for Mabutol Trucking follows fundamentally different real-world operational rules:

1. **No Instant Pick-up ("Pick-up Now"):** Heavy truck loading requires warehouse staging, loading crews, forklift access, and LGU truck ban coordination. Turnaround time for loading/unloading alone ranges from 2 to 4+ hours per end. All bookings require a future `scheduled_departure` enforcing equipment-based lead times (`min_lead_time_hours`: 24h standard; 48h for Refrigerated Van and 10-Wheeler Flatbed).
2. **Estimated Delivery Window vs. Fixed Constant:** Total turnaround is dynamic:
   $$\text{Estimated Arrival} = \text{Scheduled Departure} + \text{Route Duration} + \text{Loading Buffer}$$
3. **Customer Linehaul vs. Internal Fleet Positioning:** Pricing charged to the customer must be anchored strictly to the trip distance between the **Pick-up Location** and the **Destination**. Variations in driver starting locations (e.g., driver starting in Cabanatuan, Nueva Ecija for a pickup in Balagtas, Bulacan) represent **internal deadhead costs** that cannot be passed on to the customer without standardizing rules.
4. **Dispatcher-in-the-Loop:** Heavy FTL shipments require human or semi-automated verification of truck assignment, toll routes, helper requirements, and mobilization fees before a binding price is presented to the customer.

---

## 2. End-to-End FTL Business Flow & Lifecycle

```
[ Mobile / Web Customer ]            [ Web Dispatcher Portal ]           [ Driver App & GPS ]
          |                                     |                                 |
1. Draft Booking -----------------------------> |                                 |
   - Pickup & Dropoff                           |                                 |
   - Scheduled Departure (>= 24h)               |                                 |
   - Vehicle Type Request                       |                                 |
                                       2. Run Quotation Builder                   |
                                          - Query RPC `get_ranked_trucks`         |
                                          - Calculate Linehaul & Tolls            |
                                          - Apply Mobilization Fee (if deadhead)  |
3. Receive Quote <----------------------------- |                                 |
   - Review Breakdown                           |                                 |
   - Accept & Confirm Quote                    4. Assign Truck & Driver ---------> |
5. Status: Confirmed -------------------------> |                                 5. Execute Delivery Lifecycle
                                                                                  - Arrived @ Pickup
                                                                                  - Loading Complete
                                                                                  - In Transit
                                                                                  - Unloaded / Delivered
                                                                                  |
                                                                               6. System Triggers Update:
                                                                                  - next_available_address
                                                                                  - next_available_at (Now)
```

### Key Phases:
1. **Draft Booking Request (Customer):** Customer inputs pickup, dropoff, requested truck type, and a scheduled pickup date/time (lead times validated). Initial Status: `booked` (or `pending_quote`).
2. **Office Review & Quotation Builder (Dispatcher via Web):** Dispatcher uses the Quotation Builder to rank candidate trucks by projected availability and proximity, auto-calculate linehaul and toll amounts, add an optional mobilization fee if empty deadhead distance exceeds the threshold, and set a quote expiration timer (`quote_expires_at`). Status transitions to `quoted` (with event `office_quoted`).
3. **Customer Quote Acceptance:** Customer accepts quote and confirms booking. Status: `confirmed`.
4. **Chained Dispatching & Assignment:** Dispatcher assigns the designated truck/driver. Status: `assigned` (or `driver_assigned`).
5. **Execution & Projected Availability Sync (Driver):** Live GPS and ETA route updates stream via tracking logs. Upon completing drop-off, database triggers automatically flip the truck to `available` at the drop-off location, enabling seamless chained pickups (e.g., Balagtas drop-off $\rightarrow$ Malolos pickup).

---

## 3. Pricing & Deadhead Economics Model

### A. Customer Linehaul Calculation
The base price shown to the customer is deterministic and repeatable:

$$\text{Raw Linehaul} = \text{Base Rate} + (\text{Trip Distance}_{\text{pickup } \rightarrow \text{dropoff}} \times \text{Per-KM Rate}) + (\text{Cargo Weight}_{\text{kg}} \times \text{Per-KG Rate})$$

$$\text{Linehaul Amount} = \max(\text{Raw Linehaul}, \text{Min Charge})$$

### B. Mobilization Surcharge (Excess Deadhead)
Empty truck repositioning distance is tracked internally as `deadhead_km`:

$$\text{Deadhead Distance} = \text{Distance}(\text{Truck Projected Location} \rightarrow \text{Pick-up Location})$$

$$\text{Excess Deadhead} = \max(0, \text{Deadhead Distance} - \text{Free Deadhead Threshold}_{\text{km}})$$

$$\text{Mobilization Fee} = \text{Excess Deadhead} \times \text{Deadhead Per-KM Rate}$$

$$\text{Quoted Amount} = \text{Linehaul Amount} + \text{Toll Amount} + \text{Mobilization Fee}$$

$$\text{Final Amount} = \text{COALESCE}(\text{Override Amount}, \text{Quoted Amount})$$

---

## 4. Database Schema Migrations (`public.*`)

Execute this SQL migration in Supabase to extend existing tables without breaking current queries or generated columns.

```sql
-- 1. Equipment-specific lead times & loading/unloading dwell buffers
ALTER TABLE public.vehicle_types
  ADD COLUMN IF NOT EXISTS min_lead_time_hours integer NOT NULL DEFAULT 24 CHECK (min_lead_time_hours >= 0),
  ADD COLUMN IF NOT EXISTS loading_buffer_minutes integer NOT NULL DEFAULT 180 CHECK (loading_buffer_minutes >= 0);

-- Set 48-hour prep lead time for specialized equipment (Refrigerated & Flatbed)
UPDATE public.vehicle_types
SET min_lead_time_hours = 48
WHERE name ILIKE '%refrigerated%' OR name ILIKE '%flat%';

-- 2. Deadhead thresholds on existing pricing_rates
ALTER TABLE public.pricing_rates
  ADD COLUMN IF NOT EXISTS free_deadhead_km numeric NOT NULL DEFAULT 30 CHECK (free_deadhead_km >= 0),
  ADD COLUMN IF NOT EXISTS deadhead_per_km_rate numeric NOT NULL DEFAULT 0 CHECK (deadhead_per_km_rate >= 0);

-- 3. Price breakdown, internal deadhead metric, and quote expiry on shipments
ALTER TABLE public.shipments
  ADD COLUMN IF NOT EXISTS linehaul_amount numeric CHECK (linehaul_amount >= 0),
  ADD COLUMN IF NOT EXISTS toll_amount numeric NOT NULL DEFAULT 0 CHECK (toll_amount >= 0),
  ADD COLUMN IF NOT EXISTS mobilization_fee numeric NOT NULL DEFAULT 0 CHECK (mobilization_fee >= 0),
  ADD COLUMN IF NOT EXISTS deadhead_km numeric NOT NULL DEFAULT 0 CHECK (deadhead_km >= 0),
  ADD COLUMN IF NOT EXISTS quote_expires_at timestamp with time zone;

-- 4. Projected availability & location on trucks for chained dispatching
ALTER TABLE public.trucks
  ADD COLUMN IF NOT EXISTS home_base_location_id uuid REFERENCES public.locations(id),
  ADD COLUMN IF NOT EXISTS current_lat numeric CHECK (current_lat IS NULL OR (current_lat >= -90 AND current_lat <= 90)),
  ADD COLUMN IF NOT EXISTS current_lng numeric CHECK (current_lng IS NULL OR (current_lng >= -180 AND current_lng <= 180)),
  ADD COLUMN IF NOT EXISTS last_location_at timestamp with time zone,
  ADD COLUMN IF NOT EXISTS next_available_address text,
  ADD COLUMN IF NOT EXISTS next_available_lat numeric CHECK (next_available_lat IS NULL OR (next_available_lat >= -90 AND next_available_lat <= 90)),
  ADD COLUMN IF NOT EXISTS next_available_lng numeric CHECK (next_available_lng IS NULL OR (next_available_lng >= -180 AND next_available_lng <= 180)),
  ADD COLUMN IF NOT EXISTS next_available_at timestamp with time zone;

CREATE INDEX IF NOT EXISTS idx_trucks_next_available_at ON public.trucks (next_available_at);
CREATE INDEX IF NOT EXISTS idx_shipments_scheduled_departure ON public.shipments (scheduled_departure);
```

---

## 5. Automated Chained Availability Triggers

These triggers maintain `public.trucks` projected locations across assignments, route ETA updates, and delivery completions.

```sql
-- A. Sync truck projected location & availability when a delivery status changes
CREATE OR REPLACE FUNCTION public.sync_truck_availability_on_delivery()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_shipment record;
  v_buffer_mins integer := 180;
  v_unloading_mins integer := 90;
  v_eta timestamp with time zone;
  v_has_other_active boolean;
BEGIN
  SELECT
    s.dropoff_address,
    s.dropoff_lat,
    s.dropoff_lng,
    s.scheduled_departure,
    s.estimated_arrival,
    s.route_duration_minutes,
    vt.loading_buffer_minutes
  INTO v_shipment
  FROM public.shipments s
  LEFT JOIN public.vehicle_types vt ON vt.id = s.requested_vehicle_type_id
  WHERE s.id = NEW.shipment_id;

  v_buffer_mins := COALESCE(v_shipment.loading_buffer_minutes, 180);
  v_unloading_mins := GREATEST(v_buffer_mins / 2, 60);

  -- 1. Delivery Completed / Delivered -> Truck is at drop-off & immediately available
  IF NEW.status::text IN ('completed', 'delivered') THEN
    UPDATE public.trucks
    SET
      current_lat = COALESCE(v_shipment.dropoff_lat, current_lat),
      current_lng = COALESCE(v_shipment.dropoff_lng, current_lng),
      last_location_at = now(),
      next_available_address = v_shipment.dropoff_address,
      next_available_lat = COALESCE(v_shipment.dropoff_lat, current_lat),
      next_available_lng = COALESCE(v_shipment.dropoff_lng, current_lng),
      next_available_at = now(),
      updated_at = now()
    WHERE id = NEW.truck_id;

  -- 2. Delivery Cancelled -> Fallback to home base or current position if no other active deliveries exist
  ELSIF NEW.status::text = 'cancelled' THEN
    SELECT EXISTS (
      SELECT 1
      FROM public.deliveries d
      WHERE d.truck_id = NEW.truck_id
        AND d.id <> NEW.id
        AND d.status::text NOT IN ('completed', 'delivered', 'cancelled')
    ) INTO v_has_other_active;

    IF NOT v_has_other_active THEN
      UPDATE public.trucks t
      SET
        next_available_address = COALESCE(l.address, l.name),
        next_available_lat = COALESCE(t.current_lat, l.latitude),
        next_available_lng = COALESCE(t.current_lng, l.longitude),
        next_available_at = now(),
        updated_at = now()
      FROM public.locations l
      WHERE t.id = NEW.truck_id
        AND l.id = t.home_base_location_id;

      UPDATE public.trucks
      SET
        next_available_lat = current_lat,
        next_available_lng = current_lng,
        next_available_at = now(),
        updated_at = now()
      WHERE id = NEW.truck_id
        AND home_base_location_id IS NULL;
    END IF;

  -- 3. Active Delivery -> Project availability at destination + unloading buffer
  ELSE
    v_eta := COALESCE(
      v_shipment.estimated_arrival,
      COALESCE(v_shipment.scheduled_departure, now())
        + make_interval(mins => COALESCE(v_shipment.route_duration_minutes, 120)::int + v_buffer_mins)
    );

    UPDATE public.trucks
    SET
      next_available_address = v_shipment.dropoff_address,
      next_available_lat = v_shipment.dropoff_lat,
      next_available_lng = v_shipment.dropoff_lng,
      next_available_at = v_eta + make_interval(mins => v_unloading_mins),
      updated_at = now()
    WHERE id = NEW.truck_id;
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_truck_availability_on_delivery ON public.deliveries;
CREATE TRIGGER trg_sync_truck_availability_on_delivery
  AFTER INSERT OR UPDATE OF status, truck_id
  ON public.deliveries
  FOR EACH ROW
  EXECUTE FUNCTION public.sync_truck_availability_on_delivery();


-- B. Sync truck's next_available_at when live route ETA updates occur
CREATE OR REPLACE FUNCTION public.sync_truck_eta_from_route()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_truck_id uuid;
  v_unloading_mins integer := 90;
BEGIN
  IF NEW.leg = 'destination' THEN
    SELECT
      d.truck_id,
      GREATEST(COALESCE(vt.loading_buffer_minutes, 180) / 2, 60)
    INTO v_truck_id, v_unloading_mins
    FROM public.deliveries d
    JOIN public.shipments s ON s.id = d.shipment_id
    LEFT JOIN public.vehicle_types vt ON vt.id = s.requested_vehicle_type_id
    WHERE d.id = NEW.delivery_id;

    IF v_truck_id IS NOT NULL THEN
      UPDATE public.trucks
      SET
        next_available_at = NEW.eta_at + make_interval(mins => v_unloading_mins),
        updated_at = now()
      WHERE id = v_truck_id;
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_truck_eta_from_route ON public.delivery_route_eta;
CREATE TRIGGER trg_sync_truck_eta_from_route
  AFTER INSERT OR UPDATE OF eta_at, leg
  ON public.delivery_route_eta
  FOR EACH ROW
  EXECUTE FUNCTION public.sync_truck_eta_from_route();


-- C. Keep current_lat and current_lng updated from driver tracking logs
CREATE OR REPLACE FUNCTION public.sync_truck_gps_from_tracking()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  IF NEW.truck_id IS NOT NULL THEN
    UPDATE public.trucks
    SET
      current_lat = NEW.latitude,
      current_lng = NEW.longitude,
      last_location_at = NEW.recorded_at
    WHERE id = NEW.truck_id
      AND (last_location_at IS NULL OR NEW.recorded_at >= last_location_at);
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_truck_gps_from_tracking ON public.tracking_logs;
CREATE TRIGGER trg_sync_truck_gps_from_tracking
  AFTER INSERT ON public.tracking_logs
  FOR EACH ROW
  EXECUTE FUNCTION public.sync_truck_gps_from_tracking();
```

---

## 6. Smart Dispatch & Truck Ranking RPC Function

This RPC leverages PostGIS geography calculations to rank candidate trucks by projected location proximity and time availability.

```sql
CREATE OR REPLACE FUNCTION public.get_ranked_trucks_for_shipment(p_shipment_id uuid)
RETURNS TABLE (
  truck_id uuid,
  fleet_code text,
  plate_number text,
  vehicle_type_id uuid,
  vehicle_type_name text,
  truck_status text,
  assigned_driver_id uuid,
  driver_name text,
  projected_location_label text,
  projected_lat numeric,
  projected_lng numeric,
  available_at timestamp with time zone,
  estimated_deadhead_km numeric,
  can_make_pickup boolean,
  schedule_gap_minutes integer
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
AS $$
DECLARE
  v_shipment record;
BEGIN
  SELECT
    s.requested_vehicle_type_id,
    s.pickup_lat,
    s.pickup_lng,
    COALESCE(s.scheduled_departure, now()) AS target_pickup
  INTO v_shipment
  FROM public.shipments s
  WHERE s.id = p_shipment_id;

  RETURN QUERY
  WITH truck_positions AS (
    SELECT
      t.id,
      t.fleet_code,
      t.plate_number,
      t.vehicle_type_id,
      vt.name AS vehicle_type_name,
      t.status::text AS truck_status,
      t.assigned_driver_id,
      d.full_name AS driver_name,
      COALESCE(
        t.next_available_address,
        CASE WHEN t.current_lat IS NOT NULL THEN 'Current GPS Position' END,
        l.address,
        l.name,
        'Home Base'
      ) AS loc_label,
      COALESCE(t.next_available_lat, t.current_lat, l.latitude) AS ref_lat,
      COALESCE(t.next_available_lng, t.current_lng, l.longitude) AS ref_lng,
      COALESCE(t.next_available_at, now()) AS avail_at
    FROM public.trucks t
    LEFT JOIN public.vehicle_types vt ON vt.id = t.vehicle_type_id
    LEFT JOIN public.drivers d ON d.id = t.assigned_driver_id
    LEFT JOIN public.locations l ON l.id = t.home_base_location_id
    WHERE t.status::text NOT IN ('maintenance', 'inactive', 'retired')
      AND (
        v_shipment.requested_vehicle_type_id IS NULL
        OR t.vehicle_type_id = v_shipment.requested_vehicle_type_id
      )
  ),
  computed AS (
    SELECT
      tp.*,
      CASE
        WHEN tp.ref_lat IS NOT NULL
          AND tp.ref_lng IS NOT NULL
          AND v_shipment.pickup_lat IS NOT NULL
          AND v_shipment.pickup_lng IS NOT NULL
        THEN ROUND(
          (
            ST_Distance(
              ST_SetSRID(ST_MakePoint(tp.ref_lng::double precision, tp.ref_lat::double precision), 4326)::geography,
              ST_SetSRID(ST_MakePoint(v_shipment.pickup_lng::double precision, v_shipment.pickup_lat::double precision), 4326)::geography
            ) / 1000.0 * 1.25
          )::numeric,
          2
        )
        ELSE 0::numeric
      END AS deadhead_km
    FROM truck_positions tp
  )
  SELECT
    c.id AS truck_id,
    c.fleet_code,
    c.plate_number,
    c.vehicle_type_id,
    c.vehicle_type_name,
    c.truck_status,
    c.assigned_driver_id,
    c.driver_name,
    c.loc_label AS projected_location_label,
    c.ref_lat AS projected_lat,
    c.ref_lng AS projected_lng,
    c.avail_at AS available_at,
    c.deadhead_km AS estimated_deadhead_km,
    (
      c.avail_at + make_interval(mins => ROUND((c.deadhead_km / 40.0) * 60 + 30)::int)
      <= v_shipment.target_pickup
    ) AS can_make_pickup,
    EXTRACT(
      EPOCH FROM (
        v_shipment.target_pickup - (c.avail_at + make_interval(mins => ROUND((c.deadhead_km / 40.0) * 60)::int))
      )
    )::integer / 60 AS schedule_gap_minutes
  FROM computed c
  ORDER BY
    can_make_pickup DESC,
    c.deadhead_km ASC,
    c.avail_at ASC;
END;
$$;
```

---

## 7. Web Dispatcher Quotation Builder Integration

TypeScript calculation helper and submission function for the Web Admin Portal:

```typescript
import { supabase } from '@/lib/supabase';

export interface PricingRate {
  id: string;
  vehicle_type_id: string;
  base_rate: number;
  per_km_rate: number;
  per_kg_rate: number;
  min_charge: number;
  free_deadhead_km: number;
  deadhead_per_km_rate: number;
}

export interface QuoteBreakdown {
  linehaulAmount: number;
  tollAmount: number;
  deadheadKm: number;
  excessDeadheadKm: number;
  suggestedMobilizationFee: number;
  mobilizationFee: number;
  quotedAmount: number;
  finalAmount: number;
}

/**
 * Calculates an itemized FTL quotation based on customer trip distance and selected truck deadhead.
 */
export function calculateFtlQuote(params: {
  rate: PricingRate;
  distanceKm: number;
  weightKg?: number | null;
  tollAmount?: number;
  deadheadKm?: number;
  overrideMobilizationFee?: number;
  overrideTotalAmount?: number | null;
}): QuoteBreakdown {
  const {
    rate,
    distanceKm,
    weightKg = 0,
    tollAmount = 0,
    deadheadKm = 0,
    overrideMobilizationFee,
    overrideTotalAmount = null,
  } = params;

  // 1. Linehaul is strictly anchored to Pick-up -> Destination
  const rawLinehaul =
    rate.base_rate +
    distanceKm * rate.per_km_rate +
    (weightKg ?? 0) * rate.per_kg_rate;

  const linehaulAmount = Math.round(Math.max(rawLinehaul, rate.min_charge) * 100) / 100;

  // 2. Mobilization applies only if empty deadhead exceeds free threshold
  const excessDeadheadKm = Math.max(0, deadheadKm - rate.free_deadhead_km);
  const suggestedMobilizationFee =
    Math.round(excessDeadheadKm * rate.deadhead_per_km_rate * 100) / 100;

  const mobilizationFee =
    overrideMobilizationFee !== undefined
      ? overrideMobilizationFee
      : suggestedMobilizationFee;

  const quotedAmount =
    Math.round((linehaulAmount + tollAmount + mobilizationFee) * 100) / 100;

  const finalAmount =
    overrideTotalAmount !== null && overrideTotalAmount !== undefined
      ? overrideTotalAmount
      : quotedAmount;

  return {
    linehaulAmount,
    tollAmount,
    deadheadKm,
    excessDeadheadKm,
    suggestedMobilizationFee,
    mobilizationFee,
    quotedAmount,
    finalAmount,
  };
}

/**
 * Saves quotation to Supabase and logs the 'office_quoted' event in shipment_events.
 */
export async function submitOfficeQuotation(params: {
  shipmentId: string;
  actorUserId: string;
  pricingRateId: string;
  quotedStatusEnum: string; // e.g., 'quoted'
  breakdown: QuoteBreakdown;
  overrideReason?: string | null;
  quoteValidHours?: number;
}) {
  const {
    shipmentId,
    actorUserId,
    pricingRateId,
    quotedStatusEnum,
    breakdown,
    overrideReason = null,
    quoteValidHours = 12,
  } = params;

  const quoteExpiresAt = new Date(
    Date.now() + quoteValidHours * 60 * 60 * 1000
  ).toISOString();

  const hasOverride = breakdown.finalAmount !== breakdown.quotedAmount;

  const { error: updateError } = await supabase
    .from('shipments')
    .update({
      status: quotedStatusEnum,
      pricing_rate_id: pricingRateId,
      linehaul_amount: breakdown.linehaulAmount,
      toll_amount: breakdown.tollAmount,
      mobilization_fee: breakdown.mobilizationFee,
      deadhead_km: breakdown.deadheadKm,
      quoted_amount: breakdown.quotedAmount,
      override_amount: hasOverride ? breakdown.finalAmount : null,
      override_reason: hasOverride ? overrideReason : null,
      final_amount: breakdown.finalAmount,
      quote_expires_at: quoteExpiresAt,
      updated_at: new Date().toISOString(),
    })
    .eq('id', shipmentId);

  if (updateError) throw updateError;

  const { error: eventError } = await supabase.from('shipment_events').insert({
    shipment_id: shipmentId,
    event_type: 'office_quoted',
    actor_id: actorUserId,
    meta: {
      linehaul_amount: breakdown.linehaulAmount,
      toll_amount: breakdown.tollAmount,
      mobilization_fee: breakdown.mobilizationFee,
      deadhead_km: breakdown.deadheadKm,
      quoted_amount: breakdown.quotedAmount,
      final_amount: breakdown.finalAmount,
      quote_expires_at: quoteExpiresAt,
    },
  });

  if (eventError) throw eventError;
}
```

---

## 8. Frontend & Mobile Action Plan

### Customer Mobile & Web App
- Replace instant `"Pick-up now"` selector with a **Scheduled Pick-up Date & Time Picker** respecting `vehicle_types.min_lead_time_hours`.
- Display linehaul vs mobilization fee breakdowns once quotes transition to `quoted`.

### Web Admin Portal
- Embed `calculateFtlQuote()` inside the Quotation Drawer modal.
- Call `get_ranked_trucks_for_shipment` to select trucks by projected availability and deadhead distance.

### Driver Mobile App
- Trigger location/status updates (`completed`, `delivered`) upon unloading to execute chained availability updates on `trucks`.