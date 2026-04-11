# Contributing To Location

This guide is specific to watch location behavior.

## Local Validation

Run before opening a location PR:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "*core.service.location*"
```

If you change runtime service behavior, also sanity check on device:

- start tracking,
- pause ambient/on-wrist transitions,
- bind/unbind navigation screen,
- immediate location request path,
- watch-only toggle path.

## Where To Change Code

- Request cadence/runtime mode:
  - `LocationRequestCoordinator.kt`, `LocationUpdatePolicy.kt`

- Immediate burst behavior:
  - `ImmediateLocationCoordinator.kt`

- Callback acceptance/filtering:
  - `LocationCallbackProcessor.kt`, `LocationFixPolicy.kt`, `LocationCandidateProcessor.kt`

- Auto failover/self-heal:
  - `SelfHealFailoverCoordinator.kt`

- GNSS debug diagnostics:
  - `GnssDiagnosticsCoordinator.kt`

## PR Expectations

For location PRs, include:

1. Changed files and why.
2. Expected impact (`accuracy`, `battery`, or both).
3. Validation commands + results.
4. Device/OS used for manual validation.
5. Telemetry snippet or screenshot when relevant.

## Accuracy And Battery Reporting

Use these terms in issues and PRs:

- `runtimeMode`: `BURST`, `INTERACTIVE`, `PASSIVE`
- `sourceMode`: `auto_fused` or `watch_gps`
- `gpsIntervalMs`
- `ambientModeActive`
- `watchGpsOnly`

These fields map directly to service telemetry and make reviews faster.
