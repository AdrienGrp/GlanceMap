# GlanceMap

GlanceMap is a Wear OS navigation app with an Android companion app for file transfer and sync.

## Public Repository Status

This repository is close to public-ready, but the bundled French Kiss theme
still needs redistribution clearance or removal from the public branch. Check
`licenses/COMPLIANCE_STATUS.md` and `docs/PUBLIC_REPO_CHECKLIST.md` before
changing GitHub visibility.

## Modules

- `:app`
  - Wear OS application.
  - Main features: navigation, map rendering, GPX handling, watch-side transfer receiver.

- `:glancemapcompanionapp`
  - Android phone companion app.
  - Main features: file picking, transfer orchestration, watch discovery, transfer service.

- `:transfercontract`
  - Shared Kotlin module containing transfer protocol constants used by both apps.

## Requirements

- JDK 17
- Android SDK with `compileSdk = 36`
- Gradle Wrapper (`./gradlew`)

## Build

Build watch app Kotlin:

```bash
./gradlew :app:compileDebugKotlin
```

Build companion app Kotlin:

```bash
./gradlew :glancemapcompanionapp:compileDebugKotlin
```

Build both:

```bash
./gradlew :app:compileDebugKotlin :glancemapcompanionapp:compileDebugKotlin
```

Run the same quality gate used by CI:

```bash
./gradlew ktlintCheck detekt :app:compileDebugKotlin :glancemapcompanionapp:compileDebugKotlin :app:testDebugUnitTest :glancemapcompanionapp:testDebugUnitTest :app:lintDebug :glancemapcompanionapp:lintDebug
```

## Key Architecture Notes

- The watch and companion use the same `applicationId` (`com.glancemap.glancemapwearos`) and must be signed by the same certificate for Wear Data Layer interoperability.
- Play `versionCode` values are centralized in `gradle.properties` and use separate phone/watch artifact suffixes under the same app version.
- The Wear OS app is marked non-standalone because the companion is required for the core first-run map/data transfer path.
- Transfer protocol paths/capabilities are centralized in `:transfercontract`:
  - `transfercontract/src/main/kotlin/com/glancemap/shared/transfer/TransferDataLayerContract.kt`
- Watch transfer/location services live in:
  - `app/src/main/java/com/glancemap/glancemapwearos/core/service`

## Release Signing

Do not commit signing credentials, keystores, signed bundles, or APKs. Release
builds can use Android Studio's Generate Signed Bundle / APK flow, private
Gradle properties, or environment variables. The checked-in `gradle.properties`
contains only placeholder comments for signing values.

## Privacy Policy

The public privacy policy page is prepared for GitHub Pages at:

`https://adriengrp.github.io/GlanceMap/privacy-policy/`

Enable it from GitHub repository settings with source `Deploy from a branch`,
branch `main`, folder `/docs`.

## License And Third-Party Assets

The root `LICENSE` covers GlanceMap project code unless another file says
otherwise. Bundled map themes, icons, vendored code, and service/API notes are
tracked under `licenses/` and `third_party/`. Review
`licenses/COMPLIANCE_STATUS.md` before any public repository or app release.

## Documentation

- Architecture index: `docs/architecture/README.md`
- Wear navigation: `docs/architecture/wear-navigation.md`
- Wear location service: `docs/architecture/wear-location-service.md`
- Wear transfer service: `docs/architecture/wear-transfer-service.md`
- Location module architecture: `docs/location/ARCHITECTURE.md`
- Location contribution guide: `docs/location/CONTRIBUTING.md`
- Location threshold rationale: `docs/location/THRESHOLDS.md`
- Compass module architecture: `docs/compass/ARCHITECTURE.md`
- Compass contribution guide: `docs/compass/CONTRIBUTING.md`
- Compass threshold rationale: `docs/compass/THRESHOLDS.md`
- Public repository checklist: `docs/PUBLIC_REPO_CHECKLIST.md`
- Google Play/privacy checklist: `docs/GOOGLE_PLAY_RELEASE_PRIVACY_CHECKLIST.md`
- Public privacy policy page source: `docs/privacy-policy.md`
- License and attribution notes: `licenses/README.md`

## Contributing

See `CONTRIBUTING.md` for setup, workflow, and PR expectations.
