# Third-Party Notices

Clock bundles or depends on the components below. This file is informational; each component
remains under its own licence.

---

## Derived source

| Project | Licence | Relationship |
| --- | --- | --- |
| [AOSP Desk Clock](https://android.googlesource.com/platform/packages/apps/DeskClock/) | Apache-2.0 | Ancestor of the alarm/timer/stopwatch data layer. Files retaining an Apache-2.0 header remain available under those terms. |
| [BlackyHawky/Clock](https://github.com/BlackyHawky/Clock) | GPL-3.0-only | Direct upstream of this fork. Source of the feature set, translations and much of `core/`. |
| [LineageOS DeskClock](https://github.com/LineageOS/android_packages_apps_DeskClock) | Apache-2.0 | App icon inspiration (icon modified by BlackyHawky). |

Translations were contributed by the upstream community via
[Weblate](https://translate.codeberg.org/projects/clock/) and are covered by the upstream
project's licence.

---

## Bundled fonts

| Font | File | Licence | Notes |
| --- | --- | --- | --- |
| Feldman Rounded | `app/src/main/res/font/feldman_rounded.ttf` | OFL-1.1 | A partial variable-font instance of `feldman_font.ttf` from the `motion` library, used for home-screen widgets (`RemoteViews` cannot use the library's font resource directly). See `WidgetTextRenderer.kt` for the subsetting command. |
| Feldman Font (web) | `fonts/FeldmanFont-Rond100.woff2` | OFL-1.1 (see `fonts/OFL.txt`) | Used only by `privacy-policy.html`, not by the app. Derived from [Fredoka](https://github.com/hafontia/Fredoka-One). |

The `motion` library additionally bundles `material_symbols_rounded.ttf`
([Material Symbols](https://github.com/google/material-design-icons), Apache-2.0) and
`feldman_font.ttf` (OFL-1.1), which are packaged into the APK transitively.

The app's primary typeface is supplied by the `motion` library; no proprietary font is
bundled in this repository.

---

## Libraries

| Library | Licence |
| --- | --- |
| AndroidX (Compose, Lifecycle, Navigation, DataStore, Media3, Preference, RecyclerView) | Apache-2.0 |
| [Material Components for Android](https://github.com/material-components/material-components-android) | Apache-2.0 |
| [Kotlin](https://kotlinlang.org) / kotlinx.serialization | Apache-2.0 |
| [Retrofit](https://github.com/square/retrofit) + converter-gson | Apache-2.0 |
| [Gson](https://github.com/google/gson) | Apache-2.0 |
| [OkHttp](https://github.com/square/okhttp) (transitive via Retrofit) | Apache-2.0 |
| [hsv-alpha-color-picker-android](https://github.com/martin-stone/hsv-alpha-color-picker-android) | Apache-2.0 |
| [io.github.feldmandev:motion](https://central.sonatype.com/artifact/io.github.feldmandev/motion) | Apache-2.0 (bundled fonts: OFL-1.1) |

---

## Network services

Clock talks to these third-party services. Neither requires an account or API key.

| Service | Used for | Terms |
| --- | --- | --- |
| [Open-Meteo](https://open-meteo.com) | Weather forecast and geocoding | <https://open-meteo.com/en/terms> — free for non-commercial use, CC-BY-4.0 data |
| [kevinroberts/city-timezones](https://github.com/kevinroberts/city-timezones) via `raw.githubusercontent.com` | World-clock city/timezone list | MIT |

See [PRIVACY.md](PRIVACY.md) for exactly what is sent to each.
