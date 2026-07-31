## Summary

- What changed:
- Why:

## Location Impact

- Area:
  - [ ] request cadence
  - [ ] immediate burst
  - [ ] callback acceptance/filtering
  - [ ] failover/self-heal
  - [ ] telemetry/diagnostics
  - [ ] other
- Expected impact:
  - [ ] accuracy
  - [ ] battery
  - [ ] both

## Compass Impact

- Area:
  - [ ] sensor source selection
  - [ ] heading smoothing/filtering
  - [ ] north reference / declination
  - [ ] magnetic interference handling
  - [ ] navigation lifecycle / low-power mode
  - [ ] telemetry/diagnostics
  - [ ] other
- Expected impact:
  - [ ] heading stability
  - [ ] responsiveness
  - [ ] battery
  - [ ] multiple

## Validation

Commands run:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "*core.service.location*"
./gradlew :app:testDebugUnitTest --tests "*domain.sensors*"
```

Manual validation:

- Device:
- Wear OS version:
- Scenario:

## Evidence (Required For Accuracy/Battery Changes)

- Before:
- After:
- Data source (logs/screenshots/track comparison):

## Docs

- [ ] Updated location docs if behavior/structure changed (`docs/location/*` or `docs/architecture/*`).
- [ ] Updated compass docs if behavior/structure changed (`docs/compass/*` or `docs/architecture/*`).
