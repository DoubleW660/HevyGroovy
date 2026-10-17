# Unilateral and bilateral exercises

Choose **Exercises → Edit exercise → Movement type** (also available for new exercises).
The setting belongs to the exercise and applies across all routines and existing history.

- **Bilateral:** volume = logged weight × reps.
- **Unilateral:** enter weight and reps **per side**; volume = logged weight × reps × 2.

For example, 17 reps at 140 lb on Single Leg Press gives **4,760 lb × reps**.
The exercise is preset to unilateral on fresh installs and during the v7 → v8 upgrade,
including a renamed entry with the same catalog mapping. Other exercises default to
bilateral until the user chooses otherwise. Future openings preserve the user's choice.

This changes volume totals in workout history, best set volume, and exercise workout
volume records. Changing the setting recalculates existing performances immediately;
logged sets are never rewritten or duplicated. Reps, set counts, heaviest weight,
previous-set values, and estimated 1RM remain the entered per-side values. Profile
LB/KG preferences still control aggregate display units.

History includes all completed sets; record eligibility still excludes warmups,
incomplete sets, and zero-rep sets. Duplicate exercise rows count their sets once each.
Archived exercises keep their setting and historical volume.

Schema v8 adds an `exercise.unilateral` boolean. Read queries join this current
exercise setting for both active and finished workouts; calculations remain normalized
to kilograms internally. File migration, service, SQLite, and UI tests cover the
multiplier, historical recalculation, reopening, user isolation, and unit conversion.

## Verification

On October 17, 2026, all 233 JVM tests, 93 isolated phone tests, Android lint,
and debug/QA builds passed on the connected Samsung S23 Ultra (Android 16).
The normal app was updated with `adb install -r` and migrated from v7 to v8.
Database snapshots verified all 29 workouts, 192 exercise occurrences, 384 sets,
four routines, and import/catalog receipts were unchanged. Existing exercise
metadata was preserved; Single Leg Press was marked unilateral. Its October 14
volume was verified as 4,760 lb × reps. Backups and comparison tools are under
the ignored `app/build/history-import-checks` directory.
