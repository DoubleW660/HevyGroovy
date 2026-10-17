# Routine sorting, progress, and dragging

The Workout tab defaults to **Oldest completed first**. Never-completed routines
come first, followed by each routine's most recent finished workout from oldest
to newest. **Newest completed first** and **Name A–Z** are also available. Ties
use name and ID for a stable order. The sort choice survives screen recreation.
Each routine card displays its last completed date; active sessions do not affect it.

Opening a routine shows its progress graph above the editor. Choose **Volume**,
**Reps**, or **Duration**. Each point represents one finished workout from that
routine's latest 12 completions, ordered chronologically by completion time and ID.
Tap a point or use Previous/Next to inspect its total and date. Never-completed
routines show an empty state. The graph is only inside routine details.

Volume uses the profile's LB/KG unit and each exercise's unilateral multiplier.
Reps remain entered counts, and duration is elapsed workout time in minutes.
Only completed sets contribute to volume/reps. Deleted workouts disappear from
both the graph and the routine's completion date. Routine IDs identify history;
renaming a routine does not detach its past workouts.

Completion dates come from one user-scoped aggregate across all finished history,
independently of the recent-workout list or pagination. The graph uses a bounded,
routine-scoped aggregate query through the existing history service/repository.
Reads remain on the database worker; no schema change or history rewrite is needed.

In active workouts and routine editors, hold an exercise header to drag. Cards
keep their layout during the gesture. A floating title preserves the exact grab
point, while a thin insertion line marks the destination. Measured card bounds
determine the drop position; moving near an edge scrolls the list. The display
changes order on drop, then the service saves it once. A failed save restores the
stored order and shows the existing error message. Cancellation leaves order alone.
Inline set drafts survive dragging. Accessibility Move up/Move down actions remain
available, including in routine editors; archived routines cannot be reordered.

## Verification

On October 17, 2026, all 242 JVM tests, 97 isolated phone tests, Android lint,
and debug/QA builds passed on the Samsung S23 Ultra (Android 16). Touch tests
verify the preview stays within 2 pixels of the original grab point on hold and
after movement, in both editors. They also cover cancellation, insertion markers,
edge scrolling, saved order, and preservation of logged sets. Routine tests cover
all-history sorting, the 12-session bound, user scoping, deleted history, empty
states, metric selection, and LB/KG display.

The updated regular app was installed with `adb install -r`. Database snapshots
matched every existing table before and after: 29 workouts, 192 exercise rows,
384 sets, 48 exercises, four routines, 30 routine exercise rows, and all receipts.
The installed routine list and graph were reviewed on the phone. Local backups,
comparison scripts, UI snapshots, and the graph screenshot are under the ignored
`app/build/routine-checks` directory. Test and lint reports are under `app/build/reports`.
