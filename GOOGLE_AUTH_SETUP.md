# Google sign-in setup (Tanaw-Dev)

The Android implementation uses Credential Manager and exchanges the Google ID
token with Supabase. No Google client secret is shipped in the app. Email login
remains available. New customers missing a valid contact number complete their
profile before entering the workspace.

## Google Cloud

1. Open https://console.cloud.google.com/auth/clients and select/create Tanaw.
2. Configure Google Auth Platform branding, support contact and audience. Use
   External for personal Google accounts. During Testing, add your test accounts.
   Request only `openid`, email and profile; no Drive/Gmail permissions needed.
3. Create an OAuth client of type **Web application**. Authorized redirect URI:
   `https://wrcrxsvppehuaqennrla.supabase.co/auth/v1/callback`.
4. Create an OAuth client of type **Android** in the same Cloud project:
   package `com.tanaw.app`; debug SHA-1
   `DF:16:BC:B6:1F:A9:91:E2:3A:76:91:4D:8F:1E:4A:FB:9D:EC:8B:D9`.
   This fingerprint belongs to this machine's debug key. Register the release/Play
   signing fingerprint separately before shipping.

## Supabase and local app

1. In Tanaw-Dev Authentication → Sign In / Providers → Google, enable Google.
2. Enter the Web client ID and secret. If registering multiple client IDs in
   Supabase, list the Web client ID first, followed by the Android client ID.
   Keep nonce validation enabled. Do not disable email confirmation as a workaround.
3. Add `GOOGLE_WEB_CLIENT_ID=<web-client-id>.apps.googleusercontent.com` to the
   existing ignored `local.properties`, or set that environment variable. Use the
   **Web** client ID, not the Android ID. Never place the secret in local.properties,
   source control, chat, screenshots or memory notes.
4. Rebuild/install. Tap Continue with Google, select an owned account and complete
   its consent flow. Complete the contact profile when prompted.

## Acceptance

- New account receives customer access only; existing office/driver roles remain server-controlled.
- Verify sign-in, cancel/retry, sign-out, account switching, cold session restore,
  profile save and owned shipment visibility on an actual Google-enabled device.
- Missing local configuration shows a safe setup message; this is not a working
  provider until both Google Cloud and Supabase settings are configured.
- No OAuth client or provider settings were created by the implementation alone.

References: https://supabase.com/docs/guides/auth/social-login/auth-google
and https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation
