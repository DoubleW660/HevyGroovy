[CmdletBinding()]
param(
    [string]$ResultsDirectory = (Join-Path $PSScriptRoot '..\macrobenchmark\build\outputs\connected_android_test_additional_output'),
    [string]$OutputFile = (Join-Path $PSScriptRoot '..\docs\performance-results.md'),
    [ValidateRange(20, 100000)]
    [int]$MinimumSamples = 20,
    [switch]$FailOnBudget,
    [switch]$NoReport
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
$culture = [Globalization.CultureInfo]::InvariantCulture
$issues = New-Object 'System.Collections.Generic.List[string]'
$rows = New-Object 'System.Collections.Generic.List[object]'
$records = @{}
$sources = New-Object 'System.Collections.Generic.List[object]'

function Get-Property($Value, [string]$Name) {
    if ($null -eq $Value) { return $null }
    $property = $Value.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    # Keep nested arrays intact, including a sampled metric with only one iteration.
    return ,$property.Value
}

function Get-Numbers($Runs, [string]$Description, [switch]$AllowNegative) {
    foreach ($sample in @($Runs)) {
        if ($null -eq $sample) {
            $issues.Add("$Description contains a null sample.")
            continue
        }
        $number = 0.0
        if (-not [double]::TryParse([string]$sample, [Globalization.NumberStyles]::Float, $culture, [ref]$number) -or
            [double]::IsNaN($number) -or [double]::IsInfinity($number) -or
            (-not $AllowNegative -and $number -lt 0)) {
            $issues.Add("$Description contains an invalid sample: $sample.")
            continue
        }
        $number
    }
}

function Get-Statistics([double[]]$Samples) {
    if ($Samples.Count -eq 0) { return $null }
    $sorted = @($Samples | Sort-Object)
    $middle = [int][Math]::Floor($sorted.Count / 2)
    $median = $sorted[$middle]
    if ($sorted.Count % 2 -eq 0) {
        $median = ($sorted[$middle - 1] + $sorted[$middle]) / 2
    }
    [pscustomobject]@{
        N = $sorted.Count
        Median = $median
        P95 = $sorted[[int][Math]::Ceiling(0.95 * $sorted.Count) - 1]
        Max = $sorted[$sorted.Count - 1]
    }
}

function Format-Milliseconds($Value) {
    if ($null -eq $Value) { return '-' }
    return ([double]$Value).ToString('0.00', $culture)
}

function Add-MetricRow([int]$Fixture, [string]$Test, [string]$Label, [string]$Metric,
    $Budget, [string]$CountMetric = '') {
    $key = "$Fixture/$Test"
    $record = $null
    if ($records.ContainsKey($key) -and $records[$key].Count -eq 1) {
        $record = $records[$key][0]
    }
    $metricObject = Get-Property (Get-Property $record 'metrics') $Metric
    $rawRuns = Get-Property $metricObject 'runs'
    $samples = @()
    if ($null -ne $rawRuns) {
        $samples = @(Get-Numbers $rawRuns "$Fixture/$Test/$Metric")
    }
    $stats = Get-Statistics $samples
    $complete = $samples.Count -ge $MinimumSamples
    if (-not $complete) {
        $issues.Add("$Fixture/$Test/$Metric has $($samples.Count) measured samples; at least $MinimumSamples are required.")
    }
    $countsLabel = '-'
    if ($CountMetric) {
        $countObject = Get-Property (Get-Property $record 'metrics') $CountMetric
        $rawCounts = Get-Property $countObject 'runs'
        $counts = @()
        if ($null -ne $rawCounts) {
            $counts = @(Get-Numbers $rawCounts "$Fixture/$Test/$CountMetric")
        }
        $invalidCounts = @($counts | Where-Object { $_ -ne 1 })
        if ($counts.Count -ne $samples.Count -or $counts.Count -eq 0 -or $invalidCounts.Count -gt 0) {
            $complete = $false
            $countsLabel = 'INVALID'
            $issues.Add("$Fixture/$Test must contain exactly one traced operation per timing sample (metric $CountMetric).")
        } else {
            $countsLabel = "1 x $($counts.Count)"
        }
    }
    $status = 'INCOMPLETE'
    if ($complete) {
        if ($null -eq $Budget) { $status = 'INFO' }
        elseif ($stats.P95 -le $Budget) { $status = 'PASS' }
        else { $status = 'OVER BUDGET' }
    }
    $rows.Add([pscustomobject]@{
        Fixture = $Fixture; Label = $Label; Stats = $stats
        Budget = $Budget; Status = $status; Counts = $countsLabel
    })
}

$files = @()
if (Test-Path -LiteralPath $ResultsDirectory -PathType Container) {
    $files = @(Get-ChildItem -LiteralPath $ResultsDirectory -Filter '*benchmarkData.json' -File -Recurse | Sort-Object FullName)
}
if ($files.Count -eq 0) {
    $issues.Add("No *benchmarkData.json files found in $ResultsDirectory.")
}

foreach ($file in $files) {
    try {
        $document = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
        $context = Get-Property $document 'context'
        $build = Get-Property $context 'build'
        $version = Get-Property $build 'version'
        $brand = Get-Property $build 'brand'
        $model = Get-Property $build 'model'
        $deviceName = Get-Property $build 'device'
        $sdk = Get-Property $version 'sdk'
        $description = "$brand $model ($deviceName), Android API $sdk".Trim()
        if (-not $model -and -not $deviceName) { $description = 'Device metadata unavailable; inspect raw JSON.' }
        $sources.Add([pscustomobject]@{ Path = $file.FullName; Device = $description })
        foreach ($entry in @(Get-Property $document 'benchmarks')) {
            if ($null -eq $entry) { continue }
            $name = [string](Get-Property $entry 'name')
            if ($name -notmatch '^(coldStartup|libraryLoad|templateOpen|setSave|libraryScroll)(?:\[|$)') { continue }
            $testName = $Matches[1]
            $fixture = $null
            if ($name -match 'history=(10|1,?000)(?:\]|,|$)') {
                $fixture = [int]$Matches[1].Replace(',', '')
            } else {
                $parameters = Get-Property $entry 'params'
                foreach ($parameterName in @('history', 'historyCount')) {
                    $parameterValue = Get-Property $parameters $parameterName
                    $normalizedValue = ([string]$parameterValue).Replace(',', '')
                    if ($normalizedValue -in @('10', '1000')) { $fixture = [int]$normalizedValue; break }
                }
            }
            if ($null -eq $fixture) {
                $issues.Add("Cannot determine history fixture for '$name' in $($file.Name).")
                continue
            }
            $key = "$fixture/$testName"
            if (-not $records.ContainsKey($key)) { $records[$key] = New-Object 'System.Collections.Generic.List[object]' }
            $records[$key].Add($entry)
        }
    } catch {
        $issues.Add("Cannot read $($file.FullName): $($_.Exception.Message)")
    }
}

foreach ($key in $records.Keys) {
    if ($records[$key].Count -gt 1) {
        $issues.Add("Duplicate results for $key. Select a single device/run directory; stale reports are not combined.")
    }
}

foreach ($fixture in @(10, 1000)) {
    Add-MetricRow $fixture 'coldStartup' 'Cold start: usable' 'timeToFullDisplayMs' 2000
    Add-MetricRow $fixture 'coldStartup' 'Cold start: first frame' 'timeToInitialDisplayMs' $null
    Add-MetricRow $fixture 'libraryLoad' 'Library load' 'library_loadSumMs' 300 'library_loadCount'
    Add-MetricRow $fixture 'templateOpen' 'Template open' 'template_openSumMs' 300 'template_openCount'
    Add-MetricRow $fixture 'setSave' 'Set save' 'set_saveSumMs' 200 'set_saveCount'
}

$scrollLines = New-Object 'System.Collections.Generic.List[string]'
foreach ($fixture in @(10, 1000)) {
    $key = "$fixture/libraryScroll"
    if (-not $records.ContainsKey($key) -or $records[$key].Count -ne 1) { continue }
    $frameMetric = Get-Property (Get-Property $records[$key][0] 'sampledMetrics') 'frameOverrunMs'
    $frameRuns = Get-Property $frameMetric 'runs'
    if ($null -eq $frameRuns) { continue }
    $frames = @()
    foreach ($run in @($frameRuns)) {
        $frames += @(Get-Numbers $run "$fixture/libraryScroll/frameOverrunMs" -AllowNegative)
    }
    $stats = Get-Statistics $frames
    if ($null -eq $stats) { continue }
    $scrollLines.Add("| $fixture | $(@($frameRuns).Count) | $($stats.N) | $(Format-Milliseconds $stats.Median) | $(Format-Milliseconds $stats.P95) | $(Format-Milliseconds $stats.Max) |")
}

$budgetFailures = @($rows | Where-Object { $_.Status -eq 'OVER BUDGET' })
$overall = 'PASS: all required measurements meet the p95 budgets.'
if ($issues.Count -gt 0) {
    $overall = 'INCOMPLETE: these results do not establish compliance with the performance budgets.'
} elseif ($budgetFailures.Count -gt 0) {
    $overall = 'OVER BUDGET: one or more required p95 measurements exceed the budget.'
}

if (-not $NoReport) {
    $reportPath = [IO.Path]::GetFullPath($OutputFile)
    $reportDirectory = [IO.Path]::GetDirectoryName($reportPath)
    [void][IO.Directory]::CreateDirectory($reportDirectory)
    $lines = New-Object 'System.Collections.Generic.List[string]'
    $lines.Add('# Phone performance results')
    $lines.Add('')
    $lines.Add($overall)
    $lines.Add('')
    $reportTimeZone = [TimeZoneInfo]::FindSystemTimeZoneById('Eastern Standard Time')
    $reportTime = [TimeZoneInfo]::ConvertTime([DateTimeOffset]::UtcNow, $reportTimeZone)
    $lines.Add("Generated $($reportTime.ToString('yyyy-MM-dd HH:mm:ss zzz', $culture)) (America/New_York). Nearest-rank p95 from raw measured iterations; minimum $MinimumSamples per operation and fixture. Times are milliseconds.")
    $lines.Add('')
    $lines.Add('| History workouts | Operation | N | Median | p95 | Max | p95 budget | Trace count | Result |')
    $lines.Add('| ---: | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |')
    foreach ($row in $rows) {
        $n = 0; $median = $null; $p95 = $null; $maximum = $null
        if ($null -ne $row.Stats) {
            $n = $row.Stats.N; $median = $row.Stats.Median; $p95 = $row.Stats.P95; $maximum = $row.Stats.Max
        }
        $lines.Add("| $($row.Fixture) | $($row.Label) | $n | $(Format-Milliseconds $median) | $(Format-Milliseconds $p95) | $(Format-Milliseconds $maximum) | $(Format-Milliseconds $row.Budget) | $($row.Counts) | $($row.Status) |")
    }
    if ($scrollLines.Count -gt 0) {
        $lines.Add('')
        $lines.Add('Scrolling frame overrun is informational. A positive value means the frame missed its device deadline; these percentiles pool frames, rather than operation iterations.')
        $lines.Add('')
        $lines.Add('| History workouts | Iterations | Frames | Median | p95 | Max |')
        $lines.Add('| ---: | ---: | ---: | ---: | ---: | ---: |')
        foreach ($line in $scrollLines) { $lines.Add($line) }
    }
    if ($issues.Count -gt 0) {
        $lines.Add('')
        $lines.Add('Incomplete measurements:')
        $lines.Add('')
        foreach ($issue in $issues) { $lines.Add("- $issue") }
    }
    $lines.Add('')
    $lines.Add('Raw data and device metadata:')
    $lines.Add('')
    $baseUri = [Uri]($reportDirectory + [IO.Path]::DirectorySeparatorChar)
    foreach ($source in $sources) {
        $relative = $baseUri.MakeRelativeUri([Uri]$source.Path).ToString()
        $lines.Add("- $($source.Device): [benchmark JSON]($relative).")
    }
    $lines.Add('')
    $lines.Add('See [the measurement protocol](performance-checks.md) for fixture contents, compilation, and measurement limits. The raw JSON and Perfetto traces remain the source of record.')
    [IO.File]::WriteAllLines($reportPath, $lines, (New-Object Text.UTF8Encoding($false)))
    Write-Host "Report: $reportPath"
}

Write-Host $overall
if ($issues.Count -gt 0) {
    throw "Performance measurements are incomplete: $($issues -join ' ')"
}
if ($FailOnBudget -and $budgetFailures.Count -gt 0) {
    throw "$($budgetFailures.Count) performance measurements exceed their p95 budgets."
}
