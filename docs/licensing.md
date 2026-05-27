# AvaTok Comms — licensing

## What's GPL and why

The Jami engine (`libjami`, `libjamiclient`) is GPL-3.0-or-later. We
link it **in-process** via JNI: the daemon is compiled into a shared
library that loads inside our Android app's process, and our Kotlin
code calls JNI-generated bindings to invoke its functions.

Linking GPL code in-process means the combined work must be released
under GPL. Therefore:

- **The entire AvaTok Comms Android app is GPL-3.0-or-later.**
- **This repository is public** (a GPL-3.0 program must be distributable
  in source form to any recipient of the binary).
- The full GPL-3.0 text is in [`LICENSE`](../LICENSE).
- Third-party attributions are in [`NOTICE`](../NOTICE).

This is **not a workaround or a mistake** — it was a deliberate,
accepted trade-off when we picked Jami over building a P2P engine from
scratch. See the proposal at
`avatok.ai/docs/proposals/avatok-comms-jami-mvp.md §1.3`.

## What stays closed

The closed AvaTok backend — wallet, marketplace, payments, ledger,
business logic — lives in a **separate repository** (not yet built;
not this one, and emphatically not `avatok.ai` either) and is reached
only via a network HTTP API. **GPL does not cross a network API
boundary.** The Android app calls the backend the same way any closed
mobile app calls a SaaS backend — the backend's source code is not
"linked" with the app, just called over the network.

This is the standard accepted GPL-client / closed-network-server
architecture. Examples in the wild:

- Many GPL Android apps (F-Droid catalog) talk to closed REST APIs
- Linux kernel (GPL) runs closed userspace cloud services
- The Jami project itself works this way — the GPL daemon talks to
  Jami's name service (proprietary) over HTTPS

## The unbreakable separation rule

The existing `avatok.ai` Next.js web app (`/Users/davy/Documents/websites/avatok/`)
is **closed source**. To keep the GPL boundary clean and to prevent
inadvertent contamination, the following rules apply:

1. This repository (`avatok-comms`) **must never import, copy, or
   refactor** any source from `avatok.ai`.
2. This repository **must never share** build config, dependencies,
   environment files, CI/CD, deployment, DNS, or hosting with
   `avatok.ai`.
3. The future closed AvaTok backend will live in a **third, separate
   repository** — not in `avatok.ai`, not in `avatok-comms`.
4. The only communication between `avatok-comms` and the backend is
   ordinary HTTPS calls. The backend's source code is **not** in this
   repo and is **not** linked into this app.

Three repos, three concerns, three licences:

| Repo | What it is | Licence | Visibility |
|---|---|---|---|
| `avatok.ai` (existing) | Next.js web app — creator marketplace UI | proprietary | private |
| `avatok-comms` (this) | Android app on Jami engine | **GPL-3.0** | **public** |
| `avatok-backend` (future) | Wallet, marketplace, payments | proprietary | private |

The two proprietary repos never link Jami code in-process. They never
link `avatok-comms` code either. They only talk to the network.

## Distribution obligations

Because we distribute a GPL binary (the APK), we must:

1. Provide the **complete corresponding source** to any recipient of
   the binary. This repo + the vendored Jami submodule satisfy that.
2. Include the **GPL-3.0 license text** with the binary. Our APK
   resource bundle includes `LICENSE` (planned for Phase 6).
3. Pass on the **same GPL-3.0 rights** to recipients (no further
   restrictions, no DRM that prevents source compliance).
4. **Attribute Jami** and the other GPL/LGPL components per their
   notices. See [`NOTICE`](../NOTICE).

## What this means for users

Anyone who installs the AvaTok Comms APK:

- Receives the same GPL-3.0 rights — they can read this source, modify
  it, redistribute it.
- Sees an "About" screen with the license info and a link back to this
  repository (planned for Phase 6).
- Talks to **no AvaTok server** when using messaging or calling. Pure
  peer-to-peer over Jami's DHT.
- Only contacts an AvaTok server later, when the future backend exists,
  for closed marketplace functions — and only via clearly-labelled
  HTTPS calls.

## Legal sign-off

Before public release of an APK signed for the Play Store / F-Droid,
a licensing lawyer should review:

- The GPL-client / closed-backend split is implemented as described above
- No proprietary business logic has leaked into this repo
- The attribution and license text are correct and complete
- The Jami trademark and naming are respected (we do not call our app
  "Jami" or use Jami's logo; we attribute Jami's project)

This file is a description of the intended architecture and is **not**
legal advice.
