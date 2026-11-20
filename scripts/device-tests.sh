#!/usr/bin/env bash
set -euo pipefail
./gradlew assembleDebug assembleDebugAndroidTest
mkdir -p app/build/reports/androidTests
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.keepr.app.test/androidx.test.runner.AndroidJUnitRunner | tee app/build/reports/androidTests/instrumentation.txt
python3 - <<'PY'
from pathlib import Path
import re
output=Path('app/build/reports/androidTests/instrumentation.txt').read_text()
assert re.search(r'OK \(\d+ tests?\)',output) and 'FAILURES!!!' not in output and 'INSTRUMENTATION_FAILED' not in output, 'Instrumentation suite failed or did not finish'
PY
