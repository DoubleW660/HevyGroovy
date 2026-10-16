# Functional phone checks

These checks cover workout behavior and the phone layout. They do not run the
performance benchmark or claim that timing budgets have passed.

Use the separate QA installation, `com.example.hevygroovy.qa`, for destructive
scenarios. It has separate app storage from the regular installation. Instrumented
UI scenarios use their own randomly named SQLite databases. Never clear or uninstall
the regular app to prepare a test.

## Automated coverage

Run the QA instrumentation suite on the unlocked phone:

```powershell
$previousAndroidSerial = $env:ANDROID_SERIAL
try {
    $env:ANDROID_SERIAL = '<phone serial>'
    .\gradlew.bat :app:connectedQaAndroidTest -PphoneChecks=true
} finally {
    $env:ANDROID_SERIAL = $previousAndroidSerial
}
```

`PhoneReadinessTest` exercises real SQLite persistence and the real background
gateway. It checks original KG/LB labels, workout and exercise notes, completed and
unfinished sets after a storage connection and ViewModel are recreated, unsaved
workout details across saved-instance-state restoration, and discard confirmation
against persisted data. Its history scenario creates 26 finished workouts, loads a
second page, opens the oldest workout, checks both original units and converted
totals, excludes an unfinished placeholder from history, and verifies that another
user's workout stays hidden. Existing editor, library, and activity recovery tests cover
the full finish flow, template editing, failed-save drafts, and activity recreation.

The 10-second waits in instrumentation tests are synchronization timeouts. They are
not performance targets or measured load times.

The connected Gradle test task removes the QA installation after the suite. Its
generated APKs remain in the build directory. Before running the external phone
helper, reinstall both QA APKs and prepare the persistent fixture directly through
ADB, after the Gradle suite has finished:

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$serial = '<phone serial>'
& $adb -s $serial install -r 'app/build/outputs/apk/qa/app-qa.apk'
if ($LASTEXITCODE -ne 0) { throw 'QA app installation failed' }
& $adb -s $serial install -r 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk'
if ($LASTEXITCODE -ne 0) { throw 'QA test installation failed' }
$fixtureOutput = & $adb -s $serial shell am instrument -w -r -e class 'com.example.hevygroovy.ui.PhoneFixtureSetupTest#prepareManualPhoneFixture' com.example.hevygroovy.qa.test/androidx.test.runner.AndroidJUnitRunner
$fixtureOutput
if ($LASTEXITCODE -ne 0 -or ($fixtureOutput -join "`n") -notmatch 'OK\s+\(1 test\)') { throw 'QA fixture setup failed' }
.\scripts\check-phone.ps1 -Serial $serial -AdbPath $adb
```

Check that fixture instrumentation reports `OK (1 test)` before running the helper.
The fixture deliberately resets only the QA database and profile, then creates 30
finished workouts, two routines, and the named active recovery session. The helper
saves screenshots, XML hierarchies, and `phone-check-results.json` under
`app/build/phone-checks`. It uses the phone's window hierarchy to locate controls,
including the checked state of the unit chips. It restores the original global font
and rotation settings on completion or failure. Rerun the fixture before repeating
the helper: the check changes the QA default unit to KG and adds an unfinished set.
These commands do not clear, uninstall, or seed `com.example.hevygroovy`.

## Phone-only checks

Record the device, Android version, build, and observed result for each scenario.
Keep the phone unlocked. Restore airplane mode, Wi-Fi, font size, and rotation
settings after testing.

| Scenario | Action | Expected result |
| --- | --- | --- |
| Offline flow | Enable airplane mode and disable Wi-Fi. Create a workout, record KG and LB sets, finish, then open its history detail. | Every action works locally; original set units and converted aggregate volume are labeled. |
| Process restart | Save a named active workout with notes and at least one set. Force-stop the QA package, then relaunch it. | The same session, notes, set values, units, and completion state return without another session being created. |
| Rotation | Open a set editor, type unsaved weight and reps, rotate to landscape and back. | The editor stays open with the draft intact; the session stays active. |
| Discard | Open discard confirmation, choose Keep workout, then confirm discard. Relaunch the QA app. | Keep preserves the session; confirmed discard removes it permanently. |
| Navigation | Visit history, workout/routines, exercises, and profile. Open a routine or workout detail and use Back. | The selected tab remains clear, Back returns to the expected list, and an active workout stays available. |
| Layout | Check the normal phone font size, a larger font size, landscape, the keyboard, and long workout/exercise names. | Bottom navigation clears system bars; content scrolls; buttons, units, and dialog actions stay usable. |

`ActivityScenario.recreate()` and Compose saved-state restoration are separate
checks from process death. A real process restart requires an external command:

```powershell
& $adb -s $serial shell am force-stop com.example.hevygroovy.qa
& $adb -s $serial shell am start -W -n com.example.hevygroovy.qa/com.example.hevygroovy.MainActivity
```

Do not force-stop the app while its instrumentation runner is active; that kills
the test process. Capture the saved session before stopping and inspect the UI
after relaunch. Saved database data must survive a force-stop. Android may discard
unsaved UI drafts after a force-stop; rotation and saved-instance-state restoration
verify those drafts separately.

## Results

Verified on 2026-10-16 using a Samsung Galaxy S23 Ultra (SM-S918U), Android 16 /
API 36, QA build `1.0-qa` and regular debug build `1.0`.

| Check | Observed result |
| --- | --- |
| JVM tests | 189 passed; no failures. |
| Android lint | `lintQa` passed. |
| Offline phone suite | All 47 instrumentation tests passed with airplane mode enabled and Wi-Fi disabled. Original airplane/Wi-Fi settings were restored. |
| Final navigation recheck | Both targeted tests passed after the navigation fixes. Finishing from an already loaded History tab refreshes its list; Home's View all and Profile's history shortcut open the list. |
| History pagination/detail | The real SQLite phone test loads 26 workouts across two pages, opens the oldest, preserves the list position, and checks notes, sets, original KG/LB units, and completed-set totals. Another user's workout is excluded. |
| Real process restart | Force-stopping and relaunching QA restores Phone recovery check, workout and exercise notes, the completed 60 KG set, and the unfinished 135 LB set. KG remains selected in Profile after restarting. |
| Physical rotation | An unsaved 62.5 weight draft survives landscape and portrait changes. Cancel leaves the saved 60 KG value unchanged. Landscape dialog actions remain visible. |
| Default unit | Adding a set after choosing KG creates a KG set; the existing LB set stays in LB. |
| Discard confirmation | The phone test verifies Keep workout, confirmed discard, and deletion after storage reopens. |
| Visual review | Home, routines, exercises, history list/detail, active session, and Profile were inspected in phone screenshots. Insets keep the menu and Resume dock clear of the system navigation area. Larger text exposed a clipped menu label; the adjusted menu was rechecked at font scale 1.3 with every label fully visible. |
| Installation | The updated regular debug app was installed with `adb install -r` and opened successfully. Its data was preserved; only the separate QA and QA test installations were removed afterward. |

The successful external check report is
`app/build/phone-checks/phone-check-results.json` (`passed: true`, six checks,
no cleanup errors). Screenshots and XML hierarchies are in the same directory.
The final larger-text review is `10-profile-large-font-final.png`, history list and
sets are `11-history-list.png` and `12-history-sets.png`, and the installed regular
app is `13-regular-installed.png`. Airplane mode was restored to off, Wi-Fi to on,
font scale to 1.0, automatic rotation to on, and user rotation to 0.
JVM results are under `app/build/reports/tests/testQaUnitTest`; the latest targeted
phone report is under `app/build/reports/androidTests/connected/qa`. Gradle replaces
that phone report on each run, so the latest report contains the two final checks
rather than the earlier full 47-test suite.

These are functional and visual checks on this phone. Performance benchmarks and
release checks were not run. A full TalkBack audit and other device sizes remain
outside this verification.
