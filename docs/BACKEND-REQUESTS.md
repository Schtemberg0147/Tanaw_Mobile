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
