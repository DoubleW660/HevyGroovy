[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,
    [string]$AdbPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
    [string]$ArtifactDirectory = (Join-Path $PSScriptRoot '..\app\build\phone-checks')
)

# Functional checks only. Run PhoneFixtureSetupTest in the separate QA app first.
# This script never clears, uninstalls, or targets the ordinary app installation.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
Set-Variable -Name qaPackage -Value 'com.example.hevygroovy.qa' -Option Constant
$component = "$qaPackage/com.example.hevygroovy.MainActivity"
$remoteXml = '/sdcard/hevy-groovy-qa.xml'
$remoteScreenshot = '/sdcard/hevy-groovy-qa.png'
$artifacts = [IO.Path]::GetFullPath($ArtifactDirectory)
$checks = New-Object 'System.Collections.Generic.List[object]'
$screenshots = New-Object 'System.Collections.Generic.List[string]'
$cleanupErrors = New-Object 'System.Collections.Generic.List[string]'
$originalSettings = @{}
$lastDocument = $null
$failure = $null
$passed = $false
$protocol = @(
    'Open the QA Home, Workout, Exercises, History detail and Profile screens; save screenshots.',
    'Select KG in Profile, force-stop only the QA package, relaunch, and verify KG remains selected.',
    'Resume Phone recovery check; verify saved notes and original 60 KG / 135 LB set labels.',
    'Enter an unsaved 62.5 weight draft, rotate to landscape, verify the draft, return to portrait, and cancel.',
    'Verify the original saved 60 KG value remains; add a new set and verify its default is KG while the old LB set remains LB.',
    'Capture Profile at font scale 1.3; restore all original global font and rotation settings.'
)

function Invoke-Adb {
    param([string[]]$Arguments)
    $output = & $AdbPath -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed ($LASTEXITCODE): $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return ($output -join "`n").Trim()
}

function Test-QaFocused {
    $windows = Invoke-Adb -Arguments @('shell', 'dumpsys', 'window')
    $focus = @($windows -split "`n" | Where-Object { $_ -match 'mCurrentFocus=' } | Select-Object -First 1)
    return ($focus.Count -gt 0 -and $focus[0] -match ([regex]::Escape($qaPackage) + '/'))
}

function Assert-QaFocused {
    # Rotation and dialog dismissal briefly leave Android without a focused window.
    # Wait for the QA window before sending any input or collecting a hierarchy.
    $deadline = [DateTime]::UtcNow.AddSeconds(5)
    do {
        if (Test-QaFocused) { return }
        Start-Sleep -Milliseconds 150
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'The QA app is not the focused window. Unlock the phone and leave HevyGroovy QA in front.'
}

function Start-Qa {
    $result = Invoke-Adb -Arguments @('shell', 'am', 'start', '-W', '-n', $component)
    if ($result -match '(?m)^Error:') { throw $result }
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    do {
        if (Test-QaFocused) { return }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'The QA activity did not become focused. The phone may be locked.'
}

function Get-UiDocument {
    Assert-QaFocused
    [void](Invoke-Adb -Arguments @('shell', 'uiautomator', 'dump', '--compressed', $remoteXml))
    $xml = Invoke-Adb -Arguments @('shell', 'cat', $remoteXml)
    $start = $xml.IndexOf('<?xml')
    if ($start -lt 0) { $start = $xml.IndexOf('<hierarchy') }
    if ($start -lt 0) { throw 'UI Automator did not return a hierarchy.' }
    $document = New-Object System.Xml.XmlDocument
    $document.LoadXml($xml.Substring($start))
    $script:lastDocument = $document
    return $document
}

function Find-UiNode {
    param(
        [System.Xml.XmlDocument]$Document,
        [string]$Id,
        [string]$IdPrefix,
        [string]$Text,
        [string]$Description,
        [switch]$PartialText,
        [switch]$PartialDescription,
        [Nullable[bool]]$Checked
    )
    foreach ($node in $Document.SelectNodes('//node')) {
        $resource = $node.GetAttribute('resource-id')
        $simpleId = ($resource -split '/')[-1]
        if ($Id -and $resource -ne $Id -and $simpleId -ne $Id) { continue }
        if ($IdPrefix -and -not $simpleId.StartsWith($IdPrefix, [StringComparison]::Ordinal)) { continue }
        if ($Text) {
            $value = $node.GetAttribute('text')
            if ($PartialText) {
                if (-not $value.Contains($Text)) { continue }
            } elseif ($value -ne $Text) { continue }
        }
        if ($Description) {
            $value = $node.GetAttribute('content-desc')
            if ($PartialDescription) {
                if (-not $value.Contains($Description)) { continue }
            } elseif ($value -ne $Description) { continue }
        }
        if ($null -ne $Checked -and $node.GetAttribute('checked') -ne ([bool]$Checked).ToString().ToLowerInvariant()) { continue }
        return $node
    }
    return $null
}

function Get-UiSize {
    param([System.Xml.XmlDocument]$Document)
    $root = $Document.SelectSingleNode('/hierarchy/node')
    if ($null -eq $root -or $root.GetAttribute('bounds') -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
        throw 'Could not read the visible window bounds.'
    }
    return @{ Width = [int]$Matches[3]; Height = [int]$Matches[4] }
}

function Move-UiList {
    param([System.Xml.XmlDocument]$Document)
    Assert-QaFocused
    $size = Get-UiSize -Document $Document
    $x = [int]($size.Width * 0.50)
    [void](Invoke-Adb -Arguments @('shell', 'input', 'swipe', "$x", "$([int]($size.Height * 0.76))",
        "$x", "$([int]($size.Height * 0.31))", '280'))
}

function Wait-UiNode {
    param([hashtable]$Selector, [string]$Label, [switch]$Scroll)
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    $scrolls = 0
    do {
        $document = Get-UiDocument
        $node = Find-UiNode -Document $document @Selector
        if ($null -ne $node) { return $node }
        if ($Scroll -and $scrolls -lt 7) {
            Move-UiList -Document $document
            $scrolls++
        } else {
            Start-Sleep -Milliseconds 150
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "UI condition did not appear: $Label"
}

function Tap-UiNode {
    param([System.Xml.XmlElement]$Node)
    Assert-QaFocused
    if ($Node.GetAttribute('bounds') -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
        throw 'The selected UI node has no usable bounds.'
    }
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    [void](Invoke-Adb -Arguments @('shell', 'input', 'tap', "$x", "$y"))
}

function Tap-Selector {
    param([hashtable]$Selector, [string]$Label, [switch]$Scroll)
    $node = Wait-UiNode -Selector $Selector -Label $Label -Scroll:$Scroll
    Tap-UiNode -Node $node
}

function Save-PhoneScreenshot {
    param([string]$Name)
    Assert-QaFocused
    [void](Invoke-Adb -Arguments @('shell', 'screencap', '-p', $remoteScreenshot))
    $path = Join-Path $artifacts "$Name.png"
    [void](Invoke-Adb -Arguments @('pull', $remoteScreenshot, $path))
    $screenshots.Add($path)
    $document = Get-UiDocument
    $document.Save((Join-Path $artifacts "$Name.xml"))
}

function Add-Check {
    param([string]$Name, [string]$Observed)
    $checks.Add([pscustomobject]@{ name = $Name; passed = $true; observed = $Observed })
    Write-Host "PASS: $Name"
}

function Set-PhoneSetting {
    param([string]$Name, [string]$Value)
    [void](Invoke-Adb -Arguments @('shell', 'settings', 'put', 'system', $Name, $Value))
}

function Restore-PhoneSetting {
    param([string]$Name, [string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -eq 'null') {
        [void](Invoke-Adb -Arguments @('shell', 'settings', 'delete', 'system', $Name))
    } else {
        Set-PhoneSetting -Name $Name -Value $Value
    }
}

function Wait-PortraitOrLandscape {
    param([bool]$Landscape)
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    do {
        $document = Get-UiDocument
        $size = Get-UiSize -Document $document
        if (($size.Width -gt $size.Height) -eq $Landscape) { return }
        Start-Sleep -Milliseconds 150
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "The expected screen orientation did not appear (landscape: $Landscape)."
}

if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw "ADB was not found at $AdbPath" }
[void](New-Item -ItemType Directory -Path $artifacts -Force)

try {
    $deviceModel = Invoke-Adb -Arguments @('shell', 'getprop', 'ro.product.model')
    $androidVersion = Invoke-Adb -Arguments @('shell', 'getprop', 'ro.build.version.release')
    foreach ($name in @('accelerometer_rotation', 'user_rotation', 'font_scale')) {
        $originalSettings[$name] = Invoke-Adb -Arguments @('shell', 'settings', 'get', 'system', $name)
    }
    # The only process ever stopped by this protocol is the separate QA package.
    [void](Invoke-Adb -Arguments @('shell', 'am', 'force-stop', $qaPackage))
    Start-Qa
    Assert-QaFocused
    Set-PhoneSetting -Name 'accelerometer_rotation' -Value '0'
    Set-PhoneSetting -Name 'user_rotation' -Value '0'
    Wait-PortraitOrLandscape -Landscape $false

    [void](Wait-UiNode -Selector @{ Id = 'home-tab' } -Label 'bottom navigation')
    [void](Wait-UiNode -Selector @{ Id = 'resume-workout' } -Label 'seeded active workout loaded on Home')
    Save-PhoneScreenshot -Name '01-home'
    Tap-Selector -Selector @{ Id = 'templates-tab' } -Label 'Workout tab'
    [void](Wait-UiNode -Selector @{ Text = 'Upper Body' } -Label 'Upper Body routine')
    Save-PhoneScreenshot -Name '02-workout'
    Tap-Selector -Selector @{ Id = 'exercises-tab' } -Label 'Exercises tab'
    [void](Wait-UiNode -Selector @{ Text = 'Bench Press' } -Label 'exercise library')
    Save-PhoneScreenshot -Name '03-exercises'
    Tap-Selector -Selector @{ Id = 'history-tab' } -Label 'History tab'
    Tap-Selector -Selector @{ IdPrefix = 'history-card-' } -Label 'first workout history card'
    [void](Wait-UiNode -Selector @{ Id = 'history-total-volume' } -Label 'history totals')
    Save-PhoneScreenshot -Name '04-history-detail'
    Tap-Selector -Selector @{ Id = 'profile-tab' } -Label 'Profile tab'
    [void](Wait-UiNode -Selector @{ Id = 'profile-unit-LB'; Checked = $true } -Label 'fixture default LB')
    Save-PhoneScreenshot -Name '05-profile'
    Add-Check -Name 'Five tabs and history detail' -Observed 'Home, Workout routines, Exercises, History detail, and Profile rendered on the phone.'

    Tap-Selector -Selector @{ Id = 'profile-unit-KG' } -Label 'KG preference'
    [void](Wait-UiNode -Selector @{ Id = 'profile-unit-KG'; Checked = $true } -Label 'saved KG preference')
    [void](Invoke-Adb -Arguments @('shell', 'am', 'force-stop', $qaPackage))
    Start-Qa
    Tap-Selector -Selector @{ Id = 'profile-tab' } -Label 'Profile after real process restart'
    [void](Wait-UiNode -Selector @{ Id = 'profile-unit-KG'; Checked = $true } -Label 'persisted KG after process restart')
    Add-Check -Name 'Default unit survives process restart' -Observed 'KG remained selected after force-stop and a fresh QA process launch.'

    Tap-Selector -Selector @{ Id = 'resume-workout' } -Label 'persistent active-workout dock'
    [void](Wait-UiNode -Selector @{ Text = 'Phone recovery check' } -Label 'saved workout name')
    [void](Wait-UiNode -Selector @{ Text = 'Saved offline on this phone' } -Label 'saved workout notes')
    [void](Wait-UiNode -Selector @{ Text = 'Controlled reps' } -Label 'saved exercise notes' -Scroll)
    [void](Wait-UiNode -Selector @{ Text = '60.0 KG × 8 reps' } -Label 'saved KG set' -Scroll)
    [void](Wait-UiNode -Selector @{ Text = '135.0 LB × 6 reps' } -Label 'saved LB set' -Scroll)
    Save-PhoneScreenshot -Name '06-recovered-workout'
    Add-Check -Name 'Active session survives process restart' -Observed 'The named active workout, workout and exercise notes, and original 60 KG / 135 LB sets returned after a real process restart.'

    Tap-Selector -Selector @{ Description = 'Edit set 1 for Bench Press' } -Label 'first set editor' -Scroll
    Tap-Selector -Selector @{ Id = 'set-weight' } -Label 'weight input'
    Assert-QaFocused
    [void](Invoke-Adb -Arguments (@('shell', 'input', 'keyevent', '123') + @('67') * 16))
    [void](Invoke-Adb -Arguments @('shell', 'input', 'text', '62.5'))
    [void](Wait-UiNode -Selector @{ Id = 'set-weight'; Text = '62.5' } -Label 'entered unsaved weight')
    Assert-QaFocused
    Set-PhoneSetting -Name 'user_rotation' -Value '1'
    Wait-PortraitOrLandscape -Landscape $true
    [void](Wait-UiNode -Selector @{ Id = 'set-weight'; Text = '62.5' } -Label 'draft after landscape rotation')
    Save-PhoneScreenshot -Name '07-landscape-draft'
    Assert-QaFocused
    Set-PhoneSetting -Name 'user_rotation' -Value '0'
    Wait-PortraitOrLandscape -Landscape $false
    [void](Wait-UiNode -Selector @{ Id = 'set-weight'; Text = '62.5' } -Label 'draft after returning to portrait')
    Tap-Selector -Selector @{ Text = 'Cancel' } -Label 'cancel unsaved set edit'
    [void](Wait-UiNode -Selector @{ Text = '60.0 KG × 8 reps' } -Label 'original weight after cancelling' -Scroll)
    Add-Check -Name 'Rotation retains draft and Cancel preserves saved value' -Observed 'Unsaved 62.5 survived both orientation changes; cancelling restored the saved 60.0 KG set.'

    Tap-Selector -Selector @{ Description = 'Add set to Bench Press' } -Label 'add set using the profile default' -Scroll
    [void](Wait-UiNode -Selector @{ Text = '0.0 KG × 0 reps' } -Label 'new set defaults to KG' -Scroll)
    [void](Wait-UiNode -Selector @{ Text = '135.0 LB × 6 reps' } -Label 'existing LB set retains its unit' -Scroll)
    Save-PhoneScreenshot -Name '08-new-default-kg-set'
    Add-Check -Name 'Default applies only to new sets' -Observed 'A new 0.0 KG set used the profile preference; the existing 135.0 LB set remained LB.'

    Tap-Selector -Selector @{ Id = 'minimize-workout' } -Label 'minimize active session'
    Tap-Selector -Selector @{ Id = 'profile-tab' } -Label 'Profile for large-font review'
    Assert-QaFocused
    Set-PhoneSetting -Name 'font_scale' -Value '1.3'
    [void](Wait-UiNode -Selector @{ Id = 'profile-unit-KG'; Checked = $true } -Label 'Profile at larger font size')
    Save-PhoneScreenshot -Name '09-profile-large-font'
    Add-Check -Name 'Large-font screen captured' -Observed 'Profile and the selected KG control rendered at font scale 1.3. Inspect the screenshot for visual quality.'
    $passed = $true
} catch {
    $failure = $_
    try {
        if (Test-QaFocused) { Save-PhoneScreenshot -Name 'failure' }
    } catch { Write-Warning "Could not capture the failure screen: $($_.Exception.Message)" }
} finally {
    # Restore every setting independently, including after any assertion or ADB failure.
    foreach ($name in @('font_scale', 'user_rotation', 'accelerometer_rotation')) {
        if ($originalSettings.ContainsKey($name)) {
            try { Restore-PhoneSetting -Name $name -Value $originalSettings[$name] }
            catch { $cleanupErrors.Add("${name}: $($_.Exception.Message)") }
        }
    }
    if ($cleanupErrors.Count -gt 0) { $passed = $false }
    $report = [ordered]@{
        kind = 'functional-phone-checks'
        performanceTest = $false
        recordedAtUtc = [DateTime]::UtcNow.ToString('o')
        package = $qaPackage
        serial = $Serial
        deviceModel = if (Get-Variable deviceModel -ErrorAction SilentlyContinue) { $deviceModel } else { $null }
        androidVersion = if (Get-Variable androidVersion -ErrorAction SilentlyContinue) { $androidVersion } else { $null }
        passed = $passed
        error = if ($null -ne $failure) { $failure.Exception.Message } else { $null }
        checks = $checks.ToArray()
        screenshots = $screenshots.ToArray()
        protocol = $protocol
        originalSettings = $originalSettings
        cleanupErrors = $cleanupErrors.ToArray()
        timeoutMeaning = '20-second waits synchronize functional UI conditions; no load-time budgets are measured.'
        visualReview = 'Screenshots require human inspection; selector checks do not establish pixel-level visual quality.'
        fixtureEffect = 'Only QA data is changed: default unit becomes KG and a new unfinished set is added.'
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $artifacts 'phone-check-results.json') -Encoding utf8
    Write-Host "Functional phone report: $(Join-Path $artifacts 'phone-check-results.json')"
}

if ($null -ne $failure) { throw $failure }
if ($cleanupErrors.Count -gt 0) { throw "Could not restore phone settings: $($cleanupErrors -join '; ')" }
