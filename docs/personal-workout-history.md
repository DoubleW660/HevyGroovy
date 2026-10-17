# Personal workout history import

The CSV import includes 29 workouts from August 2 through October 14, 2026,
using the previously selected August 1 cutoff. It preserves 192 exercise occurrences,
384 sets, 3,423 reps, workout and exercise notes, exercise ordering, and workout dates.
All 48 exercise names resolve to the existing exercise catalog. Older sessions remain excluded.

Loads come from the export's `weight_lbs` column and remain LB. Eight blank loads
remain null in the bundled source and become zero additional recorded load in the
current set model; no bodyweight is invented. Five zero-rep sets are retained.
The 247 normal, 89 failure, 34 warm-up, and 14 drop sets preserve their types.
Assisted exercise loads stay exactly as exported; record calculations currently
describe logged load and do not invert assistance into resistance.

The export's timestamps have no UTC offsets. Conversion pins them to
America/New_York before generating epoch milliseconds, independently of the phone's
current timezone. The selected sessions contain no timed/distance sets, supersets,
RPE values, or rest timers; unsupported nonblank fields fail conversion.

## Persistence

Normal app startup imports history on the existing database worker after exercise
and routine seeding. Historical workouts are inserted as already finished, without
starting, finishing, or replacing the user's active workout. Routine links use the
user's imported routine mappings; exercise names remain historical snapshots.

Schema v7 adds a user-scoped receipt for each stable source session key. Reopening
or reinstalling an update skips existing receipts and preserves later edits. A
deleted imported workout leaves a receipt with a null workout ID, preventing its
unexpected restoration. The full payload is validated before writing, and all
pending workouts, children, and receipts are committed in one transaction.

Finished workouts can be removed through **History → workout details → Delete
workout**. Confirmation is required. Deletion removes the workout, exercise rows,
and sets atomically; it does not delete exercises, routines, or an active session.
Records, previous performance, and visible history are refreshed after success.
Volume totals and estimated 1RM follow the profile unit; database calculations
remain normalized to kilograms.

Named test databases import history only with an explicit opt-in. Existing catalog
opt-in constructors keep their original behavior.

## Reproduction

```powershell
.\scripts\build-personal-history.ps1 `
  -WorkoutCsv '<path to workout_data.csv>' `
  -Since '2026-08-01'
```

The script writes `app/src/main/assets/workout_history.json`. It rejects duplicate
source rows, inconsistent metadata, missing exercise/routine mappings, invalid
numbers, and ambiguous source timestamps. Session keys use the original start
timestamp and title, rather than a changing file hash or end time.

Import tests cover the actual bundle, active-session preservation, repeated imports,
units and notes, user isolation, deletion receipts, rollback, and v6-to-v7 migration.
On October 17, 2026, all 221 JVM tests, 84 phone tests, and Android lint passed.
The regular app was updated on the Samsung S23 Ultra using `adb install -r`.
Direct verification of its migrated v7 database confirmed 29 imported workouts,
384 sets, and 3,423 reps, with both pre-existing workouts unchanged. Reopening
retained the same import totals. Local database backups and verification snapshots
are under the ignored `app/build/history-import-checks` directory.

The finished-workout deletion and profile-unit update also passed all 228 JVM
tests, 89 phone tests, and lint on October 17, 2026. Tests verify atomic rollback,
record and previous-performance fallback, imported deletion receipts, confirmation,
refreshing history/home, and live LB/KG totals and estimates. The update was installed
on the regular phone app; all 30 workouts present immediately before that update
were verified unchanged afterward. No personal workout was deleted for testing.
