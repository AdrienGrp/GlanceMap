# Compass Module Architecture

This document is the contributor entry point for watch compass behavior.

## Scope

Main source roots:

- `app/src/main/java/com/glancemap/glancemapwearos/domain/sensors`
- `app/src/main/java/com/glancemap/glancemapwearos/presentation/features/navigate/effects/NavigateCompassEffects.kt`
- `app/src/main/java/com/glancemap/glancemapwearos/presentation/features/settings/CompassSettingsScreen.kt`

This module owns:

- heading source selection (`TYPE_HEADING`, `ROTATION_VECTOR`, `MAGNETOMETER` fallback),
- north reference handling (`TRUE` vs `MAGNETIC`),
- heading smoothing/jump guard/accuracy inference,
- magnetic interference detection,
- low-power cadence coordination from navigation lifecycle.

## Runtime Pipeline

1. `NavigateCompassEffects` starts/stops compass based on lifecycle, ambient state, and nav mode.
2. `CompassViewModel` forwards calls and exposes state flows from `CompassManager`.
3. `CompassManager` resolves sensor pipeline and registers listeners at current rate mode.
4. Sensor callbacks produce raw azimuth/heading, then apply display rotation + north reference handling.
5. Smoothing and quality logic computes final heading, accuracy, source status, and interference state.
6. Navigation UI consumes heading and quality state to rotate map/cone behavior.

## Ownership

- `CompassManager.kt`
  - Sensor registration, source pipeline resolution, declination handling, smoothing, diagnostics.

- `CompassViewModel.kt`
  - UI bridge to manager start/stop/settings actions.

- `NavigateCompassEffects.kt`
  - Compose lifecycle wiring and low-power mode transitions.

- `CompassSettingsScreen.kt` and `CompassRecalibrationDialog.kt`
  - User-facing compass configuration and recalibration trigger.

- `CompassHeadingSourceMode.kt` and `NorthReferenceMode.kt`
  - Configuration enums shared by settings, runtime, and diagnostics.

## Guardrails

- Keep Android sensor API handling inside `CompassManager.kt`.
- Keep Compose lifecycle side effects in `NavigateCompassEffects.kt`.
- Keep pure math helpers in testable `internal` functions.
- Any change affecting heading stability or power must include before/after evidence in PR.

## Tests

Current compass unit tests live under:

- `app/src/test/java/com/glancemap/glancemapwearos/domain/sensors`

When adjusting heading math or thresholds, update/add tests in:

- `CompassManagerMathTest.kt`
