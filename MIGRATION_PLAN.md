# CifliX — CloudStream Core Migration

This branch is reserved for the CloudStream-core migration. The existing CifliX Android implementation remains untouched on `feature/iraqi-platform-native`.

## Target

Rebase the Android app on the upstream `recloudstream/cloudstream` Kotlin application so that CifliX uses the native CloudStream extension runtime and can consume compatible `.cs3` extensions and `repo.json` repositories.

## Product scope

- Android app only.
- Brand: CifliX.
- Arabic-first interface.
- Movies and series.
- Native repository manager and extension installer.
- Native playback and subtitles.
- No admin panel, custom backend/server, live TV, or sports sections.

## Required migration work

1. Import the upstream CloudStream source at a pinned commit and preserve its GPL-3.0 license, copyright notices, and source-distribution obligations.
2. Rebrand application label, launcher assets, package/application ID, update metadata, and user-facing references to CifliX.
3. Keep the native repository and `.cs3` plugin loading implementation intact; do not substitute the old JavaScript/Nuvio plugin runtime.
4. Localize the user-facing UI to Arabic where translations are available and verify RTL layouts.
5. Remove or hide out-of-scope live-TV and sports entry points without breaking shared player or extension APIs.
6. Configure a reproducible Android release build and produce a test APK.
7. Test repository add/remove/update, extension install/uninstall/update, provider catalog loading, movie/series details, episode selection, stream extraction, subtitles, and Media3 playback.

## Acceptance criteria

- A valid CloudStream `repo.json` can be added and its plugin list displayed.
- A compatible `.cs3` extension can be installed and loaded by the native runtime.
- The installed provider exposes its supported movie/series catalog.
- A playable stream can be selected and played, with subtitle selection where supplied.
- Build and installation succeed on Android 16 / Poco F7.
- Failures from unavailable third-party websites are surfaced clearly; external provider uptime cannot be guaranteed.

## Important UX clarification

Adding a repository makes its available extensions visible. The user then installs an extension; that provider supplies its own catalog. A repository URL alone does not automatically install every extension or populate a universal movie catalog.

## Current status

- [x] Created an isolated migration branch.
- [x] Confirmed upstream CloudStream is Kotlin-based and GPL-3.0.
- [ ] Import upstream source at a pinned commit.
- [ ] Rebrand and Arabic/RTL adaptation.
- [ ] Scope cleanup (movies and series only).
- [ ] Build and device verification.
