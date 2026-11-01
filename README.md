# Nightfall

An Android sleep sounds app that can start itself at bedtime, with optional Home Assistant and n8n connections.

Status: early but usable. The app in `app/` has the Tonight screen, a mixer with 18 sounds generated live on the phone, a sleep timer with fade, saved mixes, a media notification, night dim, breathing, and optional Home Assistant and n8n connections.

Not built yet: guided meditation audio, room speakers such as Sonos, widgets, and a scheduled backup start.

## Layout

- `app/` is the Nightfall app.
- `docs/AUTOMATION.md` covers commands, events and setup.
- `design/` holds the UI proposal.
- `spike/autostart/` is the test app that proved auto-start on Android 17.

## Test results so far (Galaxy Z Fold 7, Android 17)

| Build | Battery limits | Result |
|---|---|---|
| Target API 36 | On | Refused: the foreground service can't start from the background |
| Target API 37 | On | Refused the same way |
| Target API 36 | Off | Starts and plays |
| Target API 37 | Off | Muted by Android, while writes and the playback position still look normal |

The app must stay on target API 36 for unattended start, and it has to treat a failed audio focus request as a failed start.

A full start from Home Assistant through the Companion app has also worked: the app confirmed audio in about 3 seconds and reported it to n8n. That test ran with the screen on and the phone unplugged. Screen off, charging and overnight runs are still to do.

## Build

Run `gradlew assembleDebug` in the repo root, or open it in Android Studio.

Run `gradlew :app:testDebugUnitTest -Proborazzi.test.record=true` for the tests. Screen images land in `app/build/screens`.

The Manrope font is under the SIL Open Font License.
