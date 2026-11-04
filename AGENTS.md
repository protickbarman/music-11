# Repository notes

## Layout
- `app/src/main/java/com/xrotune/app/` — Kotlin + Jetpack Compose + Material 3
  sources. Package `com.xrotune.app`.
- `app/src/test/` — JVM tests against real captured API responses.
- `preview/index.html` — static visual preview of the app screens.

## Build
JDK 17+ and the Android SDK (platform 35, build-tools 35.0.0) are required.

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

A debug build must stay at zero warnings.

## Tests
`app/src/test/.../ResponseParserTest.kt` runs against real captured responses in
`app/src/test/resources/`. Plain JVM tests, no emulator. Regenerate fixtures if
the API shape changes. `org.json` comes from the `org.json:json` test dependency
because `android.jar` stubs those classes out.

## InnerTube: what works without authentication
Reference client: `WEB_REMIX`, public key
`AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30`.

Search and browse work as a guest. Playback does not, and this was measured
rather than assumed: across a spread of client profiles with no cookies and no
auth, only `ANDROID_VR` ever returned formats, and only for a whitelisted
subset of videos. The gate is the request identity plus device attestation and
per-session PO tokens, not the client version string — the old and current
`WEB_REMIX` versions behave identically.

`MusicRepository.resolveMedia` therefore walks `ApiConfig.PLAYER_CLIENTS` in
order and keeps the first playable response. `ResponseParser.parsePlayable`
turns a refusal into `PlaybackUnavailableException`, which the UI renders as a
message rather than a crash. Nothing hangs.

## Server-side response shape
`streamingData` for gated responses is absent or empty; when present it may hold
only `serverAbrStreamingUrl` with `adaptiveFormats` entries that carry neither
`url` nor `signatureCipher`. `parsePlayable` therefore requires a non-blank
`url` before accepting a format.

## Hygiene
`local.properties`, build outputs, `.gradle/`, `.kotlin/` and all signing
material are git-ignored. Never commit an APK or a keystore.
