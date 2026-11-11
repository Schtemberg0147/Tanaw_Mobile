-- 07_driver_locations.sql: Live telemetry location tracking table for TANAW drivers

CREATE TABLE IF NOT EXISTS "public"."driver_locations" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "delivery_id" "uuid" NOT NULL,
    "driver_id" "uuid" NOT NULL,
    "latitude" double precision NOT NULL,
    "longitude" double precision NOT NULL,
    "heading" double precision DEFAULT 0.0,
    "speed" double precision DEFAULT 0.0,
    "recorded_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "driver_locations_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "driver_locations_delivery_id_fkey" FOREIGN KEY ("delivery_id") REFERENCES "public"."deliveries"("id") ON DELETE CASCADE,
    CONSTRAINT "driver_locations_driver_id_fkey" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id") ON DELETE CASCADE
);

ALTER TABLE "public"."driver_locations" OWNER TO "postgres";

-- Enable RLS
ALTER TABLE "public"."driver_locations" ENABLE ROW LEVEL SECURITY;

-- Driver insert policy
CREATE POLICY "drivers insert own location" ON "public"."driver_locations"
    FOR INSERT TO authenticated
    WITH CHECK ("driver_id" = "public"."my_driver_id"());

-- Customer read policy
CREATE POLICY "customers read assigned delivery location" ON "public"."driver_locations"
    FOR SELECT TO authenticated
    USING (
        "delivery_id" IN (
            SELECT d.id FROM "public"."deliveries" d
            JOIN "public"."shipments" s ON s.id = d.shipment_id
            WHERE s.customer_id = "public"."my_customer_id"()
        )
        OR "driver_id" = "public"."my_driver_id"()
    );

-- Add to Realtime publication
ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."driver_locations";
