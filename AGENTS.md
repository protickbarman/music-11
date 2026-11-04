# Repository notes

## Layout
- `XroTune/` — Android app (`com.xrotune.app`), Kotlin + Jetpack Compose + Material 3.
- `XroTune/preview/index.html` — static visual preview of the app screens.
- `YOUTUBE-PLAYER-RESEARCH.md` — how the player works end to end: InnerTube
  request/response, attestation challenge, signed URLs, SABR/UMP, ABR policy.
- `ANALYSIS-youtube-guest.md` — decompiled evidence for how YouTube gates guests.
- `youtube-apk/`, `ytmusic-apk/`, `ytmusic-mirror/` — local APK analysis, git-ignored.

## Build environment
Java, Gradle and the Android SDK are **not** preinstalled. Provisioning that works:

```bash
sudo apt-get install -y openjdk-21-jdk unzip
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Android SDK cmdline-tools -> /opt/android-sdk, then:
sudo chown -R "$(whoami)" /opt/android-sdk
yes | sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# jadx, for APK analysis
sudo mkdir -p /opt/jadx && sudo chown -R "$(whoami)" /opt/jadx
curl -sL https://github.com/skylot/jadx/releases/download/v1.5.6/jadx-1.5.6.zip \
  -o /tmp/jadx.zip && unzip -q /tmp/jadx.zip -d /opt/jadx

cd XroTune && ./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

`XroTune/local.properties` must contain `sdk.dir=/opt/android-sdk`.
A debug build must stay at zero warnings.

## Tests
`XroTune/app/src/test/.../ResponseParserTest.kt` runs against real captured
responses in `app/src/test/resources/`. Plain JVM tests, no emulator. Regenerate
fixtures if the API shape changes. `org.json` comes from the `org.json:json` test
dependency because `android.jar` stubs those classes out.

## InnerTube: what works without authentication

Reference client: `WEB_REMIX`, public key
`AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30`.

**Search and browse work as a guest.**

- Search needs `params`. An unfiltered search returns mostly artists, profiles and
  podcasts, which carry only a `browseEndpoint` and are not playable. The videos
  filter (`ApiConfig.SearchFilter.VIDEOS`, param `EgWKAQIQAWoKEAkQChAFEAMQBA==`)
  reliably returns 20/20 playable rows.
- `browseId` matters for guests. `FEmusic_home` returns only browse-only rows,
  while `FEmusic_explore` and `FEmusic_new_releases` return playable items.

**Playback does not work without authentication.** This was measured, not assumed.

Across 12 client profiles (WEB_REMIX, WEB, MWEB, ANDROID, ANDROID_MUSIC,
ANDROID_VR, ANDROID_TESTSUITE, IOS, IOS_MUSIC, TVHTML5, WEB_EMBEDDED_PLAYER,
TVHTML5_SIMPLY_EMBEDDED_PLAYER), with no cookies and no auth:

| client | typical result |
|---|---|
| WEB_REMIX, WEB, TVHTML5 | `UNPLAYABLE` — "Video unavailable", 0 formats |
| MWEB, ANDROID_TESTSUITE | `UNPLAYABLE` — "The page needs to be reloaded" |
| ANDROID_MUSIC, IOS_MUSIC | `LOGIN_REQUIRED` — "Please sign in" |
| ANDROID, IOS | HTTP 400 |
| WEB_EMBEDDED_PLAYER, TVHTML5_SIMPLY_EMBEDDED_PLAYER | `ERROR` |
| **ANDROID_VR** | **`OK`, formats with plain `url`** |

`ANDROID_VR` is the only profile that returns directly downloadable URLs without
auth. It is not usable:

- Measured success **1 of 15 videos** (6.7%). Repeat runs are identical.
- The outcome is **deterministic per video**, not rate limiting: one video returned
  `OK` 7/7 times while another returned `UNPLAYABLE` 3/3 times, alternating in the
  same session.
- It does not correlate with video age or popularity (older set 1/5, newer set
  0/5, one 2009 video works and another does not).
- `visitorData` and `X-YouTube-Client-*` headers change nothing (0/5 either way).

The successful fetch does return real audio (HTTP 206, valid MP4 box), so the
mechanism works; the server just refuses most videos.

**Why:** the requests are un-attested. The real app is a managed signed-out
session that carries a visitor id, DroidGuard device attestation, and per-session
PO tokens bound to the session and videoId. Media is also moving to SABR
(`serverAbrStreamingUrl`) rather than downloadable URLs. Full decompiled evidence
and string references are in `ANALYSIS-youtube-guest.md`.

### If audio playback is required

The honest options, in order of preference:

1. **Authenticate.** Use a real signed-in session and re-test the plain-HTTP
   player path. This is the natural next step and does not require any gating to
   be bypassed.
2. **Use a supported route.** The official YouTube Data / Player APIs, or an
   embedded supported player.
3. Do not attempt to defeat attestation or reimplement SABR. That is the
   abuse-control boundary itself, it is a different class of decision from
   building a client, and it is against YouTube's Terms of Service.

Until then, the current behaviour is correct: search and browse work, and
playback fails with a clear message. `ResponseParser.parsePlayable` turns the
refusal into a `PlaybackUnavailableException`, which the UI renders as
`error_playback_unavailable`. Nothing hangs, nothing crashes.

## Server-side response shape
`streamingData` for gated responses is absent or empty; when present it may hold
only `serverAbrStreamingUrl` with `adaptiveFormats` entries that carry neither
`url` nor `signatureCipher`. A parser that assumes `formats[].url` exists will
misbehave, which is why `parsePlayable` checks for a non-blank `url` and raises
`PlaybackUnavailableException` otherwise.
