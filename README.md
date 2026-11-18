# Nightfall

An Android sleep sounds app that can start itself at bedtime, with optional Home Assistant and n8n connections.

Status: early but usable. The app in `app/` has the Tonight screen, a mixer with 29 stereo sounds generated live on the phone, a sleep timer with fade, an optional phone volume for each start, saved mixes, a media notification, night dim, breathing, four guided sessions, a search for Wi-Fi and Bluetooth speakers, and optional Home Assistant and n8n connections. Sonos and DLNA speakers play a live stream from the phone.

Not built yet: Google Cast and AirPlay speakers, and widgets.

The app has no bedtime schedule of its own. Automatic starts come only from an outside automation, such as Home Assistant or n8n. See `docs/AUTOMATION.md`.

## Layout

- `app/` is the Nightfall app.
- `docs/AUTOMATION.md` covers commands, events and setup.
- `guided/` holds the meditation scripts and the script that voices them.
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

To change a guided session, edit its script in `guided/scripts` and run `guided/render.py`. Audio is rendered once with the Kokoro voice model (Apache 2.0), on your own computer, so playing a session never uses an API.

The Manrope font is under the SIL Open Font License.
