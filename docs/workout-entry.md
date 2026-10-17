# Workout entry

The active workout uses an inline table: set type, previous performance, weight,
reps, and a completion checkbox. A normal working set shows its number; warm-ups
show W and drop sets show D. Failure sets remain available as F. Advanced options
retain optional RPE, rest time, and set deletion. Units can be changed beside the weight.

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

On October 17, 2026, all 221 JVM tests, 84 phone tests, and Android lint passed.
Phone tests include actual touch dragging, persisted order, focused draft preservation,
inline completion, cross-routine previous performance, cancellation, and invalid or
failed drafts blocking Finish. Native list scrolling is disabled during a held reorder
so it cannot consume the drag; programmatic edge scrolling remains available.
The verified update was installed on the regular Samsung S23 Ultra app.
