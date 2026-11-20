// @ts-nocheck
// supabase/functions/create-driver-user/index.ts
// Creates a Supabase Auth user for a newly registered driver.
// Uses the service_role key so the calling admin's session is unaffected.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function buildCorsHeaders(req: Request): Record<string, string> {
  const configuredOrigin = Deno.env.get("ALLOWED_ADMIN_ORIGIN")?.trim();
  const requestOrigin = req.headers.get("Origin")?.trim() || "";
  const allowOrigin = configuredOrigin
    ? (requestOrigin === configuredOrigin ? configuredOrigin : configuredOrigin)
    : requestOrigin || "*";
  return {
    "Access-Control-Allow-Origin": allowOrigin,
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers":
      "authorization, x-client-info, apikey, content-type",
    "Vary": "Origin",
  };
}

function jsonResponse(
  body: Record<string, unknown>,
  status: number,
  corsHeaders: Record<string, string>,
): Response {
  return new Response(JSON.stringify(body), {
    headers: { ...corsHeaders, "Content-Type": "application/json" },
    status,
  });
}

Deno.serve(async (req) => {
  const corsHeaders = buildCorsHeaders(req);

  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "METHOD_NOT_ALLOWED" }, 405, corsHeaders);
  }

  try {
    // ---- 1. Verify the caller is an authenticated admin/staff ----
    const authHeader = req.headers.get("Authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return jsonResponse({ error: "UNAUTHORIZED" }, 401, corsHeaders);
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
    if (!supabaseUrl || !serviceRoleKey || !anonKey) {
      return jsonResponse({ error: "SERVER_MISCONFIGURED" }, 500, corsHeaders);
    }

    // Verify caller's JWT using the anon key
    const callerClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const {
      data: { user: caller },
      error: callerErr,
    } = await callerClient.auth.getUser();
    if (callerErr || !caller) {
      return jsonResponse({ error: "UNAUTHORIZED" }, 401, corsHeaders);
    }

    // Initialize admin client with service_role key to bypass RLS for administrative checks & actions
    const adminClient = createClient(supabaseUrl, serviceRoleKey);

    // Check caller's role in public.users using adminClient
    const { data: callerProfile, error: profileErr } = await adminClient
      .from("users")
      .select("role, is_active")
      .eq("id", caller.id)
      .single();

    if (
      profileErr ||
      !callerProfile ||
      callerProfile.is_active !== true ||
      !["admin", "staff"].includes(callerProfile.role)
    ) {
      return jsonResponse(
        { error: "Only active admin/staff can create driver accounts." },
        403,
        corsHeaders,
      );
    }

    // ---- 2. Parse & validate request body ----
    const body = await req.json().catch(() => null);
    const email = typeof body?.email === "string" ? body.email.trim().toLowerCase() : "";
    const password = typeof body?.password === "string" ? body.password : "";
    const fullName = typeof body?.fullName === "string" ? body.fullName.trim() : "";

    if (!email || email.length > 254 || !EMAIL_REGEX.test(email)) {
      return jsonResponse({ error: "VALID_EMAIL_REQUIRED" }, 400, corsHeaders);
    }
    if (!password || password.length < 8 || password.length > 128) {
      return jsonResponse(
        { error: "Password must be between 8 and 128 characters." },
        400,
        corsHeaders,
      );
    }
    if (fullName.length > 120) {
      return jsonResponse({ error: "INVALID_FULL_NAME" }, 400, corsHeaders);
    }

    // ---- 3. Create auth user with service role key ----
    const { data: newUser, error: createErr } =
      await adminClient.auth.admin.createUser({
        email,
        password,
        email_confirm: true, // Skip email verification for admin-provisioned accounts
        app_metadata: { role: "driver" },
        user_metadata: {
          full_name: fullName,
          role: "driver",
        },
      });

    if (createErr || !newUser?.user?.id) {
      return jsonResponse({ error: "DRIVER_CREATION_FAILED" }, 400, corsHeaders);
    }

    const newUserId = newUser.user.id;

    // ---- 4. Ensure public.users row has role='driver' and full_name ----
    // Roll back auth.users creation if the profile upsert fails so no orphaned auth account remains.
    const { error: upsertErr } = await adminClient
      .from("users")
      .upsert({
        id: newUserId,
        email,
        role: "driver",
        full_name: fullName,
        is_active: true,
      });

    if (upsertErr) {
      await adminClient.auth.admin.deleteUser(newUserId).catch(() => {});
      return jsonResponse({ error: "DRIVER_PROFILE_SYNC_FAILED" }, 500, corsHeaders);
    }

    // ---- 5. Return the new user's UUID ----
    return jsonResponse({ userId: newUserId }, 200, corsHeaders);
  } catch (_err) {
    return jsonResponse({ error: "INTERNAL_ERROR" }, 500, corsHeaders);
  }
});


