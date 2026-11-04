# XroTune

An Android music client for YouTube, written in Kotlin with Jetpack Compose and
Material 3. Package `com.xrotune.app`, minSdk 26, targetSdk 35.

Search and browsing work without signing in. Audio playback is gated by the
InnerTube player endpoint for unauthenticated callers, so the app tries several
client identities in order and keeps the first one that returns a playable
stream.

## Download

Prebuilt debug APK: [releases](https://github.com/xpro-004/xrotune-android/releases)

Debug-signed, so the phone must allow installs from unknown sources.

## Build

```bash
# Requires JDK 17+ and the Android SDK (platform 35, build-tools 35.0.0)
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Layout

```
app/src/main/java/com/xrotune/app/
├── MainActivity.kt          Navigation, tabs, mini-player, top bar
├── XroTuneApp.kt            Application: repository + session singletons
├── auth/                    Google sign-in and session persistence
├── data/api/                InnerTubeClient, ResponseParser, ApiConfig
├── data/model/              Track, MusicSection, PlayableMedia, exceptions
├── data/repo/               MusicRepository (search, browse, player)
├── player/                  Media3 PlaybackService + controller
├── ui/                      Compose screens, theme, components
└── util/                    JSON helpers, error mapping
```

## How playback resolves

`MusicRepository.resolveMedia` walks `ApiConfig.PLAYER_CLIENTS` in order —
`ANDROID_VR`, `ANDROID_MUSIC`, `WEB_REMIX`, `WEB` — and returns the first
response that parses to an audio format carrying a direct URL. A transport
failure for one identity does not abort the loop; if every identity refuses,
the last refusal is surfaced to the UI.

Client version strings are not the gate: measured, the old and current
`WEB_REMIX` versions behave identically. The identity is what the server
weighs, and `ANDROID_VR` is the only identity observed to return formats
without authentication, for a whitelisted subset of videos.

## Screens

- Home — 16:9 video cards with duration badges, horizontal rails
- Search — two-column video grid, debounced query
- Library — liked music and playlists
- Player — full screen, seek slider, play/pause
- Sign in — Google sign-in, with a guest path

`preview/index.html` is a self-contained visual preview of every screen.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

`ResponseParserTest` runs against real captured API responses in
`app/src/test/resources/`. Plain JVM tests, no emulator. `org.json` comes from
the `org.json:json` test dependency because `android.jar` stubs it out.

## Notes

- Debug builds are expected to compile at zero warnings.
- `local.properties` (SDK path) and all signing material are git-ignored.
- Playback against an unauthenticated session is not guaranteed by design; the
  app reports the server's verdict instead of crashing.
