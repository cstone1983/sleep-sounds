# Nightfall

An Android sleep sounds app that can start itself at bedtime, with optional Home Assistant and n8n connections.

Status: early. `spike/autostart` is a small test app proving that sound can start from an automation on Android 17. The full app isn't built yet.

## Layout

- `design/` holds the UI proposal: PNG renders in `renders/`, canvas source in `source/`.
- `spike/autostart/` is the auto-start test app.

## Test results so far (Galaxy Z Fold 7, Android 17)

| Build | Battery limits | Result |
|---|---|---|
| Target API 36 | On | Refused: the foreground service can't start from the background |
| Target API 37 | On | Refused the same way |
| Target API 36 | Off | Starts and plays |
| Target API 37 | Off | Muted by Android, while writes and the playback position still look normal |

The app must stay on target API 36 for unattended start, and it has to treat a failed audio focus request as a failed start.

## Build

Open `spike/autostart` in Android Studio, or run `gradlew assembleDebug` there.
