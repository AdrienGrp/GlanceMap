# Compass Threshold Rationale

This file documents intent for important compass constants.

Source constants file:

- `app/src/main/java/com/glancemap/glancemapwearos/domain/sensors/CompassManager.kt`

## How To Use This File

- When you change a threshold, update the matching row.
- Include expected effect on both heading quality and battery.
- Reference measurement context (device model, environment, movement pattern).

## Key Thresholds

| Constant | Current value | Purpose | Heading impact | Battery impact |
|---|---:|---|---|---|
| `MODERATE_TURN_RATE_DEG_PER_SEC` | `25 deg/s` | Detect moderate turn behavior | Improves follow responsiveness in turns | Can keep high sampling during motion |
| `FAST_TURN_RATE_DEG_PER_SEC` | `72 deg/s` | Detect fast turn behavior | Allows faster heading catch-up | More processing during sharp movement |
| `HEADING_RELOCK_WINDOW_MS` | `1200 ms` | Grace window after sensor re-register | Reduces restart flip/jump artifacts | Neutral |
| `HEADING_LARGE_JUMP_REJECT_DEG` | `120 deg` | Reject implausible one-shot heading jumps | Reduces sudden heading spikes | Neutral |
| `HEADING_LARGE_JUMP_CONFIRM_WINDOW_MS` | `350 ms` | Confirm large jump with second coherent sample | Balances jump rejection vs recovery speed | Neutral |
| `HEADING_NOISE_GOOD_DEG` | `3.0 deg` | High-quality noise bound | Stable heading confidence | Neutral |
| `HEADING_NOISE_IMPROVING_DEG` | `5.4 deg` | Medium-quality noise bound | Avoids over-reporting high confidence | Neutral |
| `HEADING_NOISE_POOR_DEG` | `8.8 deg` | Low-quality noise bound | Flags unstable heading sooner | Neutral |
| `ADAPTIVE_RATE_HIGH_TURN_RATE_DEG_PER_SEC` | `30 deg/s` | Trigger to stay/return to high sensor rate | Better behavior while turning | Higher sensor cost while active |
| `ADAPTIVE_RATE_STABLE_TO_LOW_MS` | `4000 ms` | Time in stable state before dropping to low rate | Can slightly delay low-power transition | Reduces sustained sensor drain once stable |
| `MAG_FIELD_MIN_VALID_UT` | `15 uT` | Lower bound for plausible magnetic field | Detects abnormal environment | Neutral |
| `MAG_FIELD_MAX_VALID_UT` | `85 uT` | Upper bound for plausible magnetic field | Detects interference/saturation | Neutral |
| `MAG_FIELD_SPIKE_THRESHOLD_UT` | `18 uT` | Spike detector for sudden interference | Captures abrupt disturbances | Neutral |
| `MAG_INTERFERENCE_HOLD_MS` | `3000 ms` | Hold interference state after trigger | Avoids rapid quality flapping | Neutral |

## Change Template

When adjusting a threshold, add this block to PR description:

```text
Threshold changed:
- Name:
- Old -> New:
- Why:
- Expected heading impact:
- Expected battery impact:
- Validation:
```
