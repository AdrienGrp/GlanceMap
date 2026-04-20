# Compliance Status

Last reviewed: 2026-04-20

Status legend:
- `DONE`: implemented and verified.
- `PENDING`: needs follow-up before/after release.
- `BLOCKER`: do not ship a bundled asset without resolution.

## Current checklist

1. User-facing legal entry reachable from watch settings
   - Status: `DONE`
   - Evidence: `Settings -> Licenses` screen available in app.

2. User-friendly credits page
   - Status: `DONE`
   - Evidence: `licenses/CREDITS_AND_THANKS.md`.

3. Open-source notices for core libraries
   - Status: `DONE`
   - Evidence: `licenses/THIRD_PARTY_NOTICES.md`.

4. Data and asset attribution
   - Status: `DONE`
   - Evidence: `licenses/DATA_AND_ASSET_ATTRIBUTION.md`.

5. Service/API terms page
   - Status: `DONE`
   - Evidence: `licenses/SERVICE_TERMS_AND_API_USAGE.md`.

6. Companion external source transparency
   - Status: `DONE`
   - Evidence: `licenses/COMPANION_EXTERNAL_SOURCES.md`.

7. OpenAndroMaps/Elevate bundled redistribution permission
   - Status: `DONE`
   - Evidence:
     - Project owner confirmed on 2026-04-20 that OpenAndroMaps and Tobias Kuehn agreed to GlanceMap's use/integration.
   - Notes:
     - Keep the original agreement messages outside the public repository.
     - Before switching visibility, record the request/response dates and approved scope in private release notes or an internal issue.
     - Scope should cover public source redistribution, APK/AAB bundling, and any modified/adapted Elevate theme/resources used by GlanceMap.

8. Elevate referenced resource-license file (`read_me_elevate.txt`)
   - Status: `DONE`
   - Evidence:
     - `app/src/main/assets/read_me_elevate.txt` is checked in next to the root source fallback `Elevate.xml`.
   - Notes:
     - Referenced by `app/src/main/assets/Elevate.xml`.
     - Generated theme assets currently include `read_me_elevate.txt` when the Elevate download/sync step succeeds.
     - The checked-in source fallback file preserves the upstream license/resource-license information needed for public source review.

9. Overpass public instance usage limits
   - Status: `PENDING`
   - Notes:
     - Main public policy indicates conservative limits (10,000 queries/day, <1 GB/day).
     - Monitor/import throttling should remain conservative.

10. OpenHiking bundled theme redistribution/license verification
   - Status: `PENDING`
   - Notes:
     - OpenHiking snapshot is now embedded in `app/src/main/assets/theme/openhiking`.
     - OpenHiking theme ZIP integration remains available as an optional refresh path in the build pipeline.
     - The observed ZIP did not include an obvious standalone license/readme file.
     - Confirm redistribution and attribution requirements with upstream before public release.

11. French Kiss bundled theme redistribution/license verification
   - Status: `PENDING`
   - Notes:
     - French Kiss snapshot is now embedded in `app/src/main/assets/theme/frenchkiss`.
     - Current snapshot was extracted from the user-provided XCTrack beta APK.
     - The embedded XML identifies it as an IGN TOP25 theme for XCTrack by Pascal Cochet.
     - No separate standalone license file was observed alongside the theme assets.
     - Confirm redistribution and attribution requirements with upstream/XCTrack before public release.

12. Tiramisu bundled theme non-commercial license review
   - Status: `PENDING`
   - Notes:
     - Tiramisu snapshot is embedded in `app/src/main/assets/theme/tiramisu`.
     - The observed release archive includes a CC BY-NC-SA 3.0 license.
     - Confirm whether public repository distribution and any app release plan are compatible with this license.

13. Hike, Ride & Sight bundled theme non-commercial license review
   - Status: `PENDING`
   - Notes:
     - Hike, Ride & Sight snapshot is embedded in `app/src/main/assets/theme/hike-ride-sight`.
     - The observed XML header declares CC BY-NC-SA 3.0.
     - Confirm whether public repository distribution and any app release plan are compatible with this license.

## Public repository and release gate

For a public repository or public app release, resolve all `BLOCKER` items.
Treat unresolved bundled asset redistribution/license `PENDING` items as
blocking unless the affected assets are removed from the public branch or the
residual risk is explicitly accepted. Publishing this repository publicly also
publishes the bundled third-party assets.

Current gate result:
- `DO NOT MAKE PUBLIC YET` unless the remaining pending bundled asset redistribution/license items are resolved or the affected assets are removed from the public branch.
- OpenAndroMaps/Elevate permission and source readme/license evidence are no longer blockers based on the project owner's confirmation and the checked-in source fallback readme.
