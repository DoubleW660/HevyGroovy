# Physical-device performance checks

This test module installs `com.example.hevygroovy.benchmark`, a separate, non-debuggable
copy of the current release configuration. The normal installed app and its data are
not changed. Fixture initialization is exported only in the benchmark variant.

The two fixtures contain 10 and 1,000 completed workouts, plus an editable active
workout, a populated exercise library, and templates. Fixture seeding, editor input,
and navigation are excluded from the measurements. `CompilationMode.None()` reports
the current app without ahead-of-time compilation; it does not claim to model a
future app with baseline profiles or release minification.

Run on an unlocked physical Android 14+ phone with a normal battery level. Select a
single device by setting `ANDROID_SERIAL`. Keep other apps and thermal conditions
consistent when comparing runs.

```powershell
$env:ANDROID_SERIAL = '<phone serial>'
.\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest
```

The default is 20 measured iterations per test and fixture. For a quick harness check:

```powershell
.\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest '-Pandroid.testInstrumentationRunnerArguments.benchmarkIterations=1'
```

The tests collect cold-start first-frame and fully-drawn times, library load,
template opening, set saving through the rendered refresh, and library scrolling
frame timing. The operation traces begin in the ViewModel and end after rendering;
UI Automator polling overhead is excluded from those trace measurements.

JSON results and per-iteration Perfetto traces are copied under
`macrobenchmark/build/outputs/connected_android_test_additional_output/`.
JUnit reports are under `macrobenchmark/build/outputs/androidTest-results/`.

The initial project budgets are p95 cold start until usable <= 2,000 ms, library or
template load <= 300 ms, and set save <= 200 ms. Treat them as proposed budgets until
device measurements establish a baseline. Evaluate nearest-rank p95 over the raw
iteration values (20 iterations gives the 19th sorted value). Frame timing uses the
reported p95 frame overrun; values <= 0 ms mean those frames met the device deadline.
A passing functional test alone does not establish compliance with these budgets.

The harness follows the official [Macrobenchmark setup](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
and [metric guidance](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics).
