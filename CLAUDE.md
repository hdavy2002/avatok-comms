# avatok-comms — AI assistant instructions

avatok-comms is the AvaTok mobile app: a Jami-derived Android client
(package `com.avatok.comms`). AvaTok-specific code lives under `app/src/`
(flavors: `main`, `withFirebase`, `withUnifiedPush`, `noPush`). The engine
is vendored as a git submodule at `vendor/jami-client-android` (upstream
Jami — not ours; do not index or grep it blindly, it is huge).

## Graphify: search the graph FIRST, and keep it indexed

The `app/` module is indexed by **graphify** into
`app/graphify-out/graph.json`.

- **Search-first (saves tokens):** to locate code or trace structure
  ("where is X / what calls Y / how does Z connect"), use graphify BEFORE
  `grep`/`rg`/`glob`. CLI:
  `graphify query "<question>" --graph app/graphify-out/graph.json`,
  `graphify explain "<node>" --graph app/graphify-out/graph.json`,
  `graphify path "A" "B" --graph app/graphify-out/graph.json`. If a
  graphify MCP is wired to this graph, prefer its query tools. Fall back to
  grep only when graphify lacks the answer (brand-new files, exact-string
  matches, non-code assets).

- **Index-on-change:** after committing or pushing, re-index:
  `cd /Users/davy/Documents/websites/avatok-comms && graphify update app`.
  Always scope to `app` — never `graphify update .` (that pulls in the
  giant vendored Jami submodule). A `post-commit` hook runs this on normal
  commits, but plumbing commits and `git push` bypass git hooks, so run it
  explicitly then. Also re-index after adding Graphiti episodes.

## Git: plumbing commits, host-shell push, correct author

- **Porcelain git hangs** here (`git status` / `commit` / `add .`) on the
  vendored Jami submodule walk. Commit with plumbing instead:
  `git update-index --add <files>` → `git write-tree` →
  `git commit-tree <tree> -p HEAD -m "..."` →
  `git update-ref refs/heads/main <new> <parent>`.
  (Plumbing skips git hooks, so run `graphify update app` manually after.)
- **Author MUST be `hdavy2002@gmail.com`** — other emails cause Vercel
  BLOCKED deploys on the linked web project.
- **`git push` only from the host shell** (Desktop Commander), never the
  sandbox (no GitHub creds there). avatok-comms has no pre-push hook, so a
  plain `git push origin main` works once on the host.

## Build/test: CI is the loop, not the sandbox

Don't build/test the Android app in the sandbox. Push in small phases and
read GitHub Actions (`gh run list/view --repo hdavy2002/avatok-comms`) as
the source of truth. The `withFirebase` flavor builds with
`-PbuildFirebase=true` and injects `google-services.json` from a GH Actions
secret; the `google-services` plugin must stay declared `apply false` at
the root `build.gradle.kts`.

## Architecture pointers

Push wakeup = Jami daemon + `dhtproxy.jami.net` + a Cloudflare Worker
bridge (`avatok-comms-bridge`, repo `hdavy2002/avatok-comms-bridge`) +
Firebase FCM; calls relay via Cloudflare Realtime TURN. Full detail lives
in the Cowork auto-memory entry `avatok-comms-push-architecture` and the
`avatok/docs/sessions/` journals in the web repo.
