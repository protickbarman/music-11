# XroTune

An Android music client that talks to the YouTube InnerTube API, written in Kotlin
with Jetpack Compose and Material 3.

Search and browsing work without signing in. Audio playback does not: the InnerTube
player endpoint gates unauthenticated callers, and that is measured and documented
below rather than worked around. How the player works end to end — request,
attestation, signed URLs, SABR/UMP, ABR policy — is in
[`YOUTUBE-PLAYER-RESEARCH.md`](YOUTUBE-PLAYER-RESEARCH.md). Why the client
identity, not the version string, is what decides whether media is served is in
[`CLIENT-NEGOTIATION.md`](CLIENT-NEGOTIATION.md). The app therefore tries several
identities in order and keeps the first playable response.

What happens on the device after a tap, step by step, is in
[`ANDROID-TAP-FLOW.md`](ANDROID-TAP-FLOW.md).

```
XroTune/
├── app/src/main/java/com/xrotune/app/
│   ├── MainActivity.kt          Navigation, mini-player, tabs
│   ├── data/api/                InnerTubeClient, ResponseParser, ApiConfig
│   ├── data/repo/               MusicRepository (search, browse, player)
│   ├── auth/                    Gmail sign-in + session persistence
│   ├── player/                  Media3 PlaybackService + controller
│   └── ui/                      Compose screens, theme, components
├── app/src/test/                JVM tests against real captured API responses
└── preview/index.html           Static visual preview of every screen
```

## Build

Java, Gradle and the Android SDK are not preinstalled on a bare machine.

```bash
sudo apt-get install -y openjdk-21-jdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Android SDK command-line tools into /opt/android-sdk, then:
sudo chown -R "$(whoami)" /opt/android-sdk
yes | sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

cd XroTune
echo "sdk.dir=/opt/android-sdk" > local.properties
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (minSdk 26, targetSdk 35).

A prebuilt debug APK is attached to the
[`xrotune-v1.0.0` release](https://github.com/xpro-004/music/releases/tag/xrotune-v1.0.0):

```
https://github.com/xpro-004/music/releases/download/xrotune-v1.0.0/XroTune-1.0.0-debug.apk
```

It is also committed at `XroTune/preview/XroTune-1.0.0-debug.apk`.
Debug-signed, so the phone must allow installs from unknown sources.
MD5 `dba5339212cf09e228cdeba0b9113324`.

## Preview

`XroTune/preview/index.html` is a self-contained visual preview of the sign-in,
home, search, library, now-playing and error screens. Serve it with any static
server, e.g. `python3 -m http.server` from that directory.

## Tests

`app/src/test/java/com/xrotune/app/data/api/ResponseParserTest.kt` runs as plain JVM
tests against responses captured from the live API in `app/src/test/resources/`.
They fail if the real renderer shape drifts, not merely when the code changes.

On the JVM, `org.json` comes from the `org.json:json` test dependency, because
`android.jar` stubs those classes out.

## InnerTube notes

The app calls `music.youtube.com/youtubei/v1/*` with the public `WEB_REMIX` key.

**Search and browse work as a guest**, with two caveats that were found by testing
the live API rather than assuming:

- Search needs the `params` filter. An unfiltered search returns mostly artists,
  profiles and podcasts, which carry only a `browseEndpoint` and are therefore not
  playable. `ApiConfig.SearchFilter.VIDEOS` reliably returns 20/20 playable rows.
- `browseId` matters for guests. `FEmusic_home` returns only browse-only rows for
  guests, while `FEmusic_explore` and `FEmusic_new_releases` return playable items.

### Playback does not work yet

The `player` endpoint refuses unauthenticated callers. Verified across WEB_REMIX
(with and without `visitorData` / client headers), ANDROID_MUSIC, IOS_MUSIC, WEB and
TVHTML5_SIMPLY_EMBEDDED: every request returns `UNPLAYABLE` ("Video unavailable")
or `LOGIN_REQUIRED` ("Sign in to confirm you're not a bot"), with zero streaming
formats. The same videos are `PLAYABLE` on the web watch page, so this is
server-side bot mitigation on the InnerTube player endpoint, not a malformed
request.

As a result, tapping a track surfaces "This track can't be played right now."
`ResponseParser.parsePlayable` turns the refusal into a `PlaybackUnavailableException`
and the UI reports it, so the app degrades cleanly instead of crashing or hanging.

Making playback work is not a matter of fixing the request shape. The real
YouTube app gates media on device attestation (DroidGuard) and per-session PO
tokens, and now delivers audio over SABR rather than as downloadable URLs — the
watch page returns no `url` and no `signatureCipher` on its audio formats at all,
only `serverAbrStreamingUrl`. See
[`ANALYSIS-youtube-guest.md`](ANALYSIS-youtube-guest.md) for the decompiled
evidence. The practical next step is to re-test with an authenticated session
from the Gmail sign-in flow, not to reimplement the gating.

## Third-party content

The decompiled YouTube APK analysis that informed the request shapes is
deliberately not committed. It contains Google's proprietary APK and derived
sources, and is far larger than a source repository should carry.
