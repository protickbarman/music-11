# Repository notes

## Layout
- `XroTune/` — Android app (`com.xrotune.app`), Kotlin + Jetpack Compose + Material 3.
- `ytmusic-apk/`, `ytmusic-mirror/` — extracted YouTube Music APK analysis.
- `XroTune/preview/index.html` — static visual preview of the app screens.

## Build environment
Java, Gradle and the Android SDK are **not** preinstalled. Provisioning steps that work:

```bash
# JDK 21
sudo apt-get install -y openjdk-21-jdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Android SDK cmdline-tools -> /opt/android-sdk, then:
sudo chown -R "$(whoami)" /opt/android-sdk   # required; sdkmanager cannot write otherwise
yes | sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# Gradle 8.9 -> /opt/gradle-8.9 (wrapper is already committed)
cd XroTune && ./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

`XroTune/local.properties` must contain `sdk.dir=/opt/android-sdk`.

## InnerTube API — important
The app talks to the YouTube Music InnerTube API (`music.youtube.com/youtubei/v1/*`)
with the public `WEB_REMIX` key.

**Search and browse work as a guest.** Search needs `params` to be useful: an
unfiltered search returns mostly artists, profiles and podcasts, which carry only a
`browseEndpoint` and are therefore *not playable*. The videos filter
(`ApiConfig.SearchFilter.VIDEOS`) reliably returns 20/20 playable rows.

Guest `browseId` matters too: `FEmusic_home` returns only browse-only rows for
guests, while `FEmusic_explore` and `FEmusic_new_releases` return playable items.

**The `player` endpoint does not work as a guest.** Verified across WEB_REMIX
(with and without visitorData / client headers), ANDROID_MUSIC, IOS_MUSIC, WEB and
TVHTML5_SIMPLY_EMBEDDED: every request returns `UNPLAYABLE` ("Video unavailable")
or `LOGIN_REQUIRED` ("Sign in to confirm you're not a bot"), with zero streaming
formats. The same videos are `PLAYABLE` on the web watch page, so this is
server-side bot mitigation on the InnerTube player endpoint, not a bug in our
request shape.

Consequences:
- Audio streaming cannot work without an authenticated, visitor-attested session.
  Implementing the `player` response path is not enough on its own.
- Do not claim streaming works. `ResponseParser.parsePlayable` correctly turns the
  refusal into a `PlaybackUnavailableException`, which the UI shows as
  "This track can't be played right now." That is the accurate current behaviour.

To make playback work, a real option is resolving the stream from the
`youtube.com/watch` page (which does return `PLAYABLE` + `streamingData`), including
the signature/n-parameter deciphering that the web player uses. That is a
substantial piece of work and has its own maintenance and ToS considerations.

## Tests
`XroTune/app/src/test/.../ResponseParserTest.kt` runs against real captured
responses in `app/src/test/resources/`. They are plain JVM tests (no emulator) and
must stay green. Regenerate the fixtures if the API shape changes.

Note: on the JVM, `org.json` comes from the `org.json:json` test dependency, because
`android.jar` stubs those classes out.
