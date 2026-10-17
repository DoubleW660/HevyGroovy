# Personal records

Records are tracked per local user and exercise across finished workouts.

| Record | Definition |
| --- | --- |
| Heaviest weight | Largest logged load in one eligible completed set, with its reps and original unit. |
| Most reps in a set | Largest rep count in one eligible completed set, with its logged load. |
| Best set volume | Largest weight × reps in one eligible completed set. |
| Most volume in a workout | Largest sum of weight × reps for that exercise in one workout. Repeated exercise rows are combined. |

Eligible sets are completed NORMAL, FAILURE, or DROP sets with positive reps.
Warm-ups and unfinished sets are excluded. Zero-load bodyweight sets can establish
rep records; zero weight/volume does not produce a weight/volume record.

Weight comparisons use kilograms internally (`lb × 0.45359237`). The UI preserves
original set units. Volume records display in the profile's LB/KG preference,
converted from normalized kg × reps without changing saved performances. Improvements must
exceed the previous value by more than 0.000001; ties do not earn new awards.
The first eligible performance establishes initial records.

## During and after training

A successful set save/completion that improves a record shows an in-app notice.
View opens the achievement details. The exercise card also offers its provisional
records. Loading, rotation, and rechecking an unchanged set do not repeat a notice.
Higher performances can produce another notice in the same workout.

Active achievements are provisional until finishing. Unchecking, editing down,
or removing a set updates them. Discarding a workout does not replace saved bests.
Finished workout details show records earned at that point in time, compared with
earlier completed workouts by completion time and workout ID. Future performances
do not erase those historical achievements.

Saved all-time bests are available under **Exercises → Records**. Finished workout
and set rows persist the underlying performances; record queries derive the bests
from that durable history. This avoids a second stored counter drifting from the
workout data. Equal winners resolve deterministically to the earliest performance.
SQL aggregates only requested exercises and batches large ID lists.

Deleting a finished workout recalculates saved bests and estimated 1RM from the
remaining history. The next qualifying best takes its place. Historical awards
and previous-performance reads likewise use the remaining finished workouts.

These are records of the logged load. The app does not currently distinguish
external load from assistance or total body weight. Estimated one-rep max is a
separate metric in the active workout, history, and exercise record view; see
[the calculation and previous-performance rules](strength-estimates.md).
Records for each weight/rep combination remain a separate future feature.

The [workout history import](personal-workout-history.md) adds August-onward CSV
performances as record baselines. Historical awards are calculated against earlier
finished workouts, so importing history does not produce live record notifications.

## Verification

Service tests cover eligibility, units, ties, chronology, duplicate exercise rows,
ownership, and initial records. ViewModel tests cover stale callbacks, retry, and
announcement suppression. Isolated phone tests verify real SQLite calculations,
saved bests after close/reopen, discard/undo behavior, and the complete notification
→ finish → history → exercise-records flow. The initial implementation passed
211 unit tests, 66 isolated phone tests on the Samsung S23 Ultra (Android 16),
and QA lint. The provisional and saved record screens were reviewed on that
phone, and the verified build was installed without clearing the regular app's data.
