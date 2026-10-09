# AutoFlow – architecture & platform analysis

AutoFlow is a privacy-first Android automation app built around one idea:

```
WHEN <trigger> → IF <condition tree> → THEN <action pipeline> → history / logs
```

This document covers the analysis requested before implementation: architecture,
Android API restrictions per feature, what is (not) possible on modern Android, the
database schema, the domain model and the extension interfaces.

---

## 1. Module structure

```
app                    Compose UI, Hilt wiring, foreground service, receivers, WorkManager, widget
core/model      (JVM)  Domain model + JSON codec (kotlinx.serialization). No Android.
core/engine     (JVM)  Automation engine, condition evaluator, trigger matcher, schedule
                       calculator, variables/templates, expressions, JSON paths, HTTP action.
                       Repository + platform interfaces. No Android → fully unit tested.
data/storage           Room database, DataStore settings, Android Keystore secret cipher,
                       repository implementations.
platform/permissions   Capability → permission / special-access mapping, rationale texts.
platform/triggers      Trigger sources (Wi-Fi, Bluetooth, battery, charger, headphones,
                       app usage), AlarmManager scheduler, device state provider.
platform/actions       Action handlers (notification, launch app, brightness, volume, DND,
                       TTS, sound, vibration, settings panels, OkHttp transport).
```

Dependency direction: `app → platform/*, data/storage → core/engine → core/model`.
The engine never depends on Android, the UI never talks to platform code directly
(only through the engine, repositories and `PermissionManager`).

The suggested `variable-system`, `notification-system`, `condition-system` and
`location-system` modules are packages for now (`core/engine/variable`,
`core/engine/condition`, `platform/actions`); they can be split into Gradle modules
when they grow. `widgets` and `ui` live in `app` because they share the Hilt graph.

### Runtime flow

```
 AlarmManager ──► TimeTriggerReceiver ──► WorkManager (AutomationRunWorker) ─┐
 Play services geofences ──► GeofenceReceiver ──► WorkManager ─────────────────┤
 Wi-Fi / BT / battery / charger / headphones / app usage                      │
   └─► TriggerSource (inside AutomationMonitorService, foreground) ───────────┤
 Widget button / Home quick action / Run now ─────────────────────────────────┤
                                                                              ▼
                                                                    AutomationEngine
                                     master switch → TriggerMatcher → ConditionEvaluator
                                                    → ActionPipeline → ActionRegistry handlers
                                                    → ExecutionRepository (history)
```

`TriggerCoordinator` observes stored automations + the master switch and keeps OS
registrations in sync: alarms for time triggers, and the monitoring service only while
an enabled automation needs a runtime-registered source.

---

## 2. Extension interfaces

```kotlin
interface TriggerSource {                       // core/engine/trigger
    val family: TriggerFamily
    fun register(onEvent: (TriggerEvent) -> Unit)
    fun unregister()
}

fun interface ActionHandler<in A : ActionSpec> { // core/engine/action
    suspend fun execute(action: A, context: AutomationContext): ActionResult
}

fun interface LeafConditionEvaluator<in C : ConditionNode> {
    fun evaluate(condition: C, environment: ConditionEnvironment): Boolean
}

fun interface DeviceStateProvider { suspend fun snapshot(): DeviceState }
fun interface HttpExecutor { suspend fun execute(call: HttpCall): HttpResponse }
interface AutomationRepository / ExecutionRepository / VariableRepository / EngineSettings
```

Adding an action = add an `ActionSpec` subtype (with `@SerialName`), a handler, register
it in `PlatformActions`, plus UI text/icon/form. The engine itself is unchanged.

---

## 3. Domain model (core/model)

| Type | Purpose |
|---|---|
| `Automation` | id, name, description, enabled, triggers (OR), condition tree, actions, stopOnError, quickAction, timestamps |
| `TriggerSpec` (sealed) | `TIME`, `INTERVAL`, `LOCATION_ENTER`, `LOCATION_EXIT` (with a `GeoPlace`: name, lat, lng, radius), `NOTIFICATION_RECEIVED`, `CALENDAR_EVENT_START`, `CALENDAR_EVENT_END`, `SUN_EVENT`, `WIFI_CONNECTED/DISCONNECTED`, `BLUETOOTH_CONNECTED/DISCONNECTED`, `BATTERY_LEVEL`, `CHARGER_CONNECTED/DISCONNECTED`, `APP_OPENED`, `HEADPHONES_CONNECTED/DISCONNECTED` |
| `ConditionNode` (sealed tree) | `AND`, `OR`, `NOT`, `TIME_RANGE`, `DAYS_OF_WEEK`, `BATTERY_LEVEL`, `CHARGING`, `WIFI_STATE`, `BLUETOOTH_STATE`, `HEADPHONES_STATE`, `VARIABLE` |
| `ActionSpec` (sealed) | `WHILE`, `WAIT_UNTIL`, `STOP`, `VARIABLE_OPERATION`, `PARSE_JSON`, `NOTIFICATION`, `DISMISS_NOTIFICATIONS`, `LAUNCH_APP`, `OPEN_URL`, `OPEN_SETTINGS`, `SET_BRIGHTNESS`, `SET_VOLUME`, `DO_NOT_DISTURB`, `SPEAK`, `PLAY_SOUND`, `VIBRATE`, `HTTP_REQUEST`, `DELAY`, `SET_VARIABLE`, `IF_ELSE`, `REPEAT` |
| `TriggerEvent` | Something that happened (with details exposed as `%trigger_*%`) |
| `Variable` | `$name` user variable, optionally secret (encrypted, never exported) |
| `ExecutionRecord` / `ExecutionStep` | History: status (SUCCESS / PARTIAL / FAILED / SKIPPED) and per-step log |
| `ActionResult` | `Success`, `Fallback` (did the supported alternative), `Failure(kind)`, `Skipped` |
| `Capability` | OS capability required by a spec (drives the permission flow) |

### Variables

* `%notification_app%`, `%notification_title%`, `%notification_text%`, `%event_title%`, `%event_location%` (from the triggering event), `%latitude%`, `%longitude%`, `%location%` (last known location, only with permission), `%battery%`, `%time%`, `%date%`, `%datetime%`, `%day%`, `%wifi%`, `%ssid%`, `%bluetooth%`,
  `%headphones%`, `%charging%`, `%volume%`, `%brightness%`, `%device%`, `%android%`,
  `%automation%`, `%trigger%`, `%trigger_<detail>%` (e.g. `%trigger_ssid%`, `%trigger_package%`).
  The trailing `%` is optional (`%battery`).
* `$name` – user variables (global, persisted) or local ones (this run only).
  JSON paths work on any variable: `$http.data.items.0.name`.
* `Set variable` can evaluate arithmetic (`$counter + 1`), `VARIABLE` conditions compare
  numerically when possible, otherwise as text, `contains` or regex.

### JSON format (export / import / storage)

```json
{
  "format": "autoflow.automations",
  "version": 1,
  "automations": [{
    "id": "…", "name": "Office Mode", "enabled": true,
    "triggers": [{ "type": "WIFI_CONNECTED", "ssid": "Office-WiFi" }],
    "condition": { "type": "AND", "children": [
        { "type": "TIME_RANGE", "start": "08:00", "end": "18:00" },
        { "type": "DAYS_OF_WEEK", "days": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"] } ] },
    "actions": [
      { "type": "SET_VOLUME", "stream": "MEDIA", "percent": 60 },
      { "type": "LAUNCH_APP", "packageName": "com.microsoft.teams" } ]
  }],
  "variables": [{ "name": "officeWifi", "value": "Company-WiFi" }]
}
```

A single automation object is also accepted. Unknown fields are ignored, unknown types
are rejected with a clear message, imported automations start disabled and get new ids.

---

## 4. Database schema (Room, `autoflow.db`, version 1)

| Table | Columns |
|---|---|
| `automations` | `id` PK, `name`, `description`, `enabled`, `triggers_json`, `condition_json` (nullable), `actions_json`, `stop_on_error`, `quick_action`, `created_at`, `updated_at` |
| `variables` | `name` PK, `value` (ciphertext when secret), `secret`, `updated_at` |
| `executions` | `id` PK, `automation_id` (idx), `automation_name`, `trigger_key`, `trigger_detail`, `status`, `started_at` (idx), `finished_at` |
| `execution_steps` | `id` PK auto, `execution_id` FK → executions ON DELETE CASCADE (idx), `position`, `kind`, `key`, `status`, `message`, `failure_kind`, `timestamp` |

Triggers, conditions and actions are recursive trees (AND/OR/NOT, If/Else, Repeat), so they
are stored as JSON columns using the same versioned codec as export. Rows that cannot be
decoded are skipped instead of crashing. History keeps the latest 1000 runs.

Settings (master switch, theme, dynamic color, log skipped runs) use DataStore Preferences.
Templates are code (`TemplateCatalog`) so they can be localized; a user template table can be
added later without migration of existing data. Schemas are exported to
`data/storage/schemas` for future migrations.

---

## 5. Android APIs and restrictions per feature

Legend: ✅ fully supported · 🔐 needs a runtime permission or special access ·
⚠️ supported with limits · ⛔ not possible for a regular app (alternative provided)

### Triggers (MVP)

| Trigger | API | Status | Notes |
|---|---|---|---|
| Sunrise / sunset (± offset, days) | NOAA solar equations (engine) + `AlarmManager` | ✅ | Computed offline from coordinates stored with the trigger (picked once with "use current location" or typed). Polar day/night skips to the next real sunrise/sunset. Accuracy ≈ 1 min. |
| Time / days / interval | `AlarmManager.setExactAndAllowWhileIdle` | ✅ / 🔐 | Exact needs "Alarms & reminders" (`SCHEDULE_EXACT_ALARM`, denied by default on 14+). Falls back to inexact `setAndAllowWhileIdle`. Alarms are re-created after boot, update, time/zone change. |
| Wi-Fi connected/disconnected | `ConnectivityManager.registerNetworkCallback` | ✅ / 🔐 | Requires a running process → foreground service. SSID needs `ACCESS_FINE_LOCATION` (+ background location to read it while not visible) – an Android rule. |
| Bluetooth device connected | `ACTION_ACL_CONNECTED/DISCONNECTED` | 🔐 | `BLUETOOTH_CONNECT` on Android 12+. |
| Battery level / charger | `ACTION_BATTERY_CHANGED`, `ACTION_POWER_(DIS)CONNECTED` | ✅ | Registered-receiver-only broadcasts since Android 8 → foreground service. Threshold fires on crossing. |
| Headphones | `AudioManager.registerAudioDeviceCallback` | ✅ | Wired, USB and Bluetooth (A2DP / LE) without permissions. |
| Arrive at / leave a place | Play services `GeofencingClient` | 🔐 ⚠️ | Needs fine **and** background location ("Allow all the time"). Delivered to a broadcast receiver by the system — no monitoring service. Android detects transitions with low power, so they can be a few minutes late; radius ≥ 100 m. Geofences are re-registered after boot, app update, location toggling (`PROVIDERS_CHANGED`) and whenever the app opens; no initial trigger, so re-registering never fires "arrive" again. |
| Notification received (any app / specific app / text) | `NotificationListenerService` | 🔐 | User enables "Notification access". The system binds the listener itself (no foreground service). Android 13+ blocks this for sideloaded apps until "Allow restricted settings" is enabled in App info — the permission card explains it. Ongoing notifications, group summaries and silent updates are ignored; content is available to actions as variables but **never written to history**. |
| Calendar event starts / ends (title filter) | `CalendarContract.Instances` + `AlarmManager` | 🔐 | `READ_CALENDAR`. Exact alarm at the next matching start/end (recurring events expanded by the provider, 8-day look-ahead); re-scheduled after each alarm, hourly via WorkManager, on boot and when the app opens. |
| App opened | `UsageStatsManager.queryEvents` | 🔐 ⚠️ | Needs Usage access. No broadcast exists, so it polls every 2 s while the screen is on. AccessibilityService is deliberately **not** used. |

**Background execution:** monitoring uses a `specialUse` foreground service with a visible,
low-priority notification, started only while an enabled automation needs it and stopped
by the master switch. Android 12+ may refuse to start it from the background; the app then
posts a "tap to resume" notification and restarts it the next time the UI is opened.
Time triggers and widget runs go through WorkManager (expedited when quota allows).

### Actions (MVP)

| Action | API | Status | Notes |
|---|---|---|---|
| Notification | `NotificationManagerCompat` | 🔐 | `POST_NOTIFICATIONS` on 13+. |
| Launch app / open URL / open settings panel | `startActivity` | ⚠️ | Background activity starts are blocked since Android 10. Allowed when AutoFlow is visible or (≤ Android 14) has "Display over other apps". Otherwise a tap-to-open notification is posted and the run is logged as *Partial*. |
| Brightness | `Settings.System.SCREEN_BRIGHTNESS` | 🔐 | "Modify system settings" special access. Switches to manual brightness. |
| Volume (media, ring, notification, alarm, call) | `AudioManager.setStreamVolume` | ✅ / 🔐 | Ring/notification volume while DND is on needs DND access. |
| Do Not Disturb | `NotificationManager.setInterruptionFilter` | 🔐 | Notification policy access. |
| Text-to-speech | `TextToSpeech` | ✅ | Waits until speech finishes. |
| Play sound | `MediaPlayer` + `RingtoneManager` default sounds | ✅ | Max duration enforced. |
| Vibrate | `Vibrator` / `VibratorManager` | ✅ | |
| Dismiss notifications | `NotificationListenerService.cancelNotification` | 🔐 | Only clearable notifications of other apps, optional app / text filter. |
| HTTP request | OkHttp | ✅ | GET/POST/PUT/PATCH/DELETE, headers, query, body, Basic/Bearer, timeout, response → variables, 1 MB cap. Cleartext HTTP is blocked by Android's default network policy. |
| Delay, variables, If/Else, Repeat, While (iteration cap), Wait until (timeout), Stop, variable operations (increment/decrement, append, replace, case, trim, substring, split, regex extract, length, URL-encode), Read JSON value | engine | ✅ | |

### Not possible for regular apps on modern Android (alternatives offered in the UI)

| Feature | Restriction | Alternative in AutoFlow |
|---|---|---|
| Toggle Wi-Fi | `setWifiEnabled` no-op since Android 10 | Open Wi-Fi panel (`Settings.Panel.ACTION_WIFI`) |
| Toggle Bluetooth | `BluetoothAdapter.enable()` deprecated/blocked since Android 13 | Open Bluetooth settings |
| Airplane mode | System apps only | Open airplane mode settings |
| Battery saver | `DEVICE_POWER` is signature-level | Open battery saver settings |
| Close other apps | `killBackgroundProcesses` only affects cached processes | Not offered |
| Lock / wake screen | Device admin / accessibility only | Planned via explicit, disclosed opt-in (post-MVP) |
| Covert camera / microphone / location | Forbidden by policy and by design | Never implemented |

### Post-MVP features and their constraints

| Feature | Constraint |
|---|---|
| SMS / calls | `RECEIVE_SMS`, `SEND_SMS`, `READ_PHONE_STATE`, `READ_CALL_LOG` – restricted by Google Play policy unless the app is a default handler or qualifies for an exception |
| Camera / microphone | Only from a visible activity or a `camera`/`microphone` foreground service with a visible notification; never hidden |
| Files | Storage Access Framework (user-picked folders) |
| Sunrise / sunset | Computable offline from coarse location |
| Webhook trigger | Needs a reachable endpoint (push service or local server) – privacy review required |

---

## 5b. Widgets

* **Quick actions** (3×3): master switch (tap to toggle), Wi-Fi and Bluetooth status (tap opens the
  system panel – apps cannot toggle them), refresh, and one button per automation marked
  *quick action*. Refreshed when automations or the master switch change, and every 30 min.
* **AutoFlow button** (1×1, resizable): bound to one automation chosen in a configuration screen
  when the widget is placed (reconfigurable on Android 12+). The binding is stored in Glance
  per-widget state. Taps run the automation through WorkManager.

## 6. Permissions

`PermissionManager` (platform/permissions) is the single place mapping each `Capability` to
runtime permissions or settings screens, SDK differences and user-facing rationale/denial
texts. Nothing is requested at startup. When the user adds a trigger/condition/action, the
builder shows a card for every missing capability: why it is needed, a Grant button
(runtime dialog or the right settings screen), and – if refused – what will not work.
Settings › Permissions lists every capability and its status.

---

## 7. Security & privacy

* No account, no analytics, no server. Data leaves the device only through HTTP actions the
  user configures.
* Secret variables are encrypted with AES-256-GCM using a non-exportable Android Keystore key
  and are never exported. Android backup / device transfer is disabled for app data.
* Every run is logged (audit log) with each step; individual automations and a master switch
  can disable everything.
* The monitoring service is always visible as a notification.
* No accessibility service, no hidden camera/microphone/location access.

---

## 8. Testing

* `core/model`, `core/engine`: JVM unit tests (engine orchestration, conditions incl.
  midnight-wrapping ranges and short-circuiting, trigger matching, DST-safe scheduling,
  templates/placeholders, expressions, JSON paths, HTTP action, concurrency, JSON codec).
* `data/storage`: Robolectric + in-memory Room (round trips, pruning, corrupt rows,
  encryption at rest).
* `platform/triggers`, `platform/actions`: Robolectric integration tests for battery/charger
  sources, alarm scheduling, volume, notification, brightness permission checks and the
  background-launch fallback.
* `app`: builder tree operations, templates validity, localization coverage.

CI (GitHub Actions) runs unit tests, assembles the debug APK, runs Robolectric tests and lint,
and uploads the APK as an artifact.
