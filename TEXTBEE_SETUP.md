# TextBee integration status and activation

## Current status — 2026-10-25 phone-first continuation

Phone/password signup is already wired in the active mobile workspace. Login now
opens with a phone-number field and phone keyboard; existing email accounts use
the explicit email option. Supabase still generates and verifies OTPs; TextBee
only delivers the SMS through the server-side signed Send SMS hook.

The historical worker-authentication blocker below is resolved: the saved
closeout records worker v2 and a read-only device HTTP200. Those deployment and
device results have not been rechecked in this continuation. Remaining activation
was the signed Auth Send SMS hook/signing secret and Phone provider, followed by
an owned-number signup/recovery test. During this continuation the user saved the
hook/signing credential and reported saving TEXTBEE_AUTH_HOOK_SECRET; the dashboard
confirms the HTTPS Send SMS hook is enabled. Edge secret equality is unverified.
Phone provider and phone confirmations are now verified enabled after the user
saved settings; OTP length is six digits and expiry is300 seconds (5 minutes). Actual SMS
receipt/signup/recovery remains unverified. Never paste secrets into chat.
Shipment-alert scheduling is separate from delivering signup OTPs.

Tanaw-Dev only. Existing email/password users keep their login. New customers use
phone/password signup, Supabase SMS OTP verification and phone/password login.
SMS recovery requests use existing accounts only. Shipment SMS is optional in Profile.

## Deployed and tested

- `textbee-auth` v1: signed Supabase Send SMS HTTP hook; rejects unverified signatures.
- `textbee-worker` v1: service-only read-only device check and shipment queue processor.
- Migration `20261025060231` (`textbee_sms_delivery`): nullable email for phone-only
  accounts, authoritative Auth phone propagation, private outbox and service-only
  queue reservation. No existing customer email/phone data was rewritten.
- Queue requires active owner, opted-in notification type, verified Auth phone
  matching contact number. Send-time opt-out checked. Rapid duplicates coalesced;
  at most 20 shipment attempts per Philippine day. Ambiguous results never auto-retry.
- Provider acceptance means queued, not delivered. No real SMS acceptance verified.

## Remaining activation

1. TextBee API key and gateway device ID have been saved in Edge Function secrets.
   Secret names verified; device accessibility is NOT yet verified.
2. In Tanaw-Dev Authentication → Hooks, create a **Send SMS** HTTPS hook pointing to
   `https://wrcrxsvppehuaqennrla.supabase.co/functions/v1/textbee-auth`.
   Generate its signing secret and save the same value in Edge Function secrets as
   `TEXTBEE_AUTH_HOOK_SECRET`. Keep the value private. The function accepts the
   dashboard's `v1,whsec_...` secret format. Do not disable hook signature checks.
3. Enable Phone signup in Authentication → Sign In / Providers with SMS confirmation
   enabled. Keep existing email auth enabled. Check Auth SMS expiry/resend/rate limits
   before testing; codes are generated/verified by Supabase, not the phone app.
4. Resolve the worker authentication issue below before configuring its scheduler.
   Call the worker via a server-only scheduler every minute; never ship server
   credentials in Android/web code. Keep the worker disabled until authentication
   and send-time verification tests pass. The outbox currently has no queued jobs.
5. Install the rebuilt Android app; use an owned phone to test signup, wrong/expired
   code, resend, login, recovery and opt-in/out. A real SMS test needs the intended
   recipient's authorization and consumes carrier/platform quota.

## Worker authentication change awaiting approval

The current function compares a Bearer header with the runtime legacy service key.
The CLI's legacy service credential returned HTTP401, so no TextBee device request
was confirmed. Automatic approval review rejected a proposed deployment accepting
the configured server-key set as an unauthorized security-boundary expansion.
That patch was NOT applied or deployed.

Concrete narrower proposal for review: replace that comparison with an exact match
between the `apikey` header and **only** `SUPABASE_SECRET_KEYS.default`; use that
same default server key for the database client. Reject missing/default-missing,
publishable, user, other named secret and legacy keys. This lets holders of the
project's default server secret invoke health checks and process the limited queue.
No client-supplied recipient or message is accepted. Requires user approval before
changing the deployed authentication boundary. Then rerun health and queue checks
before enabling the scheduler. The health request itself sends no SMS.

## Operational limits

- Gateway phone needs power, internet, enabled SMS gateway and carrier SMS allowance.
- Free TextBee quotas also include OTPs; the 20/day shipment budget does not reserve
  or guarantee the provider's remaining OTP allowance.
- A changed operational contact number stops shipment SMS until it matches the
  verified signup number. Verified phone-change enrollment is separate work.
- Worker errors/stale reservations require office inspection; no automatic retry
  of unknown sends and no claim of final delivery without carrier status evidence.
- Rollback fixtures do not prove actual phone delivery or phone-provider settings.

References:
https://supabase.com/docs/guides/auth/auth-hooks/send-sms-hook
https://supabase.com/docs/guides/functions/auth
https://textbee.dev/docs/sending-sms/sending-sms

## Approved worker authentication correction —2026-10-25
- Checkpoint 2026-10-25T141041+0800-textbee-worker-auth; owner current chat. User explicitly approved narrower default-server-secret-only worker authentication and read-only device check. Earlier approval blocker resolved.
- Worker v2 deployed, SHA256decd52b78ba557b732bba5b9e4e01fe8b321c301c95992d060757b596e4744bd. Exact SUPABASE_SECRET_KEYS.default on apikey only; legacy/user/publishable/other named keys rejected; missing default fails closed.12 backend tests pass.
- Live GET configured=true/deviceAccessible=true/providerStatus=200. No SMS sent or queue processing. No hook/provider/scheduler/mobile changes this follow-up. Secrets stayed in runtime memory. Signed Auth hook secret/Phone provider/scheduler/live acceptance remain.
- Prior handoff [[../../../archive/2026-10-25T141041+0800-textbee-worker-auth/handoff]].
