# Personal exercise and routine import

The bundled import uses exercise names from `workout_data.csv` for workouts dated
**August 1, 2026 onward**. The CSV is treated strictly as data. Its earlier sessions
and completed workout/set history are not imported.

## Included data

- 48 distinct exercise names, preserving the exported names.
- 34 verified matches/aliases with instructions and muscle data from Free Exercise DB.
- 14 custom, ambiguous, or differing variants with an editable name and reviewed
  primary muscle, without attaching instructions for a different movement.
- African Curls remains custom, as requested; its description is empty for later editing.
- Four routines reconstructed from their latest logged sessions:

| Routine | Latest session | Exercises |
| --- | --- | --- |
| Lower 1 | October 14, 2026 | 8 |
| Lower 2 | October 11, 2026 | 7 |
| Upper 1 | October 12, 2026 | 8 |
| Upper 2 | October 9, 2026 | 7 |

The CSV exports workout logs rather than the original routine definitions. Imported
routines preserve exercise order. The export has no rest timers, so none are
invented. The current template model does not support planned sets, rep targets,
weights, or supersets. Completed sets are not used to invent those features.

Exercise guides are available in the library for verified descriptions. Image paths
are retained as source metadata in the asset; the app does not yet display photos.

## Safe persistence

Normal app startup imports the bundles on the existing database worker. Stable
catalog keys make the import repeatable. Existing normalized-title matches,
including archived items, are reused without overwriting their fields; duplicate
existing names resolve to the lowest ID without merging rows. Imported items retain
later edits and archive decisions. Routine mappings are scoped to the local user.
Exercises and routines import in one transaction. Existing workouts and history
remain untouched. Named test databases opt in explicitly.

Schema v5 adds exercise provenance, and v6 adds user-scoped routine provenance.
Fresh installs and existing v4/v5 databases converge on v6 without destructive resets.

## Source and reproduction

The reviewed mappings live in `data/exercise-matches.json`. Public exercise data is
from [Free Exercise DB](https://github.com/yuhonas/free-exercise-db), pinned to commit
`f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5`. Its Unlicense is included at
`app/src/main/assets/free-exercise-db-LICENSE.txt`. Broad source muscle labels are
mapped explicitly to this app's muscle groups. UTF-8 is preserved when generating assets.

Download that commit's `dist/exercises.json`, then run:

```powershell
.\scripts\build-personal-catalog.ps1 `
  -WorkoutCsv '<path to workout_data.csv>' `
  -SourceJson '<path to pinned exercises.json>' `
  -Since '2026-08-01'
```

The script writes `exercise_catalog.json` and `template_catalog.json` into
`app/src/main/assets`. It fails on unreviewed exercise names rather than guessing.
Keep the upstream license alongside regenerated assets.

## Record tracking

Personal records are now calculated from finished workouts stored in the app; see
[personal-records.md](personal-records.md) for categories and rules. The catalog
import does not create PRs or past workout history. Estimated 1RM remains deferred.

## Verification

Verified on the connected Samsung Galaxy S23 Ultra (SM-S918U), Android 16, on
October 16, 2026: 189 JVM tests and all 58 device tests passed; Android lint and
debug/QA builds passed. The device suite covers the exact 48-entry bundle, latest
routine order, custom African Curls, user scoping, v4/v5 migration, preserved edits
and archive state across reopening, duplicate prevention, and rollback on invalid
data or database failure. It also checks the scrolling exercise guide.

Reports: `app/build/reports/tests/testQaUnitTest`,
`app/build/reports/androidTests/connected/qa`, and
`app/build/reports/lint-results-qa.html`. Installation screenshots are kept locally
under `app/build/catalog-import-checks`.

The regular app was updated with `adb install -r` and opened successfully. Phone
screenshots confirmed African Curls, the imported guides, and all four routines.
Existing app data was preserved.
