# What happens when you tap a track in the Android app

This traces the Android app, not the web. It follows one tap from the touch
event all the way to bytes reaching the decoder, and marks exactly where the
real YouTube app and this app diverge.

## The chain, in order

```
tap on a row
  -> Compose clickable
  -> Screen callback onTrackClick(track)
  -> MainActivity handler
  -> PlayerViewModel.play(track)
  -> MusicRepository.resolveMedia(videoId)
  -> InnerTubeClient.post("player", ...)
  -> POST music.youtube.com/youtubei/v1/player
  -> ResponseParser.parsePlayable(...)
  -> PlaybackConnection.play(media)
  -> MediaController.setMediaItem / prepare / play
  -> PlaybackService (MediaSessionService)
  -> ExoPlayer
  -> GET <googlevideo url> with Range
  -> decoder
```

## Step 1 — the tap

The row is a Compose `clickable`. From `ui/components/Common.kt`:

```kotlin
fun TrackRow(track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.clickable(onClick = onClick)) { ... }
}
```

and `ui/components/SectionRow.kt`:

```kotlin
fun SectionRow(section: MusicSection, onTrackClick: (Track) -> Unit) {
    ... .clickable { onTrackClick(track) } ...
}
```

`SearchScreen` passes `onClick = { onTrackClick(track) }`. So a tap is just a
callback carrying a `Track`, which holds the `videoId`.

## Step 2 — the ViewModel starts resolving

`ui/player/PlayerViewModel.kt`:

```kotlin
fun play(track: Track, onReady: (PlayableMedia) -> Unit) {
    _state.value = PlayerUiState(track = track, isResolving = true)
    viewModelScope.launch {
        try {
            val media = repository.resolveMedia(track.videoId)
            _state.value = PlayerUiState(track = track, media = media)
            onReady(media)
        } catch (e: Exception) {
            _state.value = PlayerUiState(track = track, error = e.toUserMessage())
        }
    }
}
```

Two states matter: `isResolving = true` shows a spinner, and any failure becomes
`error` for the UI rather than a crash. The comment in the source says it
plainly: the player endpoint legitimately refuses unverified clients.

## Step 3 — the request

`data/repo/MusicRepository.kt` builds the body:

```kotlin
val body = JSONObject()
    .put("context", context())        // client name/version/hl/gl + visitorData
    .put("videoId", videoId)
    .put("contentCheckOk", true)
    .put("racyCheckOk", true)
val response = client.post("player", body, headers())
```

`data/api/InnerTubeClient.kt` sends it:

```
POST https://music.youtube.com/youtubei/v1/player?key=<KEY>&prettyPrint=false
Content-Type: application/json
User-Agent: <Chrome UA>
Origin: https://music.youtube.com
X-Goog-Api-Format-Version: 2
Authorization: Bearer <token>        <- only when signed in
```

`ApiConfig` sets `CLIENT_NAME = "WEB_REMIX"` and
`CLIENT_VERSION = "1.20241104.01.00"`. `visitorData` is carried forward from
whatever the previous response returned.

## Step 4 — the response is parsed, and the gate is here

`data/api/ResponseParser.kt`:

```kotlin
val status = root.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
if (status.isNotEmpty() && status != "OK") {
    throw PlaybackUnavailableException(status, reason)
}
val streaming = root.optJSONObject("streamingData")
    ?: throw PlaybackUnavailableException("ERROR", "No streaming data returned")
...
val audio = formats.filter { it startsWith "audio" && it has a url }
if (audio.isEmpty()) throw PlaybackUnavailableException("ERROR", "No directly playable audio stream")
val best = audio.maxByOrNull { it.optInt("bitrate", 0) }!!
```

So there are three failure points, in order:

1. `playabilityStatus != "OK"` -> `PlaybackUnavailableException(status, reason)`
2. no `streamingData` -> `PlaybackUnavailableException("ERROR", ...)`
3. formats present but none with a direct `url` -> same

The exception carries the server's own status and reason string, which is why
the UI can say "This track can't be played right now (LOGIN_REQUIRED)".

## Step 5 — handing it to the player

`player/PlaybackConnection.kt` builds a Media3 `MediaItem` from the resolved
`PlayableMedia` and drives the controller:

```kotlin
val item = MediaItem.Builder()
    .setUri(media.url)                 // the googlevideo url
    .setMediaId(media.videoId)
    .setMimeType(media.mimeType)
    .setMediaMetadata(... title, artist, artwork ...)
    .build()
c.setMediaItem(item)
c.prepare()
c.play()
```

The controller is a `MediaController` bound to `PlaybackService` over a
`SessionToken`. `connect()` is idempotent and the future is awaited through a
`suspendCancellableCoroutine` that resumes with the exception instead of hanging
if the connection fails.

## Step 6 — the service and ExoPlayer

`player/PlaybackService.kt` is a `MediaSessionService` holding an `ExoPlayer`
with `AUDIO_CONTENT_TYPE_MUSIC`, audio focus, and
`setHandleAudioBecomingNoisy(true)`. From there it is ExoPlayer's job: it issues
the range requests against the `googlevideo` URL and feeds the decoder. The
source comment says so: "ExoPlayer handles the googlevideo URLs handed to it;
range requests and buffering are its job."

## Where the real app differs

The real app does the same first two steps, then diverges at step 3.

| step | this app | real YouTube app |
|---|---|---|
| tap | Compose clickable | same |
| resolve | `player` JSON, `WEB_REMIX` | `player` protobuf, `ANDROID_*` |
| identity | optional Bearer token | signed-out session + visitor id |
| attestation | none | DroidGuard solves `playerAttestationRenderer.challenge` |
| token | none | poToken in `serviceIntegrityDimensions`, `&pot=` on URLs |
| delivery | plain `url`, range GET | SABR session over `/initplayback`, UMP frames |
| ABR | ExoPlayer defaults | server-steered `exoPlayerConfig` watermarks |

From the decompiled APK, the real app's own playability check is:

```java
// arfx.y()
public static boolean y(asgr asgrVar, aljo aljoVar) {
    return asgrVar.h && !arew.l(aljoVar);
}

// arew.l()
public static boolean l(aljo aljoVar) {
    if (aljoVar == null || aljoVar.w() == null || !w(aljoVar.w())) return false;
    return !aljoVar.ac() || aljoVar.f().bd();
}
```

and when it cannot play it logs exactly this, from `asze.java:575`:

```java
aqfa.a(aqey.a, aqex.k,
    "playVideo called on player response with no videoStreamingData.");
```

That is the same condition our `ResponseParser` reports as "No streaming data
returned". The real app is not doing anything cleverer at that point — it is
just more often allowed to proceed, because it can attest.

## Running this on an emulator

An emulator was considered for a live on-device trace and is not possible in
this environment:

```
/dev/kvm            MISSING
cpu virt flags      0
```

Without KVM the Android emulator cannot run, so the on-device trace has to
happen on a real phone. Everything above is read from the shipped sources in
`XroTune/` and from the decompiled APK, not inferred.

## What to check on the phone

Install the APK, tap a track, and watch which of the three failure points fires.
If the app reaches ExoPlayer and bytes flow, the request was accepted. If it
shows a playback error with a status, that status is the server's verdict, and
the fix is an authenticated session.
