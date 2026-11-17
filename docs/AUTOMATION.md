# Connecting Nightfall to automations

Nightfall doesn't need any of this to work. Every connection is optional, and details are entered in the app in the **Settings** tab, so it works with anyone's Home Assistant or n8n.

## Before you start

1. Open Nightfall and allow notifications and background start. Android won't let the app start itself without the second one.
2. On Samsung phones, also add Nightfall to **Settings > Battery > Background usage limits > Never sleeping apps**.

## Sending commands to the app

Commands are Android broadcasts sent to the app package, `com.cstone.nightfall`. Every command must include the automation key shown in the **Settings** tab. Anything else is ignored and logged as `REJECTED`.

**Allow remote control** in the **Settings** tab turns all of these commands off. Nothing starts or changes, and a `START` reports `remote_off` so your automation can stop there. Controls in the app and its notification keep working.

| Action | What it does | Extras |
|---|---|---|
| `<package>.START` | Starts tonight's mix | `key`, plus optional `secs` (0 means no timer), `gain` (1 to 100) `mix` (a mix name), `session` (a guided session: `slow_countdown`, `letting_go`, `body_scan` or `deep_rest`), `speaker` (a Wi-Fi speaker's room name, or `phone`) and `volume` (the phone's media volume for this start, 1 to 100) |
| `<package>.STOP` | Stops playback | `key` |
| `<package>.TOGGLE` | Pauses or resumes | `key` |
| `<package>.ADD15` | Adds 15 minutes to the timer | `key` |

### From Home Assistant

The Companion app delivers the broadcast. **Copy start command** in Settings gives you this with your key filled in:

```yaml
action: notify.mobile_app_YOUR_PHONE
data:
  message: command_broadcast_intent
  data:
    ttl: 0
    priority: high
    intent_package_name: <package>
    intent_action: <package>.START
    intent_extras: key:<your key>
```

Add extras to the same line, separated by commas, such as `key:<your key>,secs:2700,volume:35`. Numbers can be sent as plain text.

`ttl: 0` and `priority: high` make the push wake a sleeping phone at once. Without them it can wait for the phone's next check-in.

### From n8n

Use a Home Assistant node to call that same notify action. Without Home Assistant, n8n needs its own route to the phone, such as Tasker or a push service. That isn't built in yet.

### One-tap fallback

`nightfall://start` opens the app and starts playback. It works even when a background start isn't allowed, because the app is on screen. From Home Assistant, add it to a notification as an action button:

```yaml
data:
  actions:
    - action: URI
      title: Start sounds
      uri: deep-link://nightfall://start
```

## Getting events from the app

Fill in either or both targets in the **Settings** tab, and use the test button to check them.

- **Home Assistant:** server URL plus the ID from an automation's Webhook trigger.
- **n8n or any webhook:** a URL, plus an optional header name and value.

Each event is a JSON POST:

```json
{
  "app": "nightfall",
  "event": "started",
  "at": 1793540000000,
  "device": "SM-F966U1",
  "target_sdk": 36,
  "ok": true,
  "focus": 1,
  "output": "phone",
  "seconds_left": 2697,
  "ends_at": 1793542700000
}
```

| Event | Meaning |
|---|---|
| `started` | Audio is confirmed running |
| `start_failed` | It didn't start. `reason` is `background_start_denied`, `foreground_denied` or `audio_not_confirmed` |
| `remote_off` | A start was ignored because **Allow remote control** is off |
| `speaker_failed` | The Wi-Fi speaker couldn't be reached, so the mix is playing on the phone instead. A `started` event follows |
| `speaker_lost` | The Wi-Fi speaker stopped playing, either from its own app or because it dropped off the network, so the session ended |
| `paused`, `resumed`, `time_added` | A control was used |
| `guided_finished` | A guided session's voice ended. Sounds may keep playing |
| `timer_ended` | The sleep timer finished |
| `stopped` | Playback was stopped |
| `test` | Sent by a test button |

If the phone can't start at all, it may not be able to report that. Treat a missing `started` event after about 20 seconds as a failure.

## Phone volume

Nightfall plays at whatever media volume the phone was left on, unless **Set phone volume at start** is on (under **Change output**). Then every start, including one from an automation, sets the media volume first. A `volume` extra on the start command does the same for that one start, whether the setting is on or not.

When the session ends, the volume goes back to what it was, unless someone changed it during the session. With a Wi-Fi speaker it only affects a guided session's voice, which plays on the phone.

## Playing on other speakers

**Change output** searches for speakers when it opens.

- **Wi-Fi:** Sonos and DLNA speakers. Pick one and every start plays there, including starts from an automation. The phone makes the sound and streams it to the speaker, so it has to stay on the same network, and it uses more battery than playing on its own speaker. Timers and fades work the same.
- **Bluetooth:** connected speakers can be picked straight away. Nearby ones can be paired from the list. Android doesn't let apps connect a speaker that's already paired, so those open Bluetooth settings.

Google Cast and AirPlay speakers aren't supported. A guided session's voice always plays on the phone, even when its sounds play on a Wi-Fi speaker.

## A worked example: Home Assistant and n8n together

This is one way to connect them. Adjust the names for your own setup.

1. Create two helpers: an on/off switch for autostart, and a text helper to hold Nightfall's state.
2. In n8n, make a webhook that takes Nightfall's events, checks the key header, and copies `event` into the text helper with a Home Assistant node.
3. Point **n8n or any webhook** under Connections at that URL, with the key header.
4. Create a Home Assistant script that:
   - sets the text helper to `starting`
   - sends the start command above
   - waits up to 25 seconds for the helper to change to `started`, `start_failed` or `remote_off`
   - sends the one-tap notification if it's neither `started` nor `remote_off`
5. Run that script from your bedtime routine, only when the autostart switch is on.
