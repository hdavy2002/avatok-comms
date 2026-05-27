# Commit B — AvaTok WebView login

**Landed:** 2026-05-27  
**Spec:** mirrored in avatok repo at
`docs/proposals/avatok-comms-jami-mvp-amendment-1.md` and
`docs/proposals/avatok-comms-backend-endpoints.md`.

## What this commit does

On first run, when the device has no local Jami account, the launcher
now routes the user to **AvaTokLoginActivity** instead of straight to
Jami's `AccountWizardActivity`.

`AvaTokLoginActivity` is a single-purpose screen:

- AvaTok logo header (drawable `avatok_logo.webp` copied from
  avatok.ai's `/public/logo.webp`).
- Full-bleed WebView pointed at `https://avatok.ai/login`. Because the
  page is the real avatok.ai Clerk-hosted login, the user gets **sign
  in, sign up, and forgot-password** as inline links — no native
  rebuild required.
- A loading overlay shown while the WebView navigates and during the
  hand-off to the wizard.

`WebViewClient.onPageFinished` watches for the URL to settle on a
post-login avatok.ai page (anything that's not `/login`, `/register`,
`/sign-in`, `/sign-up`, `/forgot-password`, `/reset-password`,
`/sso-callback`, or under `/api/`). When that happens:

1. The Clerk session cookie is captured via `CookieManager`.
2. `AvaTokSession.save()` persists the cookie + (placeholder) profile
   info into `SharedPreferences("avatok_session")`.
3. The activity hands off to `AccountWizardActivity` with
   `FLAG_ACTIVITY_NEW_TASK|CLEAR_TASK` so the existing rebranded Jami
   wizard creates a local Jami keypair, profile, biometric enrollment,
   etc — exactly the flow Jami already ships, just gated behind an
   AvaTok login.

## Why "local Jami keypair" not "fetch keypair from avatok.ai"

The backend endpoint `GET /api/jami/keypair` is live on avatok.ai
(commit `f0dbfb8c` over there). Its PEM bundle is, per the deferred
work called out in `src/lib/jami/keypair.ts` on the avatok side, a
labelled **stub** until we solve Jami's archive-format wrapping
(JAMS proxy, libjami sidecar, or reverse-engineering "manual export"
— 1–2 days of work).

So Commit B intentionally **does not** call the backend keypair
endpoint yet. The device-side Jami daemon generates the keypair, the
user becomes reachable on the Jami DHT immediately, and the
"cross-device same-identity" ambition is a follow-up that swaps in
`GET /api/jami/keypair` once the archive format lands.

This means: each install gets a fresh Jami ID today. If the user
reinstalls, they get a new identity. That's a known limitation,
acceptable for the tester cohort and documented for testers in the
README.

## Files touched

| Path | Change |
|---|---|
| `app/src/main/java/com/avatok/comms/account/AvaTokLoginActivity.kt` | New — WebView login activity. |
| `app/src/main/java/com/avatok/comms/account/AvaTokSession.kt` | New — `SharedPreferences` wrapper for the session cookie. |
| `app/src/main/res/layout/activity_avatok_login.xml` | New — logo header + WebView + loading overlay. |
| `app/src/main/res/drawable/avatok_logo.webp` | New — copied from avatok.ai/public/logo.webp. |
| `app/src/main/res/values/strings.xml` | + three strings (`avatok_login_setting_up`, `avatok_login_failed`, `avatok_login_network_error`). |
| `app/src/main/AndroidManifest.xml` | Registered `AvaTokLoginActivity`. |
| `app/src/main/java/com/avatok/comms/client/HomeActivity.kt` | First-run gate (`accounts.isEmpty()`) now routes through `AvaTokLoginActivity` if no AvaTok session, else falls through to `AccountWizardActivity`. |

## What testers can verify after this APK builds

1. Fresh install → app opens to **AvaTok logo + login WebView**.
2. Sign in / sign up / forgot password all work (because they're the
   real avatok.ai flow).
3. After Clerk redirects to the post-login page, the loading overlay
   shows for a beat, then the existing AvaTok-branded wizard starts.
4. Wizard completes → HomeActivity with the chat list, ready to add
   contacts, voice/video call, etc.

## Follow-ups (Commit B' / C)

- **Real backend keypair fetch.** Once the Jami archive-format
  wrapping is solved in `src/lib/jami/keypair.ts` (on the avatok
  side), wire `AvaTokLoginActivity.onLoginSuccess()` to call
  `GET /api/jami/keypair` with the captured cookie, write the
  resulting PEM bundle to libjami's archive-import path, and call
  `AccountService.addAccount` with `archive*` properties instead of
  delegating to the wizard. This is what gives the user the same Jami
  ID across reinstalls and devices.
- **Logout / change account UI.** Today there's no path back to the
  AvaTok login once the user signs in. Add a Settings entry that
  clears `AvaTokSession` + removes the local Jami account.
- **`/api/me`-style profile endpoint.** Capture display name / email /
  user ID at login time so the rebranded UI can show "Signed in as X"
  instead of just the Jami ID.
- **Server-side log upload** (Commit C, when reached).
