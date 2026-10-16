[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$WorkoutCsv,
    [Parameter(Mandatory = $true)][string]$SourceJson,
    [datetime]$Since = [datetime]'2026-08-01'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$mapping = Get-Content -Raw (Join-Path $projectRoot 'data/exercise-matches.json') | ConvertFrom-Json
$sourceRows = Get-Content -Raw -LiteralPath $SourceJson | ConvertFrom-Json
$sourceById = @{}
$sourceRows | ForEach-Object { $sourceById[$_.id] = $_ }
$matchByTitle = @{}
$mapping.exercises | ForEach-Object { $matchByTitle[$_.title] = $_ }
$rows = @(Import-Csv -LiteralPath $WorkoutCsv | Where-Object {
    [datetime]::ParseExact($_.start_time, 'd MMM yyyy, HH:mm', [cultureinfo]::InvariantCulture) -ge $Since
})
function Catalog-Key([string]$Title) { return 'personal/' + ($Title.ToLowerInvariant() -replace '[^a-z0-9]+','-').Trim('-') }
$muscleNames = @{
    abdominals='ABS'; abductors='ABDUCTORS'; adductors='ADDUCTORS'; biceps='BICEPS'; calves='CALVES';
    chest='CHEST'; forearms='FOREARMS'; glutes='GLUTES'; hamstrings='HAMSTRINGS'; lats='LATS';
    'lower back'='LOWER_BACK'; 'middle back'='UPPER_BACK'; neck='OTHER'; quadriceps='QUADRICEPS';
    traps='TRAPS'; triceps='TRICEPS'
}
function Muscle([string]$Name, [string]$Shoulder) {
    if ($Name -eq 'shoulders') {
        if (-not $Shoulder) { throw 'A reviewed shoulder mapping is required' }
        return $Shoulder
    }
    if (-not $muscleNames.ContainsKey($Name)) { throw "Unmapped muscle: $Name" }
    return $muscleNames[$Name]
}
$entries = @($rows.exercise_title | Sort-Object -Unique | ForEach-Object {
    $title = $_
    $match = $matchByTitle[$title]
    if ($null -eq $match) { throw "Missing reviewed match: $title" }
    $verified = $match.status -in @('exact','alias')
    $source = if ($match.sourceId) { $sourceById[$match.sourceId] } else { $null }
    if ($match.sourceId -and $null -eq $source) { throw "Missing source: $($match.sourceId)" }
    $primary = $match.primaryMuscle
    $secondary = @()
    if ($source -and $verified) {
        $primary = Muscle $source.primaryMuscles[0] $match.shoulderMuscle
        $secondary = @(@($source.primaryMuscles | Select-Object -Skip 1) + @($source.secondaryMuscles) |
            ForEach-Object { Muscle $_ $match.shoulderMuscle } | Where-Object { $_ -ne $primary } | Select-Object -Unique)
    }
    [ordered]@{
        catalogKey=(Catalog-Key $title); title=$title
        description=if ($verified) { ($source.instructions -join "`n`n") } else { '' }
        primaryMuscle=$primary; secondaryMuscles=$secondary
        sourceId=if ($verified) { $match.sourceId } else { $null }
        matchStatus=$match.status
        relatedSourceId=if (-not $verified) { $match.sourceId } else { $null }
        sourceName=if ($verified) { $source.name } else { $null }
        equipment=if ($verified) { $source.equipment } else { $null }
        images=if ($verified) { @($source.images) } else { @() }
    }
})
if (($entries.catalogKey | Sort-Object -Unique).Count -ne $entries.Count) { throw 'Duplicate catalog keys' }
$templates = @($rows | Group-Object title | ForEach-Object {
    $latest = $_.Group | Sort-Object { [datetime]::ParseExact($_.start_time, 'd MMM yyyy, HH:mm', [cultureinfo]::InvariantCulture) } -Descending | Select-Object -First 1
    $session = @($_.Group | Where-Object start_time -eq $latest.start_time)
    $exerciseRows = New-Object 'System.Collections.Generic.List[object]'
    $previousTitle = $null
    $previousSet = -1
    foreach ($row in $session) {
        $setIndex = [int]$row.set_index
        if ($row.exercise_title -ne $previousTitle -or $setIndex -le $previousSet) {
            $exerciseRows.Add([ordered]@{ exerciseKey=(Catalog-Key $row.exercise_title); restTimeSeconds=$null })
        }
        $previousTitle = $row.exercise_title
        $previousSet = $setIndex
    }
    [ordered]@{
        catalogKey=('personal-routine/' + ($_.Name.ToLowerInvariant() -replace '[^a-z0-9]+','-').Trim('-'))
        title=$_.Name; description=('Based on your latest logged session: ' + $latest.start_time + '.')
        sourceSession=$latest.start_time; exercises=$exerciseRows.ToArray()
    }
})
$assets = Join-Path $projectRoot 'app/src/main/assets'
New-Item -ItemType Directory -Path $assets -Force | Out-Null
[ordered]@{ schemaVersion=1; since=$Since.ToString('yyyy-MM-dd'); source=$mapping.source; exercises=$entries } |
    ConvertTo-Json -Depth 12 | Set-Content (Join-Path $assets 'exercise_catalog.json') -Encoding utf8NoBOM
[ordered]@{ schemaVersion=1; since=$Since.ToString('yyyy-MM-dd'); templates=$templates } |
    ConvertTo-Json -Depth 12 | Set-Content (Join-Path $assets 'template_catalog.json') -Encoding utf8NoBOM
Write-Host "$($entries.Count) exercises, $($templates.Count) latest routines; cutoff $($Since.ToString('yyyy-MM-dd'))."
