#requires -Version 7.0
<#
.SYNOPSIS
Builds the bundled personal workout history from a Hevy CSV export.
.DESCRIPTION
Only sessions starting on or after Since are included. CSV timestamps are interpreted
in America/New_York, independently of the computer's time zone. The existing exercise
and routine catalogs supply stable keys; this script does not change those catalogs.
All validation finishes before workout_history.json is written.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$WorkoutCsv,
    [datetime]$Since = [datetime]'2026-08-01'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path $PSScriptRoot -Parent
$assetsPath = Join-Path $projectRoot 'app/src/main/assets'
$destination = Join-Path $assetsPath 'workout_history.json'
$culture = [cultureinfo]::InvariantCulture
$sourceTimezone = 'America/New_York'
$cutoff = [datetime]::SpecifyKind($Since.Date, [DateTimeKind]::Unspecified)
try {
    $zone = [TimeZoneInfo]::FindSystemTimeZoneById($sourceTimezone)
} catch [TimeZoneNotFoundException] {
    # Windows installations without IANA aliases use the equivalent Windows identifier.
    $zone = [TimeZoneInfo]::FindSystemTimeZoneById('Eastern Standard Time')
}

function Read-LocalTimestamp([string]$Text, [string]$Field) {
    try {
        $value = [datetime]::ParseExact($Text, 'd MMM yyyy, HH:mm', $culture, [Globalization.DateTimeStyles]::None)
    } catch {
        throw "Invalid $Field timestamp: '$Text'. Expected d MMM yyyy, HH:mm."
    }
    $value = [datetime]::SpecifyKind($value, [DateTimeKind]::Unspecified)
    if ($zone.IsInvalidTime($value) -or $zone.IsAmbiguousTime($value)) {
        throw "$Field timestamp '$Text' is ambiguous or invalid in $sourceTimezone. Supply an unambiguous source time."
    }
    return $value
}

function Convert-ToEpochMillis([datetime]$LocalTime) {
    $utc = [TimeZoneInfo]::ConvertTimeToUtc($LocalTime, $zone)
    return ([DateTimeOffset]::new($utc)).ToUnixTimeMilliseconds()
}

function Read-NullableText([string]$Text) {
    if ([string]::IsNullOrWhiteSpace($Text)) { return $null }
    return $Text
}

function Read-NonnegativeInteger([string]$Text, [string]$Field) {
    $value = 0
    if ([string]::IsNullOrWhiteSpace($Text) -or -not [int]::TryParse($Text,
        [Globalization.NumberStyles]::Integer, $culture, [ref]$value) -or $value -lt 0) {
        throw "$Field must be a nonnegative whole number; received '$Text'."
    }
    return $value
}

function Read-NullableNumber([string]$Text, [string]$Field) {
    if ([string]::IsNullOrWhiteSpace($Text)) { return $null }
    $value = 0.0
    if (-not [double]::TryParse($Text, [Globalization.NumberStyles]::Float, $culture, [ref]$value) -or
        -not [double]::IsFinite($value) -or $value -lt 0) {
        throw "$Field must be a finite nonnegative number or blank; received '$Text'."
    }
    return $value
}

function Get-Sha256([string]$Text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text))
        return [BitConverter]::ToString($digest).Replace('-', '').ToLowerInvariant()
    } finally { $sha.Dispose() }
}

function Read-CatalogKeys([string]$Path, [string]$Property) {
    $catalog = Get-Content -Raw -LiteralPath $Path -Encoding utf8 | ConvertFrom-Json
    if ($catalog.schemaVersion -ne 1) { throw "Unsupported catalog schema: $Path" }
    $keys = [Collections.Generic.Dictionary[string, string]]::new([StringComparer]::Ordinal)
    $seenKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($entry in $catalog.$Property) {
        if ([string]::IsNullOrWhiteSpace($entry.title) -or [string]::IsNullOrWhiteSpace($entry.catalogKey) -or
            -not $keys.TryAdd($entry.title, $entry.catalogKey) -or -not $seenKeys.Add($entry.catalogKey)) {
            throw "Duplicate or missing title/catalog key in $Path."
        }
    }
    return ,$keys
}

$exerciseKeys = Read-CatalogKeys (Join-Path $assetsPath 'exercise_catalog.json') 'exercises'
$templateKeys = Read-CatalogKeys (Join-Path $assetsPath 'template_catalog.json') 'templates'
$csvRows = @(Import-Csv -LiteralPath $WorkoutCsv -Encoding utf8)
if ($csvRows.Count -eq 0) { throw 'The workout CSV contains no data rows.' }
$headers = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($name in $csvRows[0].PSObject.Properties.Name) { [void]$headers.Add($name) }
foreach ($required in @('title', 'start_time', 'end_time', 'description', 'exercise_title', 'superset_id',
    'exercise_notes', 'set_index', 'set_type', 'weight_lbs', 'reps', 'distance_miles', 'duration_seconds', 'rpe')) {
    if (-not $headers.Contains($required)) { throw "The workout CSV is missing column '$required'." }
}

$sessions = [Collections.Generic.Dictionary[long, object]]::new()
$sourceRowHashes = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$includedRows = 0
for ($index = 0; $index -lt $csvRows.Count; $index++) {
    $row = $csvRows[$index]
    $record = $index + 2
    $startedLocal = Read-LocalTimestamp $row.start_time "start_time at CSV row $record"
    if ($startedLocal -lt $cutoff) { continue }
    $startedAt = Convert-ToEpochMillis $startedLocal
    if ($startedAt -lt 0) { throw "Workout at CSV row $record starts before the supported epoch." }
    if ([string]::IsNullOrWhiteSpace($row.title) -or [string]::IsNullOrWhiteSpace($row.exercise_title)) {
        throw "Workout and exercise names must be present at CSV row $record."
    }
    if (-not $exerciseKeys.ContainsKey($row.exercise_title)) {
        throw "No exercise catalog mapping for '$($row.exercise_title)' at CSV row $record."
    }
    if (-not $templateKeys.ContainsKey($row.title)) {
        throw "No routine catalog mapping for '$($row.title)' at CSV row $record."
    }
    foreach ($unsupported in @('superset_id', 'distance_miles', 'duration_seconds')) {
        if (-not [string]::IsNullOrWhiteSpace($row.$unsupported)) {
            throw "Nonblank '$unsupported' at CSV row $record cannot be represented in the current history schema."
        }
    }
    # Identical rows are ambiguous: require source review instead of silently duplicating/skipping sets.
    $canonicalRow = ($row.PSObject.Properties | ForEach-Object {
        $text = [string]$_.Value
        $text.Length.ToString($culture) + ':' + $text
    }) -join '|'
    if (-not $sourceRowHashes.Add((Get-Sha256 $canonicalRow))) {
        throw "Duplicate CSV row $record. Review whether the source contains an accidental duplicate."
    }
    if (-not $sessions.ContainsKey($startedAt)) {
        $sessions.Add($startedAt, [Collections.Generic.List[object]]::new())
    }
    $sessions[$startedAt].Add([pscustomobject]@{ data = $row; record = $record })
    $includedRows++
}
if ($includedRows -eq 0) { throw "No workouts start on or after $($cutoff.ToString('yyyy-MM-dd'))." }

$workouts = [Collections.Generic.List[object]]::new()
$sourceKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$usedExerciseKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$typeCounts = [ordered]@{ NORMAL = 0; FAILURE = 0; WARMUP = 0; DROP = 0 }
$occurrenceCount = 0
$setCount = 0
$blankWeights = 0
$zeroReps = 0
$totalReps = 0L
$totalPoundVolume = [decimal]0
$workoutNoteCount = 0
$exerciseNoteCount = 0
foreach ($startedAt in @($sessions.Keys | Sort-Object)) {
    $sessionRows = $sessions[$startedAt]
    $first = $sessionRows[0].data
    $endedAt = Convert-ToEpochMillis (Read-LocalTimestamp $first.end_time 'end_time')
    if ($endedAt -lt $startedAt) { throw "Workout '$($first.title)' ends before it starts." }
    $notes = Read-NullableText $first.description
    $sourceKey = 'hevy-history-v1/' + (Get-Sha256 ($startedAt.ToString($culture) + "`n" + $first.title))
    if (-not $sourceKeys.Add($sourceKey)) { throw "Duplicate workout source key: $sourceKey" }
    $exercises = [Collections.Generic.List[object]]::new()
    $current = $null
    $previousTitle = $null
    $previousSetIndex = -1
    foreach ($sourceRow in $sessionRows) {
        $row = $sourceRow.data
        $record = $sourceRow.record
        $rowEnd = Convert-ToEpochMillis (Read-LocalTimestamp $row.end_time "end_time at CSV row $record")
        if ($row.title -cne $first.title -or $rowEnd -ne $endedAt -or
            (Read-NullableText $row.description) -cne $notes) {
            throw "Conflicting workout title/end/notes at CSV row $record for start '$($first.start_time)'."
        }
        $setIndex = Read-NonnegativeInteger $row.set_index "set_index at CSV row $record"
        $exerciseNotes = Read-NullableText $row.exercise_notes
        if ($row.exercise_title -cne $previousTitle -or $setIndex -le $previousSetIndex) {
            if ($setIndex -ne 0) { throw "Exercise occurrence at CSV row $record must start at set_index 0." }
            $current = [ordered]@{
                exerciseKey = $exerciseKeys[$row.exercise_title]
                nameSnapshot = $row.exercise_title
                notes = $exerciseNotes
                sets = [Collections.Generic.List[object]]::new()
            }
            $exercises.Add($current)
            [void]$usedExerciseKeys.Add($current.exerciseKey)
            $occurrenceCount++
            if ($null -ne $exerciseNotes) { $exerciseNoteCount++ }
        } elseif ($setIndex -ne $previousSetIndex + 1) {
            throw "Noncontiguous set_index at CSV row ${record}: expected $($previousSetIndex + 1), received $setIndex."
        }
        if ($exerciseNotes -cne $current.notes) { throw "Conflicting exercise notes at CSV row $record." }
        $weight = Read-NullableNumber $row.weight_lbs "weight_lbs at CSV row $record"
        $reps = Read-NonnegativeInteger $row.reps "reps at CSV row $record"
        $rpe = Read-NullableNumber $row.rpe "rpe at CSV row $record"
        if ($null -ne $rpe -and $rpe -gt 10) { throw "RPE exceeds 10 at CSV row $record." }
        $type = switch -CaseSensitive ($row.set_type) {
            'normal' { 'NORMAL' }
            'failure' { 'FAILURE' }
            'warmup' { 'WARMUP' }
            'dropset' { 'DROP' }
            default { throw "Unsupported set_type '$($row.set_type)' at CSV row $record." }
        }
        $current.sets.Add([ordered]@{
            setNumber = $setIndex + 1
            weight = $weight
            unit = 'LB'
            reps = $reps
            setType = $type
            rpe = $rpe
            restSeconds = $null
        })
        $setCount++
        $typeCounts[$type]++
        $totalReps += $reps
        if ($null -eq $weight) { $blankWeights++ } else { $totalPoundVolume += [decimal]$weight * $reps }
        if ($reps -eq 0) { $zeroReps++ }
        $previousTitle = $row.exercise_title
        $previousSetIndex = $setIndex
    }
    foreach ($exercise in $exercises) { $exercise.sets = $exercise.sets.ToArray() }
    if ($null -ne $notes) { $workoutNoteCount++ }
    $workouts.Add([ordered]@{
        sourceKey = $sourceKey
        title = $first.title
        startedAtEpochMillis = $startedAt
        endedAtEpochMillis = $endedAt
        notes = $notes
        templateKey = $templateKeys[$first.title]
        exercises = $exercises.ToArray()
    })
}
if ($setCount -ne $includedRows -or $workouts.Count -ne $sessions.Count) {
    throw 'Internal count mismatch: history conversion did not preserve all selected source rows.'
}

$document = [ordered]@{
    schemaVersion = 1
    since = $cutoff.ToString('yyyy-MM-dd', $culture)
    sourceTimezone = $sourceTimezone
    workouts = $workouts.ToArray()
}
$document | ConvertTo-Json -Depth 16 | Set-Content -LiteralPath $destination -Encoding utf8NoBOM
$assetHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
[pscustomobject][ordered]@{
    Workouts = $workouts.Count
    ExerciseOccurrences = $occurrenceCount
    Exercises = $usedExerciseKeys.Count
    Sets = $setCount
    Reps = $totalReps
    BlankWeights = $blankWeights
    ZeroRepSets = $zeroReps
    WorkoutNotes = $workoutNoteCount
    ExerciseNotes = $exerciseNoteCount
    SetTypes = $typeCounts
    VolumeLbReps = $totalPoundVolume
    VolumeKgReps = $totalPoundVolume * [decimal]0.45359237
    Since = $document.since
    SourceTimezone = $sourceTimezone
    FirstStartedAtEpochMillis = $workouts[0].startedAtEpochMillis
    LastStartedAtEpochMillis = $workouts[$workouts.Count - 1].startedAtEpochMillis
    AssetSha256 = $assetHash
    AssetPath = $destination
}
