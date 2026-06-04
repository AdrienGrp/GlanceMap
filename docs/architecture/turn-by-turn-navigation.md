# GPX Turn-by-Turn Navigation Spike

## Summary

Turn-by-turn guidance is feasible from GPX alone, without requiring BRouter at guidance time. The first useful version can be a GPX-following mode:

- the user taps a play button for a GPX in the GPX screen,
- the app opens the navigate screen,
- a compact guidance popup appears over the map,
- swiping the popup up expands it into a whole-screen guidance view,
- if the user is away from the GPX start, the popup first points them toward the start with bearing and distance.

This can work for any GPX with enough points. It will not be as semantically precise as router-provided instructions because plain GPX files usually do not know road/path junctions, way names, or official maneuvers. But for hiking/riding GPX guidance on a watch, geometry-derived instructions are a reasonable first slice.

## Scope Boundary

Turn-by-turn guidance and trace recording should be separate features.

Turn-by-turn guidance:

- reads an existing GPX,
- derives or reads route instructions,
- follows live location against that route,
- shows a guidance popup on the navigate screen,
- does not write a new GPX track by itself.

Trace recording:

- records live location samples over time,
- writes a new GPX or activity trace,
- owns pause/resume/save/discard behavior,
- can run with or without guidance later, but is not required for guidance.

The two features can share location updates and formatting helpers, but they should not share session state. A future version could allow both at once, for example "follow GPX A while recording trace B", but the first implementation should keep their UI controls, persistence, and lifecycle independent.

## GPX-First User Flow

1. User opens `GpxScreen`.
2. Each GPX row has a compact play button next to the existing active-track switch/actions.
3. Tapping play starts guidance for that GPX and navigates to `WatchRoutes.NAVIGATE`.
4. `NavigateScreen` shows a guidance popup.
5. If the user is not close to the start point, the popup shows:
   - direction arrow to GPX start,
   - distance to start,
   - title of the selected GPX,
   - a stop/dismiss action.
6. Once the user is close enough to the start, guidance switches to route-following mode.
7. The compact popup shows the next turn, distance to the turn, route progress, and an expand affordance.
8. Swiping up expands the popup into a full-screen guidance surface with more detail.

## Current Routing Path

The watch-side planner is `BRouterRoutePlanner`. It creates BRouter `RoutingContext` and `RoutingEngine` instances, then converts the resulting `OsmTrack` into `RoutePlannerOutput`.

Current output keeps:

- GPX bytes,
- title and file name,
- geometry points with optional elevation.

Current output drops:

- BRouter voice hints,
- per-instruction command codes,
- distance to next maneuver,
- track index offsets for each maneuver.

This means route creation can produce turn metadata, but the app does not yet keep it in a first-class Kotlin model.

For the GPX-first version, this BRouter path is optional. BRouter remains useful later for GPX files created by route tools, but the first implementation can derive turns from GPX geometry and live location.

## GPX-Derived Turns

A GPX-derived guidance engine should convert a polyline into instruction candidates by looking at shape changes:

- simplify/resample the GPX enough to avoid noisy micro-turns,
- compute incoming and outgoing bearings at each candidate point,
- create an instruction when bearing delta exceeds thresholds,
- merge nearby candidates into one instruction,
- suppress turns on short wiggles,
- classify commands by angle.

Suggested first thresholds:

- slight turn: 25 to 45 degrees,
- normal turn: 45 to 110 degrees,
- sharp turn: more than 110 degrees,
- keep/continue: only when useful for popup continuity,
- minimum spacing between instructions: 25 to 50 meters,
- start proximity threshold: 25 to 40 meters,
- off-route threshold: 50 to 80 meters, with hysteresis.

This will produce route-shape guidance, not road-aware navigation. That is acceptable for a first GPX-following mode if UI language stays simple: "left", "sharp right", "continue", "to start", instead of promising named road/path instructions.

## BRouter Turn Hint Support

BRouter already has turn instruction support:

- `RoutingContext.turnInstructionMode` enables hint generation.
- `OsmTrack.processVoiceHints()` builds a `VoiceHintList`.
- `VoiceHint` exposes public string helpers such as `getCommandString()` and `getMessageString()`.
- `FormatGpx` can serialize hints into GPX using modes including comment style, OsmAnd style, Locus style, Cruiser style, and BRouter track-point extension style.

The cleanest app-side options are:

1. Add a narrow public adapter in the vendored BRouter module that exposes immutable hint snapshots.
2. Set `turnInstructionMode` and parse the generated GPX hints back into the app model.

Option 1 is better for live guidance because it avoids XML round-tripping and preserves typed values while the route is being created. Option 2 is useful for persistence and interoperability because saved GPX files can carry hints.

## Proposed Domain Model

Add GPX guidance models under a navigation/guidance package, not under BRouter routing:

```kotlin
data class RouteInstruction(
    val command: RouteInstructionCommand,
    val message: String,
    val latLong: LatLong,
    val trackPointIndex: Int,
    val distanceFromStartMeters: Double,
    val distanceToNextMeters: Int?,
    val turnAngleDegrees: Float?,
    val source: RouteInstructionSource,
)

enum class RouteInstructionCommand {
    CONTINUE,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    KEEP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    KEEP_RIGHT,
    U_TURN,
    ROUNDABOUT,
    EXIT_LEFT,
    EXIT_RIGHT,
    OFF_ROUTE,
    FINISH,
    UNKNOWN,
}

enum class RouteInstructionSource {
    GPX_GEOMETRY,
    BROUTER_HINT,
}
```

## Live Guidance Engine

Add a small state engine outside Compose, likely under:

`app/src/main/java/com/glancemap/glancemapwearos/presentation/features/navigate/guidance`

Responsibilities:

- hold the selected GPX guidance session,
- derive instruction candidates from the GPX geometry,
- project the current location onto the active GPX route,
- compute distance remaining to the next instruction,
- advance to the next instruction once the user passes a maneuver,
- switch from "go to start" to "follow route" when close enough,
- detect meaningful off-route drift,
- expose a compact `TurnByTurnGuidanceState` to `NavigateScreen`.
- avoid writing recorded samples or owning trace-recording state.

The route-progress math can reuse nearby GPX inspection helpers because the app already projects taps and positions against track geometry.

Initial state shape:

```kotlin
data class TurnByTurnGuidanceState(
    val active: Boolean,
    val mode: GuidanceMode,
    val trackTitle: String?,
    val nextInstruction: RouteInstruction?,
    val distanceToInstructionMeters: Double?,
    val distanceToStartMeters: Double?,
    val bearingToStartDegrees: Float?,
    val distanceRemainingMeters: Double?,
    val offRoute: Boolean,
)

enum class GuidanceMode {
    TO_START,
    FOLLOW_ROUTE,
    FINISHED,
}
```

## UI Integration

Start with a minimal Wear popup in `NavigateOverlaysLayer`:

- compact popup near the top or lower safe area of the map,
- command icon,
- distance to maneuver or distance to start,
- short text such as "Left" or "Keep right",
- progress/distance remaining,
- stop action,
- optional off-route warning.

Expanded state:

- triggered by swipe-up on the popup,
- fills the screen,
- shows large arrow/distance,
- shows GPX title,
- shows remaining distance/ETA if available,
- includes stop/close controls.

Avoid audio in the first pass. Haptic cues are a better first Wear fit and already match the app style.

## GPX Screen Entry Point

`GpxScreen` currently uses a `SwitchButton` per row to toggle active overlays. Add a compact play button next to each row when not in send/delete/rename mode.

On tap:

- load or reuse that GPX profile,
- start a guidance session in `GpxViewModel` or a dedicated `GuidanceViewModel`,
- optionally mark the GPX active so it is visible on the map,
- navigate to `WatchRoutes.NAVIGATE`.

A dedicated `GuidanceViewModel` is cleaner if this grows beyond a first pass, because the state belongs to navigation rather than the GPX list.

Do not reuse this play button as a recording control. Recording should have its own start/stop affordance when that feature is designed.

## Persistence

There are two levels, both optional for the first version:

1. Store the active guidance session in memory for the running app.
2. Later, remember "last guided GPX" or parse GPX extensions if present.

BRouter mode 9 remains attractive for routes created by route tools because it writes hints onto `trkpt` elements with `brouter:voicehint`, `desc`, and `sym`. The existing parser currently reads only `trkpt` position, elevation, and time, so it would need a small extension to capture hint tags.

## Suggested Phases

1. Build pure Kotlin GPX instruction derivation with tests.
2. Build pure Kotlin guidance progress state with tests, including "to start".
3. Add a guidance session state holder.
4. Add the GPX screen play button.
5. Add the compact navigate popup.
6. Add swipe-up full-screen expansion.
7. Add haptics and off-route warning.
8. Later, parse BRouter/GPX extension hints when present and prefer them over geometry-derived turns.

## Open Questions

- Should play automatically activate the GPX overlay, or can guidance run without marking the GPX active?
- Should guidance allow starting from the nearest point on the route when the user joins mid-route, or always lead to the official start first?
- Should reverse direction be offered if the user is closer to the end than the start?
- Should off-route handling be warning-only at first, or offer "guide me back to route" immediately?
- If trace recording is added later, should it be controlled from Navigate, GPX, or a separate activity screen?

## Recommendation

Implement the first version as GPX-backed guidance for any selected GPX. Derive simple turn instructions from geometry, start with a "to start" mode, and present it through a compact navigate popup that can expand full-screen. Add BRouter hint support later as an accuracy upgrade for routes created inside GlanceMap.
