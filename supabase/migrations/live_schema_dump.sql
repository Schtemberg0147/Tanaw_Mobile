


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;




ALTER SCHEMA "public" OWNER TO "postgres";


CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "postgis" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";






CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";






CREATE TYPE "public"."compliance_doc_type" AS ENUM (
    'drivers_license',
    'medical_certificate',
    'nbi_clearance',
    'lto_registration',
    'insurance',
    'emission_test',
    'roadworthiness_inspection',
    'franchise_permit',
    'other'
);


ALTER TYPE "public"."compliance_doc_type" OWNER TO "postgres";


CREATE TYPE "public"."delivery_status" AS ENUM (
    'assigned',
    'loading',
    'in_transit',
    'delivered',
    'cancelled'
);


ALTER TYPE "public"."delivery_status" OWNER TO "postgres";


CREATE TYPE "public"."document_kind" AS ENUM (
    'proof_of_delivery',
    'receipt',
    'waybill',
    'other'
);


ALTER TYPE "public"."document_kind" OWNER TO "postgres";


CREATE TYPE "public"."driver_status" AS ENUM (
    'available',
    'on_trip',
    'off_duty',
    'on_leave',
    'inactive'
);


ALTER TYPE "public"."driver_status" OWNER TO "postgres";


CREATE TYPE "public"."incident_category" AS ENUM (
    'traffic',
    'weather',
    'late_loading',
    'breakdown',
    'accident',
    'road_closure',
    'other'
);


ALTER TYPE "public"."incident_category" OWNER TO "postgres";


CREATE TYPE "public"."location_kind" AS ENUM (
    'hub',
    'customer_site',
    'other'
);


ALTER TYPE "public"."location_kind" OWNER TO "postgres";


CREATE TYPE "public"."maintenance_kind" AS ENUM (
    'preventive',
    'repair',
    'inspection',
    'tire',
    'other'
);


ALTER TYPE "public"."maintenance_kind" OWNER TO "postgres";


CREATE TYPE "public"."maintenance_status" AS ENUM (
    'scheduled',
    'in_progress',
    'completed',
    'cancelled'
);


ALTER TYPE "public"."maintenance_status" OWNER TO "postgres";


CREATE TYPE "public"."notification_type" AS ENUM (
    'shipment_status',
    'booking_confirmed',
    'assignment',
    'delay',
    'compliance_expiry',
    'payment',
    'message',
    'system'
);


ALTER TYPE "public"."notification_type" OWNER TO "postgres";


CREATE TYPE "public"."payment_method" AS ENUM (
    'cash',
    'bank_transfer',
    'gcash',
    'check',
    'other'
);


ALTER TYPE "public"."payment_method" OWNER TO "postgres";


CREATE TYPE "public"."payment_status" AS ENUM (
    'pending',
    'verified',
    'rejected',
    'refunded'
);


ALTER TYPE "public"."payment_status" OWNER TO "postgres";


CREATE TYPE "public"."review_status" AS ENUM (
    'pending_review',
    'approved',
    'rejected'
);


ALTER TYPE "public"."review_status" OWNER TO "postgres";


CREATE TYPE "public"."shipment_status" AS ENUM (
    'pending',
    'booked',
    'assigned',
    'loading',
    'in_transit',
    'delayed',
    'review',
    'completed',
    'cancelled'
);


ALTER TYPE "public"."shipment_status" OWNER TO "postgres";


CREATE TYPE "public"."truck_status" AS ENUM (
    'available',
    'on_trip',
    'maintenance',
    'inactive'
);


ALTER TYPE "public"."truck_status" OWNER TO "postgres";


CREATE TYPE "public"."user_role" AS ENUM (
    'admin',
    'staff',
    'driver',
    'customer'
);


ALTER TYPE "public"."user_role" OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."create_customer_for_user"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
BEGIN
    IF NEW.role = 'customer'::public.user_role THEN

        INSERT INTO public.customers (
            user_id,
            name,
            email,
            phone
        )
        VALUES (
            NEW.id,
            NEW.full_name,
            NEW.email,
            NEW.phone
        )
        ON CONFLICT (user_id) DO NOTHING;

    END IF;

    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."create_customer_for_user"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."driver_set_delivery_status"("p_delivery" "uuid", "p_status" "public"."delivery_status") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare v_cur delivery_status;
begin
  select status into v_cur from deliveries
   where id = p_delivery and driver_id = (select id from drivers where user_id = auth.uid());
  if v_cur is null then raise exception 'Delivery not found or not assigned to you'; end if;
  if p_status not in ('loading', 'in_transit', 'delivered') then
    raise exception 'Drivers may only set loading, in_transit or delivered';
  end if;
  if v_cur in ('delivered', 'cancelled') then raise exception 'Delivery already closed'; end if;
  update deliveries set status = p_status where id = p_delivery;
end $$;


ALTER FUNCTION "public"."driver_set_delivery_status"("p_delivery" "uuid", "p_status" "public"."delivery_status") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_user_role"() RETURNS "text"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
  select role::text from public.users where id = auth.uid() and is_active limit 1;
$$;


ALTER FUNCTION "public"."get_user_role"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_new_user"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
  assigned_role public.user_role;
begin
  -- Safely parse role metadata; fallback to 'customer' if missing or invalid
  begin
    assigned_role := (new.raw_user_meta_data->>'role')::public.user_role;
  exception when others then
    assigned_role := 'customer'::public.user_role;
  end;

  if assigned_role is null then
    assigned_role := 'customer'::public.user_role;
  end if;

  -- Insert or update public.users
  insert into public.users (id, email, full_name, role)
  values (
    new.id,
    coalesce(new.email, ''),
    coalesce(new.raw_user_meta_data->>'full_name', ''),
    assigned_role
  )
  on conflict (id) do update set
    email = excluded.email,
    updated_at = now();

  return new;
end;
$$;


ALTER FUNCTION "public"."handle_new_user"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."is_admin"() RETURNS boolean
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
  select exists (select 1 from public.users
                 where id = auth.uid() and is_active and role in ('admin','staff'));
$$;


ALTER FUNCTION "public"."is_admin"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare 
  v_new jsonb := case when tg_op <> 'DELETE' then to_jsonb(new) end;
  v_old jsonb := case when tg_op <> 'INSERT' then to_jsonb(old) end;
begin
  insert into activity_logs (user_id, action, entity_type, entity_id, details)
  values (
    auth.uid(),
    tg_op, 
    tg_table_name,
    coalesce(v_new->>'id', v_old->>'id', v_new->>'key', v_old->>'key'),
    jsonb_strip_nulls(jsonb_build_object('old', v_old, 'new', v_new))
  );
  
  return coalesce(new, old);
end $$;


ALTER FUNCTION "public"."log_activity"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."my_customer_id"() RETURNS "uuid"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
  select c.id from public.customers c
  join public.users u on u.id = c.user_id
  where c.user_id = auth.uid() and c.is_active and u.is_active
  limit 1;
$$;


ALTER FUNCTION "public"."my_customer_id"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."my_customer_shipment_ids"() RETURNS SETOF "uuid"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
  select id from public.shipments where customer_id = public.my_customer_id();
$$;


ALTER FUNCTION "public"."my_customer_shipment_ids"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."my_driver_id"() RETURNS "uuid"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
  select d.id from public.drivers d
  join public.users u on u.id = d.user_id
  where d.user_id = auth.uid() and u.is_active and d.status <> 'inactive'
  limit 1;
$$;


ALTER FUNCTION "public"."my_driver_id"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."my_driver_shipment_ids"() RETURNS SETOF "uuid"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
  select shipment_id from public.deliveries where driver_id = public.my_driver_id();
$$;


ALTER FUNCTION "public"."my_driver_shipment_ids"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."nearby_locations"("p_lat" double precision, "p_lng" double precision, "p_radius_m" integer DEFAULT 50000, "p_vehicle_type_id" "uuid" DEFAULT NULL::"uuid") RETURNS TABLE("id" "uuid", "name" "text", "sublabel" "text", "address" "text", "city" "text", "province" "text", "latitude" numeric, "longitude" numeric, "kind" "public"."location_kind", "distance_m" double precision)
    LANGUAGE "sql" STABLE
    AS $$
  select l.id, l.name, l.sublabel, l.address, l.city, l.province,
         l.latitude, l.longitude, l.kind,
         st_distance(l.geog, st_makepoint(p_lng, p_lat)::geography) as distance_m
  from public.locations l
  where l.is_active
    and st_dwithin(l.geog, st_makepoint(p_lng, p_lat)::geography, p_radius_m)
    and (p_vehicle_type_id is null or exists (
      select 1 from public.location_vehicle_types lvt
      where lvt.location_id = l.id and lvt.vehicle_type_id = p_vehicle_type_id))
  order by distance_m
  limit 100;
$$;


ALTER FUNCTION "public"."nearby_locations"("p_lat" double precision, "p_lng" double precision, "p_radius_m" integer, "p_vehicle_type_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."notify_user"("p_user" "uuid", "p_type" "public"."notification_type", "p_title" "text", "p_body" "text" DEFAULT NULL::"text", "p_shipment" "uuid" DEFAULT NULL::"uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
begin
  if p_user is null then return; end if;
  if exists (select 1 from notification_preferences
             where user_id = p_user and type = p_type and channel = 'in_app' and not enabled) then
    return;
  end if;
  insert into notifications (user_id, type, title, body, shipment_id)
  values (p_user, p_type, p_title, p_body, p_shipment);
end $$;


ALTER FUNCTION "public"."notify_user"("p_user" "uuid", "p_type" "public"."notification_type", "p_title" "text", "p_body" "text", "p_shipment" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."on_incident_created"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare v_track text; v_cust uuid; r record;
begin
  select s.tracking_number, c.user_id into v_track, v_cust
    from shipments s join customers c on c.id = s.customer_id where s.id = new.shipment_id;

  update shipments set status = 'delayed'
   where id = new.shipment_id and status in ('loading', 'in_transit');

  perform public.notify_user(v_cust, 'delay', 'Shipment ' || v_track || ' is delayed',
                             coalesce(new.description, new.category::text), new.shipment_id);
  for r in select id from users where role in ('admin', 'staff') and is_active loop
    perform public.notify_user(r.id, 'delay', 'Incident on ' || v_track || ': ' || new.category::text,
                               new.description, new.shipment_id);
  end loop;
  return null;
end $$;


ALTER FUNCTION "public"."on_incident_created"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."on_shipment_status_change"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare v_customer_user uuid;
begin
  if tg_op = 'INSERT' or new.status is distinct from old.status then
    insert into shipment_status_history (shipment_id, from_status, to_status, changed_by)
    values (new.id, case when tg_op = 'UPDATE' then old.status end, new.status, auth.uid());

    select user_id into v_customer_user from customers where id = new.customer_id;
    perform public.notify_user(
      v_customer_user,
      case new.status when 'booked' then 'booking_confirmed'::notification_type
                      when 'delayed' then 'delay'::notification_type
                      else 'shipment_status'::notification_type end,
      'Shipment ' || new.tracking_number || ' is now ' || replace(new.status::text, '_', ' '),
      null, new.id);
  end if;
  return null;
end $$;


ALTER FUNCTION "public"."on_shipment_status_change"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."protect_user_privileges"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
begin
  if (new.role is distinct from old.role or new.is_active is distinct from old.is_active)
     and auth.uid() is not null
     and coalesce(public.get_user_role(), '') <> 'admin' then
    raise exception 'Only admins can change role or active status';
  end if;
  return new;
end $$;


ALTER FUNCTION "public"."protect_user_privileges"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."set_updated_at"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
  new.updated_at := now();
  return new;
end $$;


ALTER FUNCTION "public"."set_updated_at"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."stamp_delivery_timestamps"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
  if new.status = 'in_transit' and new.departed_at is null then
    new.departed_at := now();
  elsif new.status = 'delivered' and new.arrived_at is null then
    new.arrived_at := now();
  end if;
  return new;
end $$;


ALTER FUNCTION "public"."stamp_delivery_timestamps"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."stamp_shipment_dates"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
  if new.status = 'review' and old.status is distinct from 'review' and new.actual_delivery_at is null then
    new.actual_delivery_at := now();
  end if;
  if new.status = 'cancelled' and new.cancelled_at is null then
    new.cancelled_at := now();
  end if;
  return new;
end $$;


ALTER FUNCTION "public"."stamp_shipment_dates"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."sync_from_delivery"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare v_ship shipment_status;
begin
  v_ship := case new.status when 'assigned'   then 'assigned'
                            when 'loading'    then 'loading'
                            when 'in_transit' then 'in_transit'
                            when 'delivered'  then 'review'
                            else null end;
  if v_ship is not null then
    update shipments set status = v_ship
     where id = new.shipment_id and status not in ('completed', 'cancelled') and status <> v_ship;
  end if;

  if new.status in ('loading', 'in_transit') then
    update trucks  set status = 'on_trip' where id = new.truck_id  and status = 'available';
    update drivers set status = 'on_trip' where id = new.driver_id and status = 'available';
elsif new.status in ('delivered', 'cancelled') then
  update trucks 
  set status = case 
    when exists (
      select 1 from maintenance_records 
      where truck_id = new.truck_id and status = 'in_progress'
    ) then 'maintenance'::truck_status
    else 'available'::truck_status
  end
  where id = new.truck_id and status = 'on_trip';

  update drivers set status = 'available' where id = new.driver_id and status = 'on_trip';
end if;
  if new.status = 'in_transit' and new.departed_at is null then
    update deliveries set departed_at = now() where id = new.id;
  elsif new.status = 'delivered' and new.arrived_at is null then
    update deliveries set arrived_at = now() where id = new.id;
  end if;
  return null;
end $$;


ALTER FUNCTION "public"."sync_from_delivery"() OWNER TO "postgres";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "public"."activity_logs" (
    "id" bigint NOT NULL,
    "user_id" "uuid",
    "action" "text" NOT NULL,
    "entity_type" "text",
    "entity_id" "text",
    "details" "jsonb",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."activity_logs" OWNER TO "postgres";


ALTER TABLE "public"."activity_logs" ALTER COLUMN "id" ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME "public"."activity_logs_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."compliance_documents" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "driver_id" "uuid",
    "truck_id" "uuid",
    "doc_type" "public"."compliance_doc_type" NOT NULL,
    "document_number" "text",
    "issued_date" "date",
    "expiry_date" "date",
    "file_path" "text",
    "review_status" "public"."review_status" DEFAULT 'pending_review'::"public"."review_status" NOT NULL,
    "reviewed_by" "uuid",
    "reviewed_at" timestamp with time zone,
    "remarks" "text",
    "uploaded_by" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "compliance_documents_check" CHECK ((((("driver_id" IS NOT NULL))::integer + (("truck_id" IS NOT NULL))::integer) = 1)),
    CONSTRAINT "compliance_documents_check1" CHECK ((("expiry_date" IS NULL) OR ("issued_date" IS NULL) OR ("expiry_date" >= "issued_date")))
);


ALTER TABLE "public"."compliance_documents" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."deliveries" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "shipment_id" "uuid" NOT NULL,
    "truck_id" "uuid" NOT NULL,
    "driver_id" "uuid" NOT NULL,
    "status" "public"."delivery_status" DEFAULT 'assigned'::"public"."delivery_status" NOT NULL,
    "assigned_by" "uuid",
    "assigned_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "departed_at" timestamp with time zone,
    "arrived_at" timestamp with time zone,
    "odometer_start" numeric(10,1),
    "odometer_end" numeric(10,1),
    "recipient_name" "text",
    "notes" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "deliveries_check" CHECK ((("odometer_end" IS NULL) OR ("odometer_start" IS NULL) OR ("odometer_end" >= "odometer_start")))
);


ALTER TABLE "public"."deliveries" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."drivers" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid",
    "full_name" "text" NOT NULL,
    "contact_number" "text",
    "emergency_contact_name" "text",
    "emergency_contact_number" "text",
    "license_number" "text" NOT NULL,
    "date_hired" "date",
    "status" "public"."driver_status" DEFAULT 'available'::"public"."driver_status" NOT NULL,
    "notes" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."drivers" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."available_drivers" WITH ("security_invoker"='true') AS
 SELECT "id",
    "user_id",
    "full_name",
    "contact_number",
    "emergency_contact_name",
    "emergency_contact_number",
    "license_number",
    "date_hired",
    "status",
    "notes",
    "created_at",
    "updated_at"
   FROM "public"."drivers" "dr"
  WHERE (("status" = 'available'::"public"."driver_status") AND (NOT (EXISTS ( SELECT 1
           FROM "public"."deliveries" "d"
          WHERE (("d"."driver_id" = "dr"."id") AND ("d"."status" = ANY (ARRAY['loading'::"public"."delivery_status", 'in_transit'::"public"."delivery_status"])))))) AND (NOT (EXISTS ( SELECT 1
           FROM "public"."compliance_documents" "c"
          WHERE (("c"."driver_id" = "dr"."id") AND ("c"."expiry_date" < CURRENT_DATE))))));


ALTER VIEW "public"."available_drivers" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."trucks" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "fleet_code" "text",
    "plate_number" "text" NOT NULL,
    "model" "text",
    "model_year" smallint,
    "vehicle_type_id" "uuid",
    "capacity_kg" numeric(10,2),
    "status" "public"."truck_status" DEFAULT 'available'::"public"."truck_status" NOT NULL,
    "assigned_driver_id" "uuid",
    "odometer_km" numeric(10,1) DEFAULT 0 NOT NULL,
    "notes" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "trucks_capacity_kg_check" CHECK (("capacity_kg" > (0)::numeric)),
    CONSTRAINT "trucks_model_year_check" CHECK ((("model_year" >= 1980) AND ("model_year" <= 2100)))
);


ALTER TABLE "public"."trucks" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."available_trucks" WITH ("security_invoker"='true') AS
 SELECT "id",
    "fleet_code",
    "plate_number",
    "model",
    "model_year",
    "vehicle_type_id",
    "capacity_kg",
    "status",
    "assigned_driver_id",
    "odometer_km",
    "notes",
    "created_at",
    "updated_at"
   FROM "public"."trucks" "t"
  WHERE (("status" = 'available'::"public"."truck_status") AND (NOT (EXISTS ( SELECT 1
           FROM "public"."deliveries" "d"
          WHERE (("d"."truck_id" = "t"."id") AND ("d"."status" = ANY (ARRAY['loading'::"public"."delivery_status", 'in_transit'::"public"."delivery_status"])))))) AND (NOT (EXISTS ( SELECT 1
           FROM "public"."compliance_documents" "c"
          WHERE (("c"."truck_id" = "t"."id") AND ("c"."expiry_date" < CURRENT_DATE))))));


ALTER VIEW "public"."available_trucks" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."cargo_categories" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "requires_special_handling" boolean DEFAULT false NOT NULL,
    "is_active" boolean DEFAULT true NOT NULL
);


ALTER TABLE "public"."cargo_categories" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."system_settings" (
    "key" "text" NOT NULL,
    "value" "jsonb" NOT NULL,
    "description" "text",
    "updated_by" "uuid",
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."system_settings" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."compliance_status" WITH ("security_invoker"='true') AS
 SELECT "cd"."id",
    "cd"."driver_id",
    "cd"."truck_id",
    "cd"."doc_type",
    "cd"."document_number",
    "cd"."issued_date",
    "cd"."expiry_date",
    "cd"."file_path",
    "cd"."review_status",
    "cd"."reviewed_by",
    "cd"."reviewed_at",
    "cd"."remarks",
    "cd"."uploaded_by",
    "cd"."created_at",
    "cd"."updated_at",
        CASE
            WHEN ("cd"."expiry_date" IS NULL) THEN 'no_expiry'::"text"
            WHEN ("cd"."expiry_date" < CURRENT_DATE) THEN 'expired'::"text"
            WHEN ("cd"."expiry_date" <= (CURRENT_DATE + COALESCE(( SELECT (("system_settings"."value" #>> '{}'::"text"[]))::integer AS "int4"
               FROM "public"."system_settings"
              WHERE ("system_settings"."key" = 'compliance_threshold_days'::"text")), 30))) THEN 'expiring_soon'::"text"
            ELSE 'valid'::"text"
        END AS "expiry_state",
    "d"."full_name" AS "driver_name",
    "t"."plate_number"
   FROM (("public"."compliance_documents" "cd"
     LEFT JOIN "public"."drivers" "d" ON (("d"."id" = "cd"."driver_id")))
     LEFT JOIN "public"."trucks" "t" ON (("t"."id" = "cd"."truck_id")));


ALTER VIEW "public"."compliance_status" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."customer_no_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."customer_no_seq" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."customers" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "customer_code" "text" DEFAULT ('CUST-'::"text" || "lpad"(("nextval"('"public"."customer_no_seq"'::"regclass"))::"text", 4, '0'::"text")) NOT NULL,
    "user_id" "uuid",
    "name" "text" NOT NULL,
    "contact_person" "text",
    "email" "text",
    "phone" "text",
    "address" "text",
    "city" "text",
    "province" "text" DEFAULT 'Nueva Ecija'::"text",
    "notes" "text",
    "is_active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."customers" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."delivery_documents" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "shipment_id" "uuid" NOT NULL,
    "delivery_id" "uuid",
    "kind" "public"."document_kind" DEFAULT 'proof_of_delivery'::"public"."document_kind" NOT NULL,
    "storage_path" "text" NOT NULL,
    "file_name" "text",
    "mime_type" "text",
    "recipient_name" "text",
    "notes" "text",
    "uploaded_by" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."delivery_documents" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."delivery_helpers" (
    "delivery_id" "uuid" NOT NULL,
    "helper_id" "uuid" NOT NULL
);


ALTER TABLE "public"."delivery_helpers" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."delivery_incidents" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "delivery_id" "uuid" NOT NULL,
    "shipment_id" "uuid" NOT NULL,
    "category" "public"."incident_category" NOT NULL,
    "description" "text",
    "delay_minutes" integer,
    "latitude" numeric(9,6),
    "longitude" numeric(9,6),
    "reported_by" "uuid",
    "resolved_at" timestamp with time zone,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "delivery_incidents_delay_minutes_check" CHECK (("delay_minutes" >= 0))
);


ALTER TABLE "public"."delivery_incidents" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."fuel_logs" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "delivery_id" "uuid",
    "truck_id" "uuid" NOT NULL,
    "driver_id" "uuid",
    "liters" numeric(8,2) NOT NULL,
    "price_per_liter" numeric(8,2),
    "total_cost" numeric(12,2),
    "odometer_km" numeric(10,1),
    "station" "text",
    "receipt_path" "text",
    "logged_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "fuel_logs_liters_check" CHECK (("liters" > (0)::numeric)),
    CONSTRAINT "fuel_logs_price_per_liter_check" CHECK (("price_per_liter" >= (0)::numeric)),
    CONSTRAINT "fuel_logs_total_cost_check" CHECK (("total_cost" >= (0)::numeric))
);


ALTER TABLE "public"."fuel_logs" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."helpers" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "full_name" "text" NOT NULL,
    "contact_number" "text",
    "is_active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."helpers" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."shipment_no_seq"
    START WITH 8000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."shipment_no_seq" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."shipments" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "tracking_number" "text" DEFAULT ((('SHP-NE-'::"text" || "to_char"((CURRENT_DATE)::timestamp with time zone, 'YY'::"text")) || '-'::"text") || "lpad"(("nextval"('"public"."shipment_no_seq"'::"regclass"))::"text", 6, '0'::"text")) NOT NULL,
    "customer_id" "uuid" NOT NULL,
    "billing_customer_id" "uuid",
    "status" "public"."shipment_status" DEFAULT 'booked'::"public"."shipment_status" NOT NULL,
    "booking_source" "text" DEFAULT 'web'::"text" NOT NULL,
    "cargo_category_id" "uuid",
    "cargo_description" "text",
    "weight_kg" numeric(10,2),
    "special_handling" "text",
    "requested_vehicle_type_id" "uuid",
    "pickup_location_id" "uuid",
    "pickup_address" "text" NOT NULL,
    "pickup_lat" numeric(9,6),
    "pickup_lng" numeric(9,6),
    "dropoff_location_id" "uuid",
    "dropoff_address" "text" NOT NULL,
    "dropoff_lat" numeric(9,6),
    "dropoff_lng" numeric(9,6),
    "distance_km" numeric(8,2),
    "scheduled_departure" timestamp with time zone,
    "estimated_arrival" timestamp with time zone,
    "actual_delivery_at" timestamp with time zone,
    "pricing_rate_id" "uuid",
    "quoted_amount" numeric(12,2),
    "override_amount" numeric(12,2),
    "override_reason" "text",
    "final_amount" numeric(12,2) GENERATED ALWAYS AS (COALESCE("override_amount", "quoted_amount")) STORED,
    "driver_notes" "text",
    "cancelled_reason" "text",
    "cancelled_at" timestamp with time zone,
    "created_by" "uuid",
    "confirmed_by" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "shipments_booking_source_check" CHECK (("booking_source" = ANY (ARRAY['web'::"text", 'mobile'::"text", 'phone'::"text"]))),
    CONSTRAINT "shipments_check" CHECK ((("status" <> 'cancelled'::"public"."shipment_status") OR ("cancelled_at" IS NOT NULL))),
    CONSTRAINT "shipments_distance_km_check" CHECK (("distance_km" >= (0)::numeric)),
    CONSTRAINT "shipments_override_amount_check" CHECK (("override_amount" >= (0)::numeric)),
    CONSTRAINT "shipments_quoted_amount_check" CHECK (("quoted_amount" >= (0)::numeric)),
    CONSTRAINT "shipments_weight_kg_check" CHECK (("weight_kg" > (0)::numeric))
);


ALTER TABLE "public"."shipments" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."tracking_logs" (
    "id" bigint NOT NULL,
    "delivery_id" "uuid" NOT NULL,
    "truck_id" "uuid",
    "driver_id" "uuid",
    "latitude" numeric(9,6) NOT NULL,
    "longitude" numeric(9,6) NOT NULL,
    "speed_kph" numeric(5,1),
    "heading_deg" numeric(5,1),
    "accuracy_m" numeric(7,1),
    "signal" "text" DEFAULT 'good'::"text" NOT NULL,
    "is_buffered" boolean DEFAULT false NOT NULL,
    "recorded_at" timestamp with time zone NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "tracking_logs_heading_deg_check" CHECK ((("heading_deg" >= (0)::numeric) AND ("heading_deg" <= (360)::numeric))),
    CONSTRAINT "tracking_logs_latitude_check" CHECK ((("latitude" >= ('-90'::integer)::numeric) AND ("latitude" <= (90)::numeric))),
    CONSTRAINT "tracking_logs_longitude_check" CHECK ((("longitude" >= ('-180'::integer)::numeric) AND ("longitude" <= (180)::numeric))),
    CONSTRAINT "tracking_logs_signal_check" CHECK (("signal" = ANY (ARRAY['good'::"text", 'weak'::"text", 'none'::"text"]))),
    CONSTRAINT "tracking_logs_speed_kph_check" CHECK (("speed_kph" >= (0)::numeric))
);


ALTER TABLE "public"."tracking_logs" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."latest_truck_positions" WITH ("security_invoker"='true') AS
 SELECT DISTINCT ON ("tl"."delivery_id") "tl"."delivery_id",
    "d"."shipment_id",
    "s"."tracking_number",
    "tl"."truck_id",
    "tl"."latitude",
    "tl"."longitude",
    "tl"."speed_kph",
    "tl"."signal",
    "tl"."recorded_at"
   FROM (("public"."tracking_logs" "tl"
     JOIN "public"."deliveries" "d" ON (("d"."id" = "tl"."delivery_id")))
     JOIN "public"."shipments" "s" ON (("s"."id" = "d"."shipment_id")))
  WHERE ("d"."status" = ANY (ARRAY['loading'::"public"."delivery_status", 'in_transit'::"public"."delivery_status"]))
  ORDER BY "tl"."delivery_id", "tl"."recorded_at" DESC;


ALTER VIEW "public"."latest_truck_positions" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."location_vehicle_types" (
    "location_id" "uuid" NOT NULL,
    "vehicle_type_id" "uuid" NOT NULL
);


ALTER TABLE "public"."location_vehicle_types" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."locations" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "sublabel" "text",
    "address" "text",
    "city" "text",
    "province" "text" DEFAULT 'Nueva Ecija'::"text",
    "latitude" numeric(9,6),
    "longitude" numeric(9,6),
    "kind" "public"."location_kind" DEFAULT 'other'::"public"."location_kind" NOT NULL,
    "is_active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "geog" "extensions"."geography"(Point,4326) GENERATED ALWAYS AS (("extensions"."st_setsrid"("extensions"."st_makepoint"(("longitude")::double precision, ("latitude")::double precision), 4326))::"extensions"."geography") STORED,
    CONSTRAINT "locations_latitude_check" CHECK ((("latitude" >= ('-90'::integer)::numeric) AND ("latitude" <= (90)::numeric))),
    CONSTRAINT "locations_longitude_check" CHECK ((("longitude" >= ('-180'::integer)::numeric) AND ("longitude" <= (180)::numeric)))
);


ALTER TABLE "public"."locations" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."maintenance_records" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "truck_id" "uuid" NOT NULL,
    "kind" "public"."maintenance_kind" DEFAULT 'preventive'::"public"."maintenance_kind" NOT NULL,
    "status" "public"."maintenance_status" DEFAULT 'scheduled'::"public"."maintenance_status" NOT NULL,
    "description" "text",
    "scheduled_date" "date" NOT NULL,
    "completed_date" "date",
    "odometer_km" numeric(10,1),
    "cost" numeric(12,2),
    "vendor" "text",
    "next_due_date" "date",
    "next_due_km" numeric(10,1),
    "created_by" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "maintenance_records_cost_check" CHECK (("cost" >= (0)::numeric))
);


ALTER TABLE "public"."maintenance_records" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."messages" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "shipment_id" "uuid",
    "sender_id" "uuid" NOT NULL,
    "recipient_id" "uuid" NOT NULL,
    "body" "text" NOT NULL,
    "read_at" timestamp with time zone,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "messages_body_check" CHECK (("length"("body") > 0))
);


ALTER TABLE "public"."messages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."notification_preferences" (
    "user_id" "uuid" NOT NULL,
    "type" "public"."notification_type" NOT NULL,
    "channel" "text" DEFAULT 'in_app'::"text" NOT NULL,
    "enabled" boolean DEFAULT true NOT NULL,
    CONSTRAINT "notification_preferences_channel_check" CHECK (("channel" = ANY (ARRAY['in_app'::"text", 'email'::"text", 'sms'::"text"])))
);


ALTER TABLE "public"."notification_preferences" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."notifications" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid" NOT NULL,
    "type" "public"."notification_type" NOT NULL,
    "title" "text" NOT NULL,
    "body" "text",
    "shipment_id" "uuid",
    "read_at" timestamp with time zone,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."notifications" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."payments" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "shipment_id" "uuid" NOT NULL,
    "amount" numeric(12,2) NOT NULL,
    "method" "public"."payment_method" DEFAULT 'cash'::"public"."payment_method" NOT NULL,
    "reference_number" "text",
    "proof_path" "text",
    "status" "public"."payment_status" DEFAULT 'pending'::"public"."payment_status" NOT NULL,
    "paid_at" timestamp with time zone,
    "verified_by" "uuid",
    "verified_at" timestamp with time zone,
    "notes" "text",
    "created_by" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "payments_amount_check" CHECK (("amount" > (0)::numeric)),
    CONSTRAINT "payments_check" CHECK ((("status" <> 'verified'::"public"."payment_status") OR (("verified_by" IS NOT NULL) AND ("verified_at" IS NOT NULL))))
);


ALTER TABLE "public"."payments" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."pricing_rates" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "vehicle_type_id" "uuid",
    "base_rate" numeric(12,2) DEFAULT 0 NOT NULL,
    "per_km_rate" numeric(12,2) DEFAULT 0 NOT NULL,
    "per_kg_rate" numeric(12,2) DEFAULT 0 NOT NULL,
    "min_charge" numeric(12,2) DEFAULT 0 NOT NULL,
    "is_active" boolean DEFAULT true NOT NULL,
    "effective_from" "date" DEFAULT CURRENT_DATE NOT NULL,
    "effective_to" "date",
    "created_by" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "pricing_rates_base_rate_check" CHECK (("base_rate" >= (0)::numeric)),
    CONSTRAINT "pricing_rates_check" CHECK ((("effective_to" IS NULL) OR ("effective_to" >= "effective_from"))),
    CONSTRAINT "pricing_rates_min_charge_check" CHECK (("min_charge" >= (0)::numeric)),
    CONSTRAINT "pricing_rates_per_kg_rate_check" CHECK (("per_kg_rate" >= (0)::numeric)),
    CONSTRAINT "pricing_rates_per_km_rate_check" CHECK (("per_km_rate" >= (0)::numeric))
);


ALTER TABLE "public"."pricing_rates" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."shipment_overview" WITH ("security_invoker"='true') AS
 SELECT "s"."id",
    "s"."tracking_number",
    "s"."status",
    "s"."cargo_description",
    "cc"."name" AS "cargo_category",
    "s"."pickup_address",
    "s"."dropoff_address",
    "s"."scheduled_departure",
    "s"."estimated_arrival",
    "s"."final_amount",
    "c"."name" AS "customer_name",
    "dr"."full_name" AS "driver_name",
    "tr"."fleet_code",
    "tr"."plate_number",
    COALESCE("p"."paid", (0)::numeric) AS "amount_paid",
    (COALESCE("s"."final_amount", (0)::numeric) - COALESCE("p"."paid", (0)::numeric)) AS "balance",
        CASE
            WHEN (COALESCE("p"."paid", (0)::numeric) <= (0)::numeric) THEN 'unpaid'::"text"
            WHEN (COALESCE("p"."paid", (0)::numeric) < COALESCE("s"."final_amount", (0)::numeric)) THEN 'partial'::"text"
            ELSE 'paid'::"text"
        END AS "payment_state"
   FROM (((((("public"."shipments" "s"
     JOIN "public"."customers" "c" ON (("c"."id" = "s"."customer_id")))
     LEFT JOIN "public"."cargo_categories" "cc" ON (("cc"."id" = "s"."cargo_category_id")))
     LEFT JOIN "public"."deliveries" "d" ON ((("d"."shipment_id" = "s"."id") AND ("d"."status" <> 'cancelled'::"public"."delivery_status"))))
     LEFT JOIN "public"."drivers" "dr" ON (("dr"."id" = "d"."driver_id")))
     LEFT JOIN "public"."trucks" "tr" ON (("tr"."id" = "d"."truck_id")))
     LEFT JOIN LATERAL ( SELECT "sum"("payments"."amount") AS "paid"
           FROM "public"."payments"
          WHERE (("payments"."shipment_id" = "s"."id") AND ("payments"."status" = 'verified'::"public"."payment_status"))) "p" ON (true));


ALTER VIEW "public"."shipment_overview" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."shipment_status_history" (
    "id" bigint NOT NULL,
    "shipment_id" "uuid" NOT NULL,
    "from_status" "public"."shipment_status",
    "to_status" "public"."shipment_status" NOT NULL,
    "changed_by" "uuid",
    "note" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."shipment_status_history" OWNER TO "postgres";


ALTER TABLE "public"."shipment_status_history" ALTER COLUMN "id" ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME "public"."shipment_status_history_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."toll_gate_rates" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "gate_name" "text" NOT NULL,
    "highway" "text" NOT NULL,
    "vehicle_class" "text" DEFAULT 'Class 2'::"text" NOT NULL,
    "fee" numeric(12,2) NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "toll_gate_rates_fee_check" CHECK (("fee" >= (0)::numeric))
);


ALTER TABLE "public"."toll_gate_rates" OWNER TO "postgres";


ALTER TABLE "public"."tracking_logs" ALTER COLUMN "id" ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME "public"."tracking_logs_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."truck_cargo_categories" (
    "truck_id" "uuid" NOT NULL,
    "cargo_category_id" "uuid" NOT NULL
);


ALTER TABLE "public"."truck_cargo_categories" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."users" (
    "id" "uuid" NOT NULL,
    "email" "text" NOT NULL,
    "full_name" "text" DEFAULT ''::"text" NOT NULL,
    "phone" "text",
    "role" "public"."user_role" DEFAULT 'customer'::"public"."user_role" NOT NULL,
    "is_active" boolean DEFAULT true NOT NULL,
    "avatar_url" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."users" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."vehicle_types" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "default_capacity_kg" numeric(10,2),
    "is_active" boolean DEFAULT true NOT NULL,
    CONSTRAINT "vehicle_types_default_capacity_kg_check" CHECK (("default_capacity_kg" > (0)::numeric))
);


ALTER TABLE "public"."vehicle_types" OWNER TO "postgres";


ALTER TABLE ONLY "public"."activity_logs"
    ADD CONSTRAINT "activity_logs_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."cargo_categories"
    ADD CONSTRAINT "cargo_categories_name_key" UNIQUE ("name");



ALTER TABLE ONLY "public"."cargo_categories"
    ADD CONSTRAINT "cargo_categories_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."compliance_documents"
    ADD CONSTRAINT "compliance_documents_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."customers"
    ADD CONSTRAINT "customers_customer_code_key" UNIQUE ("customer_code");



ALTER TABLE ONLY "public"."customers"
    ADD CONSTRAINT "customers_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."customers"
    ADD CONSTRAINT "customers_user_id_key" UNIQUE ("user_id");



ALTER TABLE ONLY "public"."deliveries"
    ADD CONSTRAINT "deliveries_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."delivery_documents"
    ADD CONSTRAINT "delivery_documents_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."delivery_helpers"
    ADD CONSTRAINT "delivery_helpers_pkey" PRIMARY KEY ("delivery_id", "helper_id");



ALTER TABLE ONLY "public"."delivery_incidents"
    ADD CONSTRAINT "delivery_incidents_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."drivers"
    ADD CONSTRAINT "drivers_license_number_key" UNIQUE ("license_number");



ALTER TABLE ONLY "public"."drivers"
    ADD CONSTRAINT "drivers_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."drivers"
    ADD CONSTRAINT "drivers_user_id_key" UNIQUE ("user_id");



ALTER TABLE ONLY "public"."fuel_logs"
    ADD CONSTRAINT "fuel_logs_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."helpers"
    ADD CONSTRAINT "helpers_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."location_vehicle_types"
    ADD CONSTRAINT "location_vehicle_types_pkey" PRIMARY KEY ("location_id", "vehicle_type_id");



ALTER TABLE ONLY "public"."locations"
    ADD CONSTRAINT "locations_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."maintenance_records"
    ADD CONSTRAINT "maintenance_records_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."notification_preferences"
    ADD CONSTRAINT "notification_preferences_pkey" PRIMARY KEY ("user_id", "type", "channel");



ALTER TABLE ONLY "public"."notifications"
    ADD CONSTRAINT "notifications_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."payments"
    ADD CONSTRAINT "payments_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."pricing_rates"
    ADD CONSTRAINT "pricing_rates_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."shipment_status_history"
    ADD CONSTRAINT "shipment_status_history_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."shipments"
    ADD CONSTRAINT "shipments_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."shipments"
    ADD CONSTRAINT "shipments_tracking_number_key" UNIQUE ("tracking_number");



ALTER TABLE ONLY "public"."system_settings"
    ADD CONSTRAINT "system_settings_pkey" PRIMARY KEY ("key");



ALTER TABLE ONLY "public"."toll_gate_rates"
    ADD CONSTRAINT "toll_gate_rates_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."tracking_logs"
    ADD CONSTRAINT "tracking_logs_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."truck_cargo_categories"
    ADD CONSTRAINT "truck_cargo_categories_pkey" PRIMARY KEY ("truck_id", "cargo_category_id");



ALTER TABLE ONLY "public"."trucks"
    ADD CONSTRAINT "trucks_fleet_code_key" UNIQUE ("fleet_code");



ALTER TABLE ONLY "public"."trucks"
    ADD CONSTRAINT "trucks_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."trucks"
    ADD CONSTRAINT "trucks_plate_number_key" UNIQUE ("plate_number");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_email_key" UNIQUE ("email");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."vehicle_types"
    ADD CONSTRAINT "vehicle_types_name_key" UNIQUE ("name");



ALTER TABLE ONLY "public"."vehicle_types"
    ADD CONSTRAINT "vehicle_types_pkey" PRIMARY KEY ("id");



CREATE INDEX "activity_logs_created_at_idx" ON "public"."activity_logs" USING "btree" ("created_at" DESC);



CREATE INDEX "activity_logs_entity_type_entity_id_idx" ON "public"."activity_logs" USING "btree" ("entity_type", "entity_id");



CREATE INDEX "activity_logs_user_id_idx" ON "public"."activity_logs" USING "btree" ("user_id");



CREATE INDEX "compliance_documents_driver_id_idx" ON "public"."compliance_documents" USING "btree" ("driver_id");



CREATE INDEX "compliance_documents_expiry_date_idx" ON "public"."compliance_documents" USING "btree" ("expiry_date");



CREATE INDEX "compliance_documents_truck_id_idx" ON "public"."compliance_documents" USING "btree" ("truck_id");



CREATE INDEX "customers_name_idx" ON "public"."customers" USING "btree" ("name");



CREATE INDEX "deliveries_driver_id_status_idx" ON "public"."deliveries" USING "btree" ("driver_id", "status");



CREATE UNIQUE INDEX "deliveries_one_active_per_shipment" ON "public"."deliveries" USING "btree" ("shipment_id") WHERE ("status" <> 'cancelled'::"public"."delivery_status");



CREATE INDEX "deliveries_truck_id_status_idx" ON "public"."deliveries" USING "btree" ("truck_id", "status");



CREATE INDEX "delivery_documents_shipment_id_idx" ON "public"."delivery_documents" USING "btree" ("shipment_id");



CREATE INDEX "delivery_incidents_shipment_id_idx" ON "public"."delivery_incidents" USING "btree" ("shipment_id");



CREATE INDEX "fuel_logs_delivery_id_idx" ON "public"."fuel_logs" USING "btree" ("delivery_id");



CREATE INDEX "fuel_logs_truck_id_logged_at_idx" ON "public"."fuel_logs" USING "btree" ("truck_id", "logged_at" DESC);



CREATE INDEX "locations_geog_idx" ON "public"."locations" USING "gist" ("geog");



CREATE INDEX "maintenance_records_truck_id_scheduled_date_idx" ON "public"."maintenance_records" USING "btree" ("truck_id", "scheduled_date");



CREATE INDEX "messages_recipient_id_created_at_idx" ON "public"."messages" USING "btree" ("recipient_id", "created_at" DESC);



CREATE INDEX "messages_shipment_id_idx" ON "public"."messages" USING "btree" ("shipment_id");



CREATE INDEX "notifications_user_id_created_at_idx" ON "public"."notifications" USING "btree" ("user_id", "created_at" DESC) WHERE ("read_at" IS NULL);



CREATE INDEX "payments_shipment_id_idx" ON "public"."payments" USING "btree" ("shipment_id");



CREATE INDEX "payments_status_idx" ON "public"."payments" USING "btree" ("status");



CREATE INDEX "shipment_status_history_shipment_id_created_at_idx" ON "public"."shipment_status_history" USING "btree" ("shipment_id", "created_at");



CREATE INDEX "shipments_created_at_idx" ON "public"."shipments" USING "btree" ("created_at" DESC);



CREATE INDEX "shipments_customer_id_idx" ON "public"."shipments" USING "btree" ("customer_id");



CREATE INDEX "shipments_scheduled_departure_idx" ON "public"."shipments" USING "btree" ("scheduled_departure");



CREATE INDEX "shipments_status_idx" ON "public"."shipments" USING "btree" ("status");



CREATE INDEX "tracking_logs_delivery_id_recorded_at_idx" ON "public"."tracking_logs" USING "btree" ("delivery_id", "recorded_at" DESC);



CREATE INDEX "tracking_logs_truck_id_recorded_at_idx" ON "public"."tracking_logs" USING "btree" ("truck_id", "recorded_at" DESC);



CREATE INDEX "tracking_logs_weak_signal" ON "public"."tracking_logs" USING "btree" ("recorded_at") WHERE (("signal" <> 'good'::"text") OR "is_buffered");



CREATE INDEX "trucks_status_idx" ON "public"."trucks" USING "btree" ("status");



CREATE OR REPLACE TRIGGER "trg_compliance_documents_audit" AFTER INSERT OR DELETE OR UPDATE ON "public"."compliance_documents" FOR EACH ROW EXECUTE FUNCTION "public"."log_activity"();



CREATE OR REPLACE TRIGGER "trg_compliance_documents_updated" BEFORE UPDATE ON "public"."compliance_documents" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_customers_audit" AFTER INSERT OR DELETE OR UPDATE ON "public"."customers" FOR EACH ROW EXECUTE FUNCTION "public"."log_activity"();



CREATE OR REPLACE TRIGGER "trg_customers_updated" BEFORE UPDATE ON "public"."customers" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_deliveries_audit" AFTER INSERT OR DELETE OR UPDATE ON "public"."deliveries" FOR EACH ROW EXECUTE FUNCTION "public"."log_activity"();



CREATE OR REPLACE TRIGGER "trg_deliveries_sync" AFTER INSERT OR UPDATE OF "status" ON "public"."deliveries" FOR EACH ROW EXECUTE FUNCTION "public"."sync_from_delivery"();



CREATE OR REPLACE TRIGGER "trg_deliveries_timestamps" BEFORE INSERT OR UPDATE OF "status" ON "public"."deliveries" FOR EACH ROW EXECUTE FUNCTION "public"."stamp_delivery_timestamps"();



CREATE OR REPLACE TRIGGER "trg_deliveries_updated" BEFORE UPDATE ON "public"."deliveries" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_drivers_audit" AFTER INSERT OR DELETE OR UPDATE ON "public"."drivers" FOR EACH ROW EXECUTE FUNCTION "public"."log_activity"();



CREATE OR REPLACE TRIGGER "trg_drivers_updated" BEFORE UPDATE ON "public"."drivers" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_helpers_updated" BEFORE UPDATE ON "public"."helpers" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_incident_created" AFTER INSERT ON "public"."delivery_incidents" FOR EACH ROW EXECUTE FUNCTION "public"."on_incident_created"();



CREATE OR REPLACE TRIGGER "trg_maintenance_records_updated" BEFORE UPDATE ON "public"."maintenance_records" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_payments_audit" AFTER INSERT OR DELETE OR UPDATE ON "public"."payments" FOR EACH ROW EXECUTE FUNCTION "public"."log_activity"();



CREATE OR REPLACE TRIGGER "trg_payments_updated" BEFORE UPDATE ON "public"."payments" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_pricing_rates_audit" AFTER INSERT OR DELETE OR UPDATE ON "public"."pricing_rates" FOR EACH ROW EXECUTE FUNCTION "public"."log_activity"();



CREATE OR REPLACE TRIGGER "trg_pricing_rates_updated" BEFORE UPDATE ON "public"."pricing_rates" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_shipments_audit" AFTER INSERT OR DELETE OR UPDATE ON "public"."shipments" FOR EACH ROW EXECUTE FUNCTION "public"."log_activity"();



CREATE OR REPLACE TRIGGER "trg_shipments_dates" BEFORE UPDATE OF "status" ON "public"."shipments" FOR EACH ROW EXECUTE FUNCTION "public"."stamp_shipment_dates"();



CREATE OR REPLACE TRIGGER "trg_shipments_status_ins" AFTER INSERT ON "public"."shipments" FOR EACH ROW EXECUTE FUNCTION "public"."on_shipment_status_change"();



CREATE OR REPLACE TRIGGER "trg_shipments_status_upd" AFTER UPDATE OF "status" ON "public"."shipments" FOR EACH ROW EXECUTE FUNCTION "public"."on_shipment_status_change"();



CREATE OR REPLACE TRIGGER "trg_shipments_updated" BEFORE UPDATE ON "public"."shipments" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_system_settings_audit" AFTER INSERT OR DELETE OR UPDATE ON "public"."system_settings" FOR EACH ROW EXECUTE FUNCTION "public"."log_activity"();



CREATE OR REPLACE TRIGGER "trg_system_settings_updated" BEFORE UPDATE ON "public"."system_settings" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_trucks_audit" AFTER INSERT OR DELETE OR UPDATE ON "public"."trucks" FOR EACH ROW EXECUTE FUNCTION "public"."log_activity"();



CREATE OR REPLACE TRIGGER "trg_trucks_updated" BEFORE UPDATE ON "public"."trucks" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_users_audit" AFTER INSERT OR DELETE OR UPDATE ON "public"."users" FOR EACH ROW EXECUTE FUNCTION "public"."log_activity"();



CREATE OR REPLACE TRIGGER "trg_users_create_customer" AFTER INSERT ON "public"."users" FOR EACH ROW EXECUTE FUNCTION "public"."create_customer_for_user"();



CREATE OR REPLACE TRIGGER "trg_users_protect" BEFORE UPDATE ON "public"."users" FOR EACH ROW EXECUTE FUNCTION "public"."protect_user_privileges"();



CREATE OR REPLACE TRIGGER "trg_users_updated" BEFORE UPDATE ON "public"."users" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



ALTER TABLE ONLY "public"."compliance_documents"
    ADD CONSTRAINT "compliance_documents_driver_id_fkey" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."compliance_documents"
    ADD CONSTRAINT "compliance_documents_reviewed_by_fkey" FOREIGN KEY ("reviewed_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."compliance_documents"
    ADD CONSTRAINT "compliance_documents_truck_id_fkey" FOREIGN KEY ("truck_id") REFERENCES "public"."trucks"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."compliance_documents"
    ADD CONSTRAINT "compliance_documents_uploaded_by_fkey" FOREIGN KEY ("uploaded_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."customers"
    ADD CONSTRAINT "customers_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."deliveries"
    ADD CONSTRAINT "deliveries_assigned_by_fkey" FOREIGN KEY ("assigned_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."deliveries"
    ADD CONSTRAINT "deliveries_driver_id_fkey" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id");



ALTER TABLE ONLY "public"."deliveries"
    ADD CONSTRAINT "deliveries_shipment_id_fkey" FOREIGN KEY ("shipment_id") REFERENCES "public"."shipments"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."deliveries"
    ADD CONSTRAINT "deliveries_truck_id_fkey" FOREIGN KEY ("truck_id") REFERENCES "public"."trucks"("id");



ALTER TABLE ONLY "public"."delivery_documents"
    ADD CONSTRAINT "delivery_documents_delivery_id_fkey" FOREIGN KEY ("delivery_id") REFERENCES "public"."deliveries"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."delivery_documents"
    ADD CONSTRAINT "delivery_documents_shipment_id_fkey" FOREIGN KEY ("shipment_id") REFERENCES "public"."shipments"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."delivery_documents"
    ADD CONSTRAINT "delivery_documents_uploaded_by_fkey" FOREIGN KEY ("uploaded_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."delivery_helpers"
    ADD CONSTRAINT "delivery_helpers_delivery_id_fkey" FOREIGN KEY ("delivery_id") REFERENCES "public"."deliveries"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."delivery_helpers"
    ADD CONSTRAINT "delivery_helpers_helper_id_fkey" FOREIGN KEY ("helper_id") REFERENCES "public"."helpers"("id");



ALTER TABLE ONLY "public"."delivery_incidents"
    ADD CONSTRAINT "delivery_incidents_delivery_id_fkey" FOREIGN KEY ("delivery_id") REFERENCES "public"."deliveries"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."delivery_incidents"
    ADD CONSTRAINT "delivery_incidents_reported_by_fkey" FOREIGN KEY ("reported_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."delivery_incidents"
    ADD CONSTRAINT "delivery_incidents_shipment_id_fkey" FOREIGN KEY ("shipment_id") REFERENCES "public"."shipments"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."drivers"
    ADD CONSTRAINT "drivers_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."fuel_logs"
    ADD CONSTRAINT "fuel_logs_delivery_id_fkey" FOREIGN KEY ("delivery_id") REFERENCES "public"."deliveries"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."fuel_logs"
    ADD CONSTRAINT "fuel_logs_driver_id_fkey" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id");



ALTER TABLE ONLY "public"."fuel_logs"
    ADD CONSTRAINT "fuel_logs_truck_id_fkey" FOREIGN KEY ("truck_id") REFERENCES "public"."trucks"("id");



ALTER TABLE ONLY "public"."location_vehicle_types"
    ADD CONSTRAINT "location_vehicle_types_location_id_fkey" FOREIGN KEY ("location_id") REFERENCES "public"."locations"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."location_vehicle_types"
    ADD CONSTRAINT "location_vehicle_types_vehicle_type_id_fkey" FOREIGN KEY ("vehicle_type_id") REFERENCES "public"."vehicle_types"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."maintenance_records"
    ADD CONSTRAINT "maintenance_records_created_by_fkey" FOREIGN KEY ("created_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."maintenance_records"
    ADD CONSTRAINT "maintenance_records_truck_id_fkey" FOREIGN KEY ("truck_id") REFERENCES "public"."trucks"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_recipient_id_fkey" FOREIGN KEY ("recipient_id") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_sender_id_fkey" FOREIGN KEY ("sender_id") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_shipment_id_fkey" FOREIGN KEY ("shipment_id") REFERENCES "public"."shipments"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."notification_preferences"
    ADD CONSTRAINT "notification_preferences_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."notifications"
    ADD CONSTRAINT "notifications_shipment_id_fkey" FOREIGN KEY ("shipment_id") REFERENCES "public"."shipments"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."notifications"
    ADD CONSTRAINT "notifications_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."payments"
    ADD CONSTRAINT "payments_created_by_fkey" FOREIGN KEY ("created_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."payments"
    ADD CONSTRAINT "payments_shipment_id_fkey" FOREIGN KEY ("shipment_id") REFERENCES "public"."shipments"("id");



ALTER TABLE ONLY "public"."payments"
    ADD CONSTRAINT "payments_verified_by_fkey" FOREIGN KEY ("verified_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."pricing_rates"
    ADD CONSTRAINT "pricing_rates_created_by_fkey" FOREIGN KEY ("created_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."pricing_rates"
    ADD CONSTRAINT "pricing_rates_vehicle_type_id_fkey" FOREIGN KEY ("vehicle_type_id") REFERENCES "public"."vehicle_types"("id");



ALTER TABLE ONLY "public"."shipment_status_history"
    ADD CONSTRAINT "shipment_status_history_changed_by_fkey" FOREIGN KEY ("changed_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."shipment_status_history"
    ADD CONSTRAINT "shipment_status_history_shipment_id_fkey" FOREIGN KEY ("shipment_id") REFERENCES "public"."shipments"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."shipments"
    ADD CONSTRAINT "shipments_billing_customer_id_fkey" FOREIGN KEY ("billing_customer_id") REFERENCES "public"."customers"("id");



ALTER TABLE ONLY "public"."shipments"
    ADD CONSTRAINT "shipments_cargo_category_id_fkey" FOREIGN KEY ("cargo_category_id") REFERENCES "public"."cargo_categories"("id");



ALTER TABLE ONLY "public"."shipments"
    ADD CONSTRAINT "shipments_confirmed_by_fkey" FOREIGN KEY ("confirmed_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."shipments"
    ADD CONSTRAINT "shipments_created_by_fkey" FOREIGN KEY ("created_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."shipments"
    ADD CONSTRAINT "shipments_customer_id_fkey" FOREIGN KEY ("customer_id") REFERENCES "public"."customers"("id");



ALTER TABLE ONLY "public"."shipments"
    ADD CONSTRAINT "shipments_dropoff_location_id_fkey" FOREIGN KEY ("dropoff_location_id") REFERENCES "public"."locations"("id");



ALTER TABLE ONLY "public"."shipments"
    ADD CONSTRAINT "shipments_pickup_location_id_fkey" FOREIGN KEY ("pickup_location_id") REFERENCES "public"."locations"("id");



ALTER TABLE ONLY "public"."shipments"
    ADD CONSTRAINT "shipments_pricing_rate_id_fkey" FOREIGN KEY ("pricing_rate_id") REFERENCES "public"."pricing_rates"("id");



ALTER TABLE ONLY "public"."shipments"
    ADD CONSTRAINT "shipments_requested_vehicle_type_id_fkey" FOREIGN KEY ("requested_vehicle_type_id") REFERENCES "public"."vehicle_types"("id");



ALTER TABLE ONLY "public"."system_settings"
    ADD CONSTRAINT "system_settings_updated_by_fkey" FOREIGN KEY ("updated_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."tracking_logs"
    ADD CONSTRAINT "tracking_logs_delivery_id_fkey" FOREIGN KEY ("delivery_id") REFERENCES "public"."deliveries"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."tracking_logs"
    ADD CONSTRAINT "tracking_logs_driver_id_fkey" FOREIGN KEY ("driver_id") REFERENCES "public"."drivers"("id");



ALTER TABLE ONLY "public"."tracking_logs"
    ADD CONSTRAINT "tracking_logs_truck_id_fkey" FOREIGN KEY ("truck_id") REFERENCES "public"."trucks"("id");



ALTER TABLE ONLY "public"."truck_cargo_categories"
    ADD CONSTRAINT "truck_cargo_categories_cargo_category_id_fkey" FOREIGN KEY ("cargo_category_id") REFERENCES "public"."cargo_categories"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."truck_cargo_categories"
    ADD CONSTRAINT "truck_cargo_categories_truck_id_fkey" FOREIGN KEY ("truck_id") REFERENCES "public"."trucks"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."trucks"
    ADD CONSTRAINT "trucks_assigned_driver_id_fkey" FOREIGN KEY ("assigned_driver_id") REFERENCES "public"."drivers"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."trucks"
    ADD CONSTRAINT "trucks_vehicle_type_id_fkey" FOREIGN KEY ("vehicle_type_id") REFERENCES "public"."vehicle_types"("id");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_id_fkey" FOREIGN KEY ("id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;



CREATE POLICY "Admins can view all profiles" ON "public"."users" FOR SELECT TO "authenticated" USING ("public"."is_admin"());



CREATE POLICY "Authenticated users can view toll gates" ON "public"."toll_gate_rates" FOR SELECT TO "authenticated" USING (true);



CREATE POLICY "Staff read shipments" ON "public"."shipments" FOR SELECT TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "Staff read trucks" ON "public"."trucks" FOR SELECT TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "Users can view own profile" ON "public"."users" FOR SELECT TO "authenticated" USING (("auth"."uid"() = "id"));



CREATE POLICY "Users read own or staff read all" ON "public"."users" FOR SELECT TO "authenticated" USING ((("auth"."uid"() = "id") OR ("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))));



CREATE POLICY "Users update own or admin update all" ON "public"."users" FOR UPDATE TO "authenticated" USING ((("auth"."uid"() = "id") OR ("public"."get_user_role"() = 'admin'::"text"))) WITH CHECK ((("auth"."uid"() = "id") OR ("public"."get_user_role"() = 'admin'::"text")));



ALTER TABLE "public"."activity_logs" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "admin manage users" ON "public"."users" TO "authenticated" USING (("public"."get_user_role"() = 'admin'::"text")) WITH CHECK (("public"."get_user_role"() = 'admin'::"text"));



CREATE POLICY "admin manages rates" ON "public"."pricing_rates" TO "authenticated" USING (("public"."get_user_role"() = 'admin'::"text")) WITH CHECK (("public"."get_user_role"() = 'admin'::"text"));



CREATE POLICY "admin manages toll gates" ON "public"."toll_gate_rates" TO "authenticated" USING (("public"."get_user_role"() = 'admin'::"text")) WITH CHECK (("public"."get_user_role"() = 'admin'::"text"));



CREATE POLICY "admin reads activity" ON "public"."activity_logs" FOR SELECT TO "authenticated" USING (("public"."get_user_role"() = 'admin'::"text"));



CREATE POLICY "admin writes settings" ON "public"."system_settings" TO "authenticated" USING (("public"."get_user_role"() = 'admin'::"text")) WITH CHECK (("public"."get_user_role"() = 'admin'::"text"));



ALTER TABLE "public"."cargo_categories" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."compliance_documents" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "customer books own shipment" ON "public"."shipments" FOR INSERT TO "authenticated" WITH CHECK ((("customer_id" = "public"."my_customer_id"()) AND ("status" = 'pending'::"public"."shipment_status") AND ("booking_source" = 'mobile'::"text")));



CREATE POLICY "customer reads own POD" ON "public"."delivery_documents" FOR SELECT TO "authenticated" USING ((("kind" = 'proof_of_delivery'::"public"."document_kind") AND (EXISTS ( SELECT 1
   FROM "public"."shipments" "s"
  WHERE (("s"."id" = "delivery_documents"."shipment_id") AND ("s"."customer_id" = "public"."my_customer_id"()))))));



CREATE POLICY "customer reads own deliveries" ON "public"."deliveries" FOR SELECT TO "authenticated" USING (("shipment_id" IN ( SELECT "public"."my_customer_shipment_ids"() AS "my_customer_shipment_ids")));



CREATE POLICY "customer reads own history" ON "public"."shipment_status_history" FOR SELECT TO "authenticated" USING ((EXISTS ( SELECT 1
   FROM "public"."shipments" "s"
  WHERE (("s"."id" = "shipment_status_history"."shipment_id") AND ("s"."customer_id" = "public"."my_customer_id"())))));



CREATE POLICY "customer reads own payments" ON "public"."payments" FOR SELECT TO "authenticated" USING ((EXISTS ( SELECT 1
   FROM "public"."shipments" "s"
  WHERE (("s"."id" = "payments"."shipment_id") AND (("s"."customer_id" = "public"."my_customer_id"()) OR ("s"."billing_customer_id" = "public"."my_customer_id"()))))));



CREATE POLICY "customer reads own shipments" ON "public"."shipments" FOR SELECT TO "authenticated" USING ((("customer_id" = "public"."my_customer_id"()) OR ("billing_customer_id" = "public"."my_customer_id"())));



CREATE POLICY "customer reads self" ON "public"."customers" FOR SELECT TO "authenticated" USING (("user_id" = "auth"."uid"()));



CREATE POLICY "customer sees live tracking" ON "public"."tracking_logs" FOR SELECT TO "authenticated" USING ((EXISTS ( SELECT 1
   FROM ("public"."deliveries" "d"
     JOIN "public"."shipments" "s" ON (("s"."id" = "d"."shipment_id")))
  WHERE (("d"."id" = "tracking_logs"."delivery_id") AND ("s"."customer_id" = "public"."my_customer_id"()) AND ("s"."status" = ANY (ARRAY['loading'::"public"."shipment_status", 'in_transit'::"public"."shipment_status", 'delayed'::"public"."shipment_status"]))))));



CREATE POLICY "customer submits payment" ON "public"."payments" FOR INSERT TO "authenticated" WITH CHECK ((("status" = 'pending'::"public"."payment_status") AND ("created_by" = "auth"."uid"()) AND (EXISTS ( SELECT 1
   FROM "public"."shipments" "s"
  WHERE (("s"."id" = "payments"."shipment_id") AND ("s"."customer_id" = "public"."my_customer_id"()))))));



ALTER TABLE "public"."customers" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."deliveries" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."delivery_documents" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."delivery_helpers" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."delivery_incidents" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "driver logs fuel" ON "public"."fuel_logs" FOR INSERT TO "authenticated" WITH CHECK (("driver_id" = "public"."my_driver_id"()));



CREATE POLICY "driver reads assigned shipments" ON "public"."shipments" FOR SELECT TO "authenticated" USING (("id" IN ( SELECT "public"."my_driver_shipment_ids"() AS "my_driver_shipment_ids")));



CREATE POLICY "driver reads own compliance" ON "public"."compliance_documents" FOR SELECT TO "authenticated" USING (("driver_id" = "public"."my_driver_id"()));



CREATE POLICY "driver reads own deliveries" ON "public"."deliveries" FOR SELECT TO "authenticated" USING (("driver_id" = "public"."my_driver_id"()));



CREATE POLICY "driver reads own delivery helpers" ON "public"."delivery_helpers" FOR SELECT TO "authenticated" USING ((EXISTS ( SELECT 1
   FROM "public"."deliveries" "d"
  WHERE (("d"."id" = "delivery_helpers"."delivery_id") AND ("d"."driver_id" = "public"."my_driver_id"())))));



CREATE POLICY "driver reads own docs" ON "public"."delivery_documents" FOR SELECT TO "authenticated" USING (("delivery_id" IN ( SELECT "deliveries"."id"
   FROM "public"."deliveries"
  WHERE ("deliveries"."driver_id" = "public"."my_driver_id"()))));



CREATE POLICY "driver reads own fuel" ON "public"."fuel_logs" FOR SELECT TO "authenticated" USING (("driver_id" = "public"."my_driver_id"()));



CREATE POLICY "driver reads own history" ON "public"."shipment_status_history" FOR SELECT TO "authenticated" USING (("shipment_id" IN ( SELECT "deliveries"."shipment_id"
   FROM "public"."deliveries"
  WHERE ("deliveries"."driver_id" = "public"."my_driver_id"()))));



CREATE POLICY "driver reads own incidents" ON "public"."delivery_incidents" FOR SELECT TO "authenticated" USING (("delivery_id" IN ( SELECT "deliveries"."id"
   FROM "public"."deliveries"
  WHERE ("deliveries"."driver_id" = "public"."my_driver_id"()))));



CREATE POLICY "driver reads own tracking" ON "public"."tracking_logs" FOR SELECT TO "authenticated" USING (("driver_id" = "public"."my_driver_id"()));



CREATE POLICY "driver reads own truck" ON "public"."trucks" FOR SELECT TO "authenticated" USING ((("assigned_driver_id" = "public"."my_driver_id"()) OR ("id" IN ( SELECT "deliveries"."truck_id"
   FROM "public"."deliveries"
  WHERE ("deliveries"."driver_id" = "public"."my_driver_id"())))));



CREATE POLICY "driver reads self" ON "public"."drivers" FOR SELECT TO "authenticated" USING (("user_id" = "auth"."uid"()));



CREATE POLICY "driver reports incident" ON "public"."delivery_incidents" FOR INSERT TO "authenticated" WITH CHECK ((("reported_by" = "auth"."uid"()) AND (EXISTS ( SELECT 1
   FROM "public"."deliveries" "d"
  WHERE (("d"."id" = "delivery_incidents"."delivery_id") AND ("d"."driver_id" = "public"."my_driver_id"()) AND ("d"."shipment_id" = "delivery_incidents"."shipment_id"))))));



CREATE POLICY "driver uploads POD" ON "public"."delivery_documents" FOR INSERT TO "authenticated" WITH CHECK ((("uploaded_by" = "auth"."uid"()) AND ("delivery_id" IN ( SELECT "deliveries"."id"
   FROM "public"."deliveries"
  WHERE ("deliveries"."driver_id" = "public"."my_driver_id"())))));



CREATE POLICY "driver writes own tracking" ON "public"."tracking_logs" FOR INSERT TO "authenticated" WITH CHECK ((("driver_id" = "public"."my_driver_id"()) AND ("delivery_id" IN ( SELECT "deliveries"."id"
   FROM "public"."deliveries"
  WHERE (("deliveries"."driver_id" = "public"."my_driver_id"()) AND ("deliveries"."status" = ANY (ARRAY['loading'::"public"."delivery_status", 'in_transit'::"public"."delivery_status"])))))));



ALTER TABLE "public"."drivers" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."fuel_logs" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."helpers" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."location_vehicle_types" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."locations" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."maintenance_records" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "mark message read" ON "public"."messages" FOR UPDATE TO "authenticated" USING (("recipient_id" = "auth"."uid"())) WITH CHECK (("recipient_id" = "auth"."uid"()));



ALTER TABLE "public"."messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."notification_preferences" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."notifications" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "own notifications delete" ON "public"."notifications" FOR DELETE TO "authenticated" USING (("user_id" = "auth"."uid"()));



CREATE POLICY "own notifications read" ON "public"."notifications" FOR SELECT TO "authenticated" USING (("user_id" = "auth"."uid"()));



CREATE POLICY "own notifications update" ON "public"."notifications" FOR UPDATE TO "authenticated" USING (("user_id" = "auth"."uid"())) WITH CHECK (("user_id" = "auth"."uid"()));



CREATE POLICY "own preferences" ON "public"."notification_preferences" TO "authenticated" USING (("user_id" = "auth"."uid"())) WITH CHECK (("user_id" = "auth"."uid"()));



ALTER TABLE "public"."payments" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."pricing_rates" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "read active rates" ON "public"."pricing_rates" FOR SELECT TO "authenticated" USING (("is_active" OR ("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))));



CREATE POLICY "read own messages" ON "public"."messages" FOR SELECT TO "authenticated" USING ((("sender_id" = "auth"."uid"()) OR ("recipient_id" = "auth"."uid"())));



CREATE POLICY "read own profile" ON "public"."users" FOR SELECT TO "authenticated" USING (("id" = "auth"."uid"()));



CREATE POLICY "read reference" ON "public"."cargo_categories" FOR SELECT TO "authenticated" USING (true);



CREATE POLICY "read reference" ON "public"."locations" FOR SELECT TO "authenticated" USING (true);



CREATE POLICY "read reference" ON "public"."truck_cargo_categories" FOR SELECT TO "authenticated" USING (true);



CREATE POLICY "read reference" ON "public"."vehicle_types" FOR SELECT TO "authenticated" USING (true);



CREATE POLICY "read settings" ON "public"."system_settings" FOR SELECT TO "authenticated" USING (true);



CREATE POLICY "send messages" ON "public"."messages" FOR INSERT TO "authenticated" WITH CHECK (("sender_id" = "auth"."uid"()));



ALTER TABLE "public"."shipment_status_history" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."shipments" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "staff full access" ON "public"."cargo_categories" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."compliance_documents" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."customers" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."deliveries" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."delivery_documents" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."delivery_helpers" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."delivery_incidents" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."drivers" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."fuel_logs" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."helpers" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."locations" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."maintenance_records" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."payments" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."shipments" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."truck_cargo_categories" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."trucks" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff full access" ON "public"."vehicle_types" TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"]))) WITH CHECK (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff read users" ON "public"."users" FOR SELECT TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff reads history" ON "public"."shipment_status_history" FOR SELECT TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



CREATE POLICY "staff reads tracking" ON "public"."tracking_logs" FOR SELECT TO "authenticated" USING (("public"."get_user_role"() = ANY (ARRAY['admin'::"text", 'staff'::"text"])));



ALTER TABLE "public"."system_settings" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."toll_gate_rates" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."tracking_logs" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."truck_cargo_categories" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."trucks" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "update own profile" ON "public"."users" FOR UPDATE TO "authenticated" USING (("id" = "auth"."uid"())) WITH CHECK (("id" = "auth"."uid"()));



ALTER TABLE "public"."users" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."vehicle_types" ENABLE ROW LEVEL SECURITY;




ALTER PUBLICATION "supabase_realtime" OWNER TO "postgres";






ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."deliveries";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."messages";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."notifications";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."shipments";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."tracking_logs";



REVOKE USAGE ON SCHEMA "public" FROM PUBLIC;
GRANT ALL ON SCHEMA "public" TO PUBLIC;
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";















































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































GRANT ALL ON FUNCTION "public"."driver_set_delivery_status"("p_delivery" "uuid", "p_status" "public"."delivery_status") TO "authenticated";



REVOKE ALL ON FUNCTION "public"."notify_user"("p_user" "uuid", "p_type" "public"."notification_type", "p_title" "text", "p_body" "text", "p_shipment" "uuid") FROM PUBLIC;

















































































GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."activity_logs" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."activity_logs" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."compliance_documents" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."compliance_documents" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."deliveries" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."deliveries" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."drivers" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."drivers" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."available_drivers" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."available_drivers" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."trucks" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."trucks" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."available_trucks" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."available_trucks" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."cargo_categories" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."cargo_categories" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."system_settings" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."system_settings" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."compliance_status" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."compliance_status" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."customers" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."customers" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."delivery_documents" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."delivery_documents" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."delivery_helpers" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."delivery_helpers" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."delivery_incidents" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."delivery_incidents" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."fuel_logs" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."fuel_logs" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."helpers" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."helpers" TO "authenticated";



GRANT USAGE ON SEQUENCE "public"."shipment_no_seq" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."shipments" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."shipments" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."tracking_logs" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."tracking_logs" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."latest_truck_positions" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."latest_truck_positions" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."location_vehicle_types" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."location_vehicle_types" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."locations" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."locations" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."maintenance_records" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."maintenance_records" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."messages" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."messages" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."notification_preferences" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."notification_preferences" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."notifications" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."notifications" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."payments" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."payments" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."pricing_rates" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."pricing_rates" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."shipment_overview" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."shipment_overview" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."shipment_status_history" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."shipment_status_history" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."toll_gate_rates" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."toll_gate_rates" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."truck_cargo_categories" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."truck_cargo_categories" TO "authenticated";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."users" TO "anon";
GRANT ALL ON TABLE "public"."users" TO "authenticated";
GRANT ALL ON TABLE "public"."users" TO "service_role";



GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."vehicle_types" TO "anon";
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE "public"."vehicle_types" TO "authenticated";









ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT SELECT,USAGE ON SEQUENCES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT SELECT,USAGE ON SEQUENCES TO "authenticated";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT SELECT,INSERT,DELETE,UPDATE ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT SELECT,INSERT,DELETE,UPDATE ON TABLES TO "authenticated";




























