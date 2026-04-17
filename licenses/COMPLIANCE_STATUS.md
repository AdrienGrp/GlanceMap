# Compliance Status

Last reviewed: 2026-03-18

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
   - Status: `PENDING`
   - Notes:
     - Written permission from Tobias for Elevate integration is still pending.
     - Track request/response date and approved scope in release notes.

8. Elevate referenced resource-license file (`read_me_elevate.txt`)
   - Status: `PENDING`
   - Notes:
     - Referenced by `app/src/main/assets/Elevate.xml`.
     - Not currently present in bundled assets.

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

## Public repository and release gate

For a public repository or public app release, resolve all `BLOCKER` items and
explicitly accept residual risk for `PENDING` items. Publishing this repository
publicly also publishes the bundled third-party assets.

Current gate result:
- `DO NOT MAKE PUBLIC YET` unless pending bundled asset redistribution items are resolved or the affected assets are removed from the public branch.
