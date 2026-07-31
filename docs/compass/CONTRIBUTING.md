# Contributing To Compass

This guide is specific to watch compass behavior.

## Local Validation

Run before opening a compass PR:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "*domain.sensors*"
```

If you change navigation integration around compass start/stop behavior, also run:

```bash
./gradlew :app:testDebugUnitTest --tests "*presentation.features.navigate*"
```

## Manual Device Checklist

Sanity check on watch:

- navigation screen open/close lifecycle,
- `COMPASS_FOLLOW` vs `NORTH_UP_FOLLOW` mode transitions,
- ambient on/off transitions,
- offline mode transitions,
- heading source mode switch (`AUTO`, `TYPE_HEADING`, `ROTATION_VECTOR`, `MAGNETOMETER`),
- north reference switch (`TRUE`, `MAGNETIC`),
- recalibration trigger behavior.

## Where To Change Code

- Sensor pipeline, smoothing, quality, declination:
  - `CompassManager.kt`

- Compass lifecycle and low-power orchestration in navigation:
  - `NavigateCompassEffects.kt`

- User settings and recalibration entry points:
  - `CompassSettingsScreen.kt`, `CompassRecalibrationDialog.kt`

- ViewModel bridge:
  - `CompassViewModel.kt`

## PR Expectations

For compass PRs, include:

1. Changed files and why.
2. Expected impact (`heading stability`, `responsiveness`, `battery`, or combinations).
3. Validation commands + results.
4. Device model + Wear OS version used for manual checks.
5. `CompassTelemetry` snippet when behavior changed.

## Suggested Compass Report Fields

Use these fields in issues/PRs:

- `requested` (requested source mode),
- `src` (active source),
- `ref` (north reference),
- `mode` (`HIGH` or `LOW` sensor rate),
- `acc` (combined heading accuracy),
- `magInterf` (magnetic interference flag),
- nav mode (`COMPASS_FOLLOW`, `NORTH_UP_FOLLOW`, `PANNING`).
