# Storage and update contracts

Keep one `SQLiteAppStorage` instance per application process. Construct it and call
its synchronous services on background workers. A service call must stay on the
same thread for the lifetime of its transaction. Never suspend or dispatch to a
different worker inside a transaction. Close storage only after workers finish.

File databases enable WAL. Mutations use IMMEDIATE (nonexclusive) transactions.
Multi-query reads use read-only snapshot transactions on Android 15/API 35 and
newer. Android 14/API 34 uses a nonexclusive transaction to preserve consistency;
that fallback still reserves the writer slot, so keep those reads short. History
summary pages and template lists are single SQL queries and need no explicit
transaction. History details and active-session recovery keep their snapshot.

## Android gateways and recovery

`AppContainer` wires `WorkoutGateway` and `LibraryGateway` to synchronous services
through one shared `AppWorker`. Storage opens lazily on its serial background
executor. Results return on the main executor as immutable snapshots.

A mutation callback reports success only after loading the resulting screen state.
An ordinary failure means the write did not complete; forms retain their drafts.
`CommittedRefreshException` means the write completed but the following read failed.
The ViewModel records the save, closes the submitted form, and blocks further writes
until a read-only refresh succeeds. Template creation carries its new ID through
this failure so recovery opens the saved template without creating another one.

Explicit library refreshes reload the catalog and template list. Opening a template
reuses those lists and reads its detail with two queries: the owned parent and one
join for all exercise rows and current names. The join preserves nullable rest
targets and orders by exercise position, then row ID. Selected template IDs and
unsaved template form fields live in `SavedStateHandle`; database objects and
screen snapshots are reloaded after process recreation.

## Partial updates

For optional RPE, set rest seconds, and exercise/template descriptions, not calling
the setter preserves the existing value. Calling the setter with null clears it.
A blank description is stored as an empty string after normalization. Required
numeric fields and enums retain their existing null-means-unchanged behavior.
An empty secondary-muscle list clears it; null on an update leaves it unchanged.
Creation normalizes a null secondary-muscle list to empty, rejects null elements,
and copies incoming lists before storing them.

History and active-session set responses contain immutable `ActiveSetEntry`
snapshots, never mutable persistence entities. Each set keeps its original unit;
aggregate weights and volumes use KG.

## Completion migration

Version 4 always gives new set rows `completed = 0`, including upgraded files.
For upgrades directly from v1/v2, sets in finished workouts retain their historical
recorded meaning. Sets in active workouts are marked completed only when reps > 0;
zero-rep legacy rows remain incomplete and can be explicitly confirmed by the user.
This is a conservative inference because those versions had no completion state.

For v3 files, preserve stored completion flags. The database cannot distinguish an
explicit user decision from a flag inferred by the old migration, so it must not
silently overwrite them. Rebuilding the set table fixes its default and preserves
IDs, relationships, constraints, and explicit completion state.
