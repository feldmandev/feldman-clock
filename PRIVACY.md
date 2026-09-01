# Privacy Policy

**App:** Clock (`com.feldman.clock`)
**Last updated:** 7 August 2026

Clock is an offline-first clock, alarm, timer and stopwatch app. It has **no accounts, no
analytics, no advertising, no tracking SDKs and no crash-reporting SDK**. Nothing you do in
the app is sent anywhere unless it is listed below.

---

## What stays on your device

All of the following is stored only in the app's private storage and is never transmitted:

- Alarms, including titles, times, repeat schedules and chosen ringtones
- Timers and stopwatch state, including lap times
- Your saved world-clock cities
- All settings, themes and widget configuration

This data is included in Android's system backup if you have backup enabled in your device
settings. That backup goes to your own Google account under Google's terms — the developer
has no access to it. You can disable it in **Settings → Google → Backup**.

---

## What leaves your device

Clock makes network requests for exactly two optional features.

### 1. Weather (optional)

If you grant the location permission, the clock screen shows current conditions.

- **What is sent:** an approximate latitude/longitude, to
  [Open-Meteo](https://open-meteo.com) (`api.open-meteo.com`).
- **Accuracy:** the app requests **coarse** location only (roughly city-level). It never
  requests fine/GPS location.
- **For saved cities:** the city name you added is sent to
  `geocoding-api.open-meteo.com` to resolve its coordinates.
- **Open-Meteo's policy:** Open-Meteo states it does not require an API key and does not
  track users. See <https://open-meteo.com/en/terms>.
- **Declining:** if you deny the location permission, the app works normally and simply does
  not show local weather. You can revoke it at any time in
  **Android Settings → Apps → Clock → Permissions**.

### 2. World-clock city list (optional)

When you search for a city to add, the app downloads a public, static city/timezone list from
`raw.githubusercontent.com`.

- **What is sent:** an ordinary HTTPS request for a public file. Your search text is **not**
  sent — filtering happens on your device after the list is downloaded.
- GitHub will see your IP address, as it would for any web request.

No other network requests are made. All traffic uses HTTPS; the app's network security
configuration rejects plain-text connections outright.

---

## Permissions and why they are requested

| Permission | Why |
| --- | --- |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Alarms must fire at the exact minute you set |
| `POST_NOTIFICATIONS` | Alarm, timer and stopwatch notifications |
| `USE_FULL_SCREEN_INTENT` | Show the alarm screen when your phone is locked |
| `FOREGROUND_SERVICE` (+ `MEDIA_PLAYBACK`, `SPECIAL_USE`) | Keep alarms ringing and timers/stopwatch counting while the app is in the background |
| `WAKE_LOCK`, `TURN_SCREEN_ON` | Wake the screen for a firing alarm |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule your alarms after a reboot |
| `VIBRATE` | Alarm and timer vibration |
| `MODIFY_AUDIO_SETTINGS`, `ACCESS_NOTIFICATION_POLICY` | Raise alarm volume and ring through Do Not Disturb |
| `ACCESS_COARSE_LOCATION` | **Optional.** Local weather only (see above) |
| `INTERNET`, `ACCESS_NETWORK_STATE` | **Optional.** Weather and city list only |
| `SYSTEM_ALERT_WINDOW` | **Optional.** Requested during onboarding to help the alarm screen appear reliably over other apps |
| Camera flash (via `CameraManager`) | **Optional.** Flashlight pulse when an alarm fires. No camera images are ever captured or accessed |

---

## Children

Clock is not directed at children and collects no personal information from anyone.

## Changes

Any change to this policy will be published in this file in the app's public repository, with
the date above updated.

## Contact

Questions or concerns: open an issue at
<https://github.com/feldmandev/feldman-clock/issues>.
