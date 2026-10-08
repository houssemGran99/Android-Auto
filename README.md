# AutoFlow

A modern, privacy-first Android automation app — a simpler alternative to Tasker.

```
WHEN  Bluetooth headphones are connected
IF    time is between 07:00 and 22:00
THEN  set media volume to 70% · launch Spotify · show "Music mode activated"
```

* Visual builder: triggers, an AND / OR / NOT condition tree, drag-and-drop action list
  with nested If/Else and Repeat
* Triggers: time, interval, arrive at / leave a place (geofences), Wi-Fi, Bluetooth, battery level, charger, headphones, app opened
* Actions: notification, launch app, open URL, settings panels, brightness, volume,
  Do Not Disturb, text-to-speech, sound, vibration, HTTP requests, wait, variables, if/else, repeat
* Variables (`%battery%`, `$myVar`, JSON paths `$http.data.temp`), arithmetic
* Execution history with a step-by-step log, templates, global search, JSON export / import,
  home-screen widgets (quick actions with master switch and Wi-Fi/Bluetooth status, and a one-tap
  button per automation), master switch, dark / light / dynamic theme
* Permissions are only requested when an automation needs them, with an explanation

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the architecture, the database schema
and a feature-by-feature analysis of what Android allows.

## Build & install

Requirements: JDK 17+, Android SDK (API 35). Then:

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # install on a connected device
./gradlew test                   # engine + Robolectric tests
```

Every push also builds the APK on GitHub Actions: download `autoflow-debug-apk` from the
workflow run and install it with `adb install app-debug.apk` (or open it on the phone).

## Try the MVP scenario on a phone

1. Open **Templates → Music mode → Use template** (or build it by hand in the builder).
2. Grant what the builder asks for: *Notifications*. Optionally *Display over other apps*
   (Android ≤ 14) so Spotify opens directly; otherwise you get a tap-to-open notification —
   Android blocks apps from opening other apps in the background.
3. Save. The ongoing "Automations active — Watching headphones" notification appears.
4. Connect Bluetooth (or wired) headphones between 07:00 and 22:00.
5. Check **History** for the step-by-step log.

Tip: set battery optimization to *Unrestricted* (Settings → Battery optimization) on phones
that aggressively kill background services.

## Project layout

| Module | Contents |
|---|---|
| `core/model` | Domain model + JSON format (pure Kotlin) |
| `core/engine` | Automation engine, conditions, variables, scheduling, HTTP (pure Kotlin) |
| `data/storage` | Room, DataStore, Keystore encryption |
| `platform/permissions` | Capability → permission mapping and explanations |
| `platform/triggers` | Trigger sources, alarms, device state |
| `platform/actions` | Action handlers |
| `app` | Compose UI, DI, background runtime, widget |
