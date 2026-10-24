# Exercise, timer, and CSV performance fixes

Opening an exercise now reads the selected exercise through the records gateway.
It does not refresh the library's exercise catalog, routine list, completion dates,
or selected routine history. Info editing uses the selected exercise snapshot and
can save before the library has loaded. Explicit library navigation still refreshes
its own data.

Rest-timer persistence uses a single background worker for reads, durable writes,
and alarm changes. Replacing a timer writes its new state once. Completion alerts
wait for a successful durable clear, and later skip, replacement, or workout
cancellation invalidates a queued alert. The alarm receiver remains alive through
its asynchronous work. Persistence failures do not leave receiver completion waiting.

The Hevy CSV parser caches date conversions within each import and groups rows by
their exact workout title and start instant before hashing. Receipt hashes retain
the existing SHA-256 format, with direct hexadecimal encoding. Validation still
checks every row, including a workout's noncontiguous rows and conflicting metadata.

Number fields also suppress duplicate keyboard text callbacks before their parent
recomposes. Selection changes do not trigger edits, and externally updated values
remain editable.

## Validation

The eight parser unit tests passed in a standalone local JUnit run. A desktop
Java 21 benchmark used 1,000 workouts / 20,000 sets (1.60 MB), three 2,000-set
warmups, and five alternating runs. Matching outputs had median parse times of
376.89 ms before and 62.09 ms after. These figures measure the parser on the
desktop, not full import or phone performance. Local benchmark artifacts are in
the ignored `app/build/parser-efficiency` directory.

Phone regression checks count library loads, edit Info without a preceding library
load, and pause timer storage to exercise command ordering, restoration, durable
completion, and cancellation races. On October 24, 2026, all 280 JVM unit tests and
39 selected phone tests passed. The debug build and Android lint also passed
(0 errors, 37 warnings). Phone checks used the separate QA app.

The tested APK was installed over the regular phone app and its SHA-256 matched
the installed APK. A fresh local backup and a comparison of every previous database
row verified preservation of 29 finished workouts, the active workout, and all
400 sets. The database remained at schema v9. Backups and verification results are
under the ignored `app/build/phone-update-checks/update-20261024T120410Z` directory.
