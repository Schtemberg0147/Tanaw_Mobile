# Tanaw Mobile

This branch connects customers and drivers to **Tanaw Dev**:
`wrcrxsvppehuaqennrla.supabase.co`.

## Build after cloning

1. Open the project in Android Studio and let it create your local SDK path.
2. Keep `sdk.dir` in ignored `local.properties`. The repository's
   `client-config.properties` supplies the public Dev URL and publishable client key.
3. Remove old `SUPABASE_URL` and `SUPABASE_ANON_KEY` entries from
   `local.properties` and your environment. Restart Android Studio if you changed
   environment variables. This branch rejects mismatched URL/key overrides.
4. Sync Gradle, then run `./gradlew :app:verifyPhoneSignup :app:assembleDebug`
   (Windows: `gradlew.bat :app:verifyPhoneSignup :app:assembleDebug`).
5. Install/run the newly built APK on the test device. Pulling code alone does not
   replace an APK already installed on another phone.

The verification task prints only the backend hostname and phone-provider status.
It does not create an account or send SMS. A successful check verifies provider
configuration, not delivery of an actual SMS. The server's signed SMS hook and
gateway must also work; see [TextBee setup](TEXTBEE_SETUP.md).

## “Phone signup is not enabled yet”

That message maps to Supabase's `phone_provider_disabled` response. A similarly
named project, an old `local.properties`, an environment override, or an old APK
can point at the original Tanaw backend instead of Dev. Verify the hostname with
the task above, rebuild, and install the new APK. Do not enable providers on a
different backend merely to work around an incorrectly configured build.

`client-config.properties` contains public client settings, not server credentials.
Database row-level security remains authoritative. Never add service-role keys,
TextBee API keys, Auth hook signing secrets, passwords, or another developer's
`local.properties` to the repository. Optional local MapTiler/Google configuration
remains separate; see [Google Auth setup](GOOGLE_AUTH_SETUP.md).

## Driver login and recovery acceptance

Run `gradlew.bat :app:verifyDriverLogin` to check the configured backend's email
and phone Auth providers. If this build has a Google Web client ID, the check also
requires the backend Google provider to be enabled. Without a client ID, the app
does not offer Google sign-in. This public-settings check sends no codes and
does not prove SMS/email delivery or Google consent/device configuration.

The active login supports phone/password and email/password, then routes using
the authenticated server profile. Terms and Privacy open accessible panels that
explicitly explain that approved company documents have not been published.
Opening a panel does not record legal acceptance.

Recovery retains only encrypted, project-scoped target/page/request time for
15 minutes, never the password or code. This retention window does not extend
server code expiry. A cold restart restores code entry and resend cooldown;
Back to sign-in clears the challenge and cooldown. Request a fresh code if the
server rejects an expired one.

`DriverLoginAcceptanceTest` exercises legal navigation and synthetic restored,
expired and cleared challenges without sending messages. `LiveDriverLoginTest`
is opt-in: runner arguments supply an authorized test account and, separately,
an owned phone recovery request/code. Never put these values in source, saved
commands, Gradle properties or version control. Verify phone recovery across
separate app processes, then password login, driver profile routing and session
restoration. Google acceptance additionally needs an owned Google account and
the configuration in GOOGLE_AUTH_SETUP.md.
