# Phone performance protocol

The benchmark installs `com.example.hevygroovy.benchmark`. Its fixture database is
separate from the ordinary app. Only the benchmark variant exposes the fixture
setup activity. Do not seed, clear, or uninstall the ordinary app to run these checks.

Use an unlocked physical phone running Android 14 (API 34) or later. Record the phone
model and Android API from the benchmark JSON; record any missing details manually
beside the report. Keep the screen on, battery at a normal level, and temperature,
power connection, and other foreground workloads consistent across comparisons.
Do not use emulator timings as phone results or suppress benchmark validity errors.

## Workloads and budgets

Both fixtures include an editable active workout with Bench and one set. The small
fixture has 10 completed workouts, 200 catalog exercises, and 20 templates. The large
fixture has 1,000 completed workouts, 500 exercises, and 100 templates. Each history
workout has six exercises and four sets per exercise; each template has eight rows.

| Measurement | Timing boundary | Proposed p95 budget |
| --- | --- | ---: |
| Cold start until usable | Process launch through first fully drawn home content | 2,000 ms |
| Library load | ViewModel dispatch through rendered library content | 300 ms |
| Template open | ViewModel dispatch through rendered selected template | 300 ms |
| Set save | ViewModel dispatch through committed write, refresh, and rendered result | 200 ms |

Cold-start first-frame time is reported separately. Scrolling captures CPU frame
duration and frame overrun; the report includes frame-overrun percentiles when
present. A positive overrun means a missed deadline at the phone's current refresh
rate. Scrolling does not currently have an enforced project budget.

The app is non-debuggable and profileable, signed locally, and inherits the current
release configuration, which has minification disabled. `CompilationMode.None()`
measures without ahead-of-time compilation. These results describe this build and
device; future minification, baseline profiles, or different phones require new runs.

Fixture creation, navigation before each operation, and set editor text entry are
outside measurement. Operation duration comes from the application's async trace,
ending after the updated screen renders, rather than UI Automator polling time.
Trace counts must show exactly one matching operation in each measured iteration.

## Run and evaluate

Choose the phone serial with `adb devices -l`, then run from the repository root:

```powershell
$env:ANDROID_SERIAL = '<phone serial>'
.\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest
.\scripts\check-performance.ps1 -FailOnBudget
```

The default is 20 measured iterations per operation and fixture. The parser computes
median, maximum, and nearest-rank p95 directly from JSON `runs`: sort N samples and
choose position `ceil(0.95 * N)`, counting from one. With 20 iterations this is the
19th sorted value. These sample percentiles establish an initial baseline; they are
not statistical guarantees for every user or device.

JSON and per-iteration Perfetto files are retained under
`macrobenchmark/build/outputs/connected_android_test_additional_output/`.
The generated report is `docs/performance-results.md`, with relative raw JSON links
and device metadata. Archive the report, JSON, and traces together when sharing
results. Select a specific run/device directory if the output tree contains stale
or duplicate records; the parser refuses to mix duplicate fixture/test results.

```powershell
.\scripts\check-performance.ps1 -ResultsDirectory '<single run directory>' -OutputFile 'docs/performance-results.md' -MinimumSamples 30 -FailOnBudget
```

`-MinimumSamples` defaults to 20 and can raise the requirement. Missing measurements,
invalid samples, missing/invalid trace counts, and too few iterations always produce
an incomplete report and a failing exit. `-FailOnBudget` also fails when a complete
run exceeds a p95 budget. Without it, complete over-budget results are still labeled
as over budget in the report. `-NoReport` validates the JSON without writing Markdown.

A one-iteration harness check can confirm that fixtures, selectors, and traces work:

```powershell
.\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest '-Pandroid.testInstrumentationRunnerArguments.benchmarkIterations=1'
```

Its report must remain **incomplete**. A successful harness or functional test does
not demonstrate that the load-time budgets pass.

The setup follows Android's [Macrobenchmark guidance](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
and [metric definitions](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics).
