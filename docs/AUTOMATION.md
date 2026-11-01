# Connecting Nightfall to automations

Nightfall doesn't need any of this to work. Every connection is optional, and details are entered in the app in the **Settings** tab, so it works with anyone's Home Assistant or n8n.

## Before you start

1. Open Nightfall and allow notifications and background start. Android won't let the app start itself without the second one.
2. On Samsung phones, also add Nightfall to **Settings > Battery > Background usage limits > Never sleeping apps**.

## Sending commands to the app

Commands are Android broadcasts sent to the app package, `com.cstone.nightfall`. Every command must include the automation key shown in the **Settings** tab. Anything else is ignored and logged as `REJECTED`.

| Action | What it does | Extras |
|---|---|---|
| `<package>.START` | Starts tonight's mix | `key`, plus optional `secs` (0 means no timer), `gain` (1 to 100) and `mix` (a mix name) |
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
    intent_package_name: <package>
    intent_action: <package>.START
    intent_extras: key:<your key>
```

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
  "seconds_left": 2697,
  "ends_at": 1793542700000
}
```

| Event | Meaning |
|---|---|
| `started` | Audio is confirmed running |
| `start_failed` | It didn't start. `reason` is `background_start_denied`, `foreground_denied` or `audio_not_confirmed` |
| `paused`, `resumed`, `time_added` | A control was used |
| `timer_ended` | The sleep timer finished |
| `stopped` | Playback was stopped |
| `test` | Sent by a test button |

If the phone can't start at all, it may not be able to report that. Treat a missing `started` event after about 20 seconds as a failure.

## A worked example: Home Assistant and n8n together

This is one way to connect them. Adjust the names for your own setup.

1. Create two helpers: an on/off switch for autostart, and a text helper to hold Nightfall's state.
2. In n8n, make a webhook that takes Nightfall's events, checks the key header, and copies `event` into the text helper with a Home Assistant node.
3. Point **n8n or any webhook** under Connections at that URL, with the key header.
4. Create a Home Assistant script that:
   - sets the text helper to `starting`
   - sends the start command above
   - waits up to 25 seconds for the helper to change to `started` or `start_failed`
   - sends the one-tap notification if it isn't `started`
5. Run that script from your bedtime routine, only when the autostart switch is on.
