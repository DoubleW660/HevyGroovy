# Workout entry

The active workout uses an inline table: set type, previous performance, weight,
reps, and a completion checkbox. A normal working set shows its number; warm-ups
show W and drop sets show D. Failure sets remain available as F. Advanced options
retain optional RPE, rest time, unit changes, and set deletion.

Previous performance belongs to the exercise and local user across all routines,
using completed sets from the latest finished workout. Its original weight unit
is shown beside the reps. It is a reference, separate from the current input.
See [previous-performance and strength-estimate rules](strength-estimates.md).

Hold an exercise's title/drag handle to collapse the workout into exercise titles.
Drag to the desired position and release to save the order. Releasing without
moving leaves the order unchanged; cancelling the gesture restores the saved order.

Cancel workout opens a discard confirmation, including for an empty workout
started accidentally. Keep workout returns to training. Confirmed discard removes
the active session and its children, without adding a history entry or changing
saved records. Finishing still requires at least one completed set.

## Implementation checks

- Inline weight/reps and completion are saved together; core entry needs no dialog.
- Input drafts survive Compose state restoration.
- Previous performance is user-scoped, bounded, and loaded in batches.
- Drag completion saves through the existing ordered-exercise service.
- Existing empty-workout and finished-workout edit guards stay in the backend.
- Estimated 1RM uses Epley for eligible 1–10 rep sets and is labelled as an estimate.

Verification results will be recorded after integration and phone checks.
