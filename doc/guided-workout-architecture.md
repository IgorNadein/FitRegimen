# Guided workout architecture

FitRegimen uses four deliberately different domain concepts. The names shown to the
user may stay shorter, but the data model must not collapse them into one table.

```text
Training plan                         Runtime and history

TrainingProgram                       TrainingSession
  ProgramDay (workout template)  ───▶   SessionExercise (snapshot)
    ProgramExercise                    SessionSet (snapshot + result)
      ProgramSet                                │
      Exercise definition                       ▼
                                         Workout (sensor recording)
                                            route / HR / reps
```

## Responsibilities

- `TrainingProgram` schedules workout templates over calendar days.
- `ProgramDay` is the workout template currently owned by a program. It contains an
  ordered prescription, but never runtime state.
- `Exercise` is a reusable catalog definition: name, instructions, tips, metric kind,
  optional media and an optional compatible legacy recording type.
- `TrainingSession` is one concrete attempt. It owns preparation, the current set,
  rest, pause and completion state and remains valid if its source plan is edited or
  deleted.
- `Workout` remains the proven GPS/indoor telemetry record. In the new model it is a
  recorded segment attached to a session set, not a second kind of user workout.

Starting a session copies the relevant plan and exercise fields into immutable
`SessionExercise` and `SessionSet` snapshots. Plan edits therefore affect the next
session only. Actual values are written to the snapshot rows as the session runs.

## Execution policy

“Manual / timer / tracker” is useful UI language but is not expressive enough for
storage. Measurement and completion are independent axes:

```text
MeasurementSource = USER_INPUT | CLOCK | RECORDER
CompletionRule     = USER_CONFIRM | DURATION_REACHED | METRIC_TARGET_REACHED
```

Examples:

| Prescription | Source | Completion |
| --- | --- | --- |
| 10 push-ups, confirm manually | USER_INPUT | USER_CONFIRM |
| 40-second plank | CLOCK | DURATION_REACHED |
| Run 5 km with GPS | RECORDER | METRIC_TARGET_REACHED |
| Run for 20 minutes while recording GPS | RECORDER | DURATION_REACHED |
| Sensor-counted repetitions with a manual fallback | RECORDER | METRIC_TARGET_REACHED |

The plan stores desired capabilities, not a device address. At runtime a tracker
provider is selected after checking availability. Manual input remains a fallback;
changing a distance target into a time target always requires explicit consent.

## State machine

```text
OVERVIEW
   │ start
   ▼
PREPARING ─────▶ ACTIVE_SET ─────▶ REST ─────▶ PREPARING
                     │                            │
                     ├─ manual confirmation      └─ next set/exercise
                     ├─ timer deadline
                     └─ correlated tracker result

last set ───────────────────────────────────────▶ COMPLETE

Any running phase ⇄ PAUSED
Any non-complete phase ─▶ ABORTED (after confirmation)
```

Deadlines are persisted as absolute wall-clock instants for process recovery, while
an in-process monotonic clock drives animation and ticks. A transition is accepted
only when the current row is still pending, so duplicate timer callbacks and stale
recorder events are idempotent.

Rest is explicit:

- `restSeconds` applies between sets of one exercise;
- `transitionSeconds` applies after the last set before the next exercise;
- the final set completes the session without a fictitious rest step.

## Tracker boundary

The guided engine never learns sensor-specific details. It requests a capability and
receives correlated metrics/results through an adapter:

```kotlin
interface ExerciseTrackerProvider {
    val capabilities: Set<TrackerCapability>
    suspend fun start(request: TrackingRequest): TrackerHandle
}

interface TrackerHandle {
    val snapshots: Flow<TrackerSnapshot>
    suspend fun pause()
    suspend fun resume()
    suspend fun finish(): TrackerResult
    suspend fun cancel()
}
```

The first adapter wraps the existing `RecorderService`; every callback is correlated
by its exact `workoutId` and session-set ID. Health Connect is the interoperability
and post-session sync boundary. Wear OS Health Services or BLE FTMS can later provide
live tracker adapters without changing the session reducer.

## Media

Exercise media is metadata plus a file in app-private storage, not a Room BLOB. The
target model supports ordered image/GIF/video items with MIME type, content hash,
local path and optional remote URL. Backups include the referenced files by content
hash. A missing media file never blocks a workout; the UI falls back to instructions.

## Compatibility rules

- Existing `Workout` rows and samples are never rewritten or duplicated.
- Existing program completions and set logs remain readable during the transition.
- A guided session can link several sensor `Workout` segments.
- A standalone legacy activity can later be surfaced as a one-exercise session.
- Deleting a plan must set source references to null; session snapshots and results
  remain intact.
- Multiple sessions for the same plan and calendar date are valid.

## Delivery order

1. Add durable session snapshots and a migration without deleting old history.
2. Ship the manual and clock-driven runner, including persisted rest and recovery.
3. Return the exact legacy recorder result to a session set.
4. Wrap the recorder behind the tracker boundary and embed live metrics.
5. Add Wear OS/Health Connect providers after capability and permission UX is stable.
6. Move backup/export to UUID links and include session snapshots and media files.
