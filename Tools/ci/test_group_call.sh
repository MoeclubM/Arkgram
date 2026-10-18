#!/usr/bin/env bash
set -euo pipefail

trap 'adb logcat -d -b crash > group-call-crash.log' EXIT
adb install -r TMessagesProj_AppTests/build/outputs/apk/afat/debug/*.apk
adb shell pm grant moe.telecom.arkgram.web android.permission.RECORD_AUDIO
./gradlew :TMessagesProj_AppTests:connectedAfatDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.telegram.messenger.voip.GroupCallInitializationTest \
  --stacktrace
