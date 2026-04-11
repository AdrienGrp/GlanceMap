# GlanceMap

GlanceMap is a Wear OS navigation app with an Android companion app for file transfer and sync.

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

## Key Architecture Notes

- The watch and companion currently use the same `applicationId` (`com.glancemap.glancemapwearos`) for Data Layer interoperability.
- Transfer protocol paths/capabilities are centralized in `:transfercontract`:
  - `transfercontract/src/main/kotlin/com/glancemap/shared/transfer/TransferDataLayerContract.kt`
- Watch transfer/location services live in:
  - `app/src/main/java/com/glancemap/glancemapwearos/core/service`

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

## Contributing

See `CONTRIBUTING.md` for setup, workflow, and PR expectations.
