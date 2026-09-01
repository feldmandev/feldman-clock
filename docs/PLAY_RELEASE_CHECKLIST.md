# Play Store release checklist

Working notes for publishing `com.feldman.clock`. Not user-facing documentation.

---

## Every release

1. Bump `versionCode` **and** `versionName` in [`app/build.gradle.kts`](../app/build.gradle.kts).
   Scheme: `major * 10000 + minor * 100 + patch` (1.0.0 → 10000).
2. Build the bundle — Play requires an `.aab`, not an `.apk`:
   ```bash
   ./gradlew :app:bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/Clock-release.aab`
3. Upload `app/build/outputs/mapping/release/mapping.txt` alongside it. Release builds are
   minified, so without this every crash report in Play Console is unreadable.
4. Smoke-test the **minified** build on a device before uploading — a debug build proves
   nothing about R8:
   ```bash
   ./gradlew :app:assembleRelease
   adb install -r app/build/outputs/apk/release/Clock_1.0.0-release.apk
   ```
   Exercise at minimum: add a world-clock city, kill the app, reopen and confirm the city
   and its weather are still there (this is the Gson + R8 path that breaks silently), then
   set an alarm and let it fire.

---

## Data Safety form

Answers matching the current code. Re-check whenever a network call is added.

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** — location only |
| Location → Approximate location | Collected: **Yes**. Shared: **No** |
| Purpose | **App functionality** (weather on the clock screen) |
| Is it required or optional? | **Optional** — the app is fully usable if denied |
| Is data processed ephemerally? | **Yes** — coordinates are sent to Open-Meteo per request and never stored |
| Is data encrypted in transit? | **Yes** — HTTPS enforced by `network_security_config.xml` |
| Can users request deletion? | No data is retained, so nothing to delete |
| Personal info / financial / health / messages / photos / files / contacts / calendar / app activity / web browsing / app info & performance / device IDs | **None collected** |

Privacy policy URL: host [`PRIVACY.md`](../PRIVACY.md) at a stable public URL (GitHub Pages,
or link the raw file) and paste it into **App content → Privacy policy**.

---

## Declarations Play will ask for

| Item | Status / answer |
| --- | --- |
| **Exact alarms** (`USE_EXACT_ALARM`) | Required. Declare as an **alarm clock app** — this is the permitted use case, and the app's core function is alarms. |
| **Full-screen intent** (`USE_FULL_SCREEN_INTENT`) | Required, for the alarm firing screen on a locked device. Same justification. |
| **Foreground services** | `mediaPlayback` (alarm ringtone), `specialUse` (timer countdown, stopwatch). For `specialUse` Play requires a written justification — the manifest `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` values are the text to reuse. |
| **`QUERY_ALL_PACKAGES`** | **Removed.** It was declared but never used. Do not re-add — Play restricts it to a narrow allowlist that a clock app does not qualify for. |
| **Location** | Approximate only, optional, foreground only. No background location. |
| **`SYSTEM_ALERT_WINDOW`** | ⚠️ Requested during onboarding but the app never creates an overlay window. See "Open items". |
| **Ads** | None |
| **Target audience** | Not directed at children |
| **Government app** | No |
| **Financial features** | None |

---

## Store listing assets still needed

None of these exist in the repo yet:

- App icon 512×512 PNG
- Feature graphic 1024×500
- At least 2 phone screenshots (4–8 recommended); 7-inch and 10-inch tablet screenshots if
  you declare tablet support
- Short description (≤80 chars) and full description (≤4000 chars)
- The old README referenced `fastlane/metadata/android/en-US/` — that directory does not
  exist here. If you want F-Droid-style metadata alongside Play, create it.

---

## Open items

Things deliberately left alone, with the reasoning.

- **`SYSTEM_ALERT_WINDOW`** — `SetupPage.kt` asks for it during onboarding, but the only use
  is a `Settings.canDrawOverlays()` check; no overlay is ever created. The alarm screen works
  via `USE_FULL_SCREEN_INTENT` + `showWhenLocked`. Dropping it would remove a sensitive
  permission and an onboarding step, but that is a product decision, not a mechanical fix.
- **APK size (~21 MB)** — about 19.6 MB is fonts arriving transitively from
  `io.github.feldmandev:motion`: `material_symbols_rounded.ttf` (14.7 MB) and
  `feldman_font.ttf` (4.9 MB). The resource shrinker keeps them because library code
  references `com.feldman.motion.R$font.*` directly. Subsetting those fonts in the `motion`
  repo would cut the app to roughly 2–3 MB. This is the single largest remaining win.
- **`material3 = "1.5.0-alpha25"` and `motion = "beta1"`** — pre-release dependencies in a
  production build. Alpha Compose APIs can change or regress between builds.
- **`minSdk = 34`** — Android 14+ only, which excludes most of the installed base. Confirmed
  intentional.
- **World-clock city list** is fetched at runtime from a third-party GitHub repo's `master`
  branch (`kevinroberts/city-timezones`). If that repo is renamed or deleted, city search
  breaks. Consider vendoring the JSON or pinning a commit SHA.
- **Lint** still reports ~946 errors, nearly all `StringFormatCount` / `MissingTranslation`
  noise inherited from upstream. The four Play-blocking checks are now `fatal` in
  `app/build.gradle.kts` and pass clean.
