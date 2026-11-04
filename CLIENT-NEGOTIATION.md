# How the player endpoint decides, and why client fallback is the right shape

This is the deeper cut on the one question that matters: given the same video
ID, why does one client identity get media and another does not? The answer
determines how XroTune should ask.

## The measurement

Two video IDs, four identities, one session, no cookies, no auth. Endpoint
`POST /youtubei/v1/player`, `contentCheckOk` and `racyCheckOk` true.

| identity | `dQw4w9WgXcQ` | `VqgVGkt2YhM` |
|---|---|---|
| WEB_REMIX `1.20241104.01.00` | `UNPLAYABLE`, 0 formats | `LOGIN_REQUIRED`, 0 formats |
| WEB_REMIX `1.20261031.09.00` | `UNPLAYABLE`, 0 formats | `LOGIN_REQUIRED`, 0 formats |
| ANDROID_VR `1.60.19` | **`OK`, 27 formats, 26 with `url`** | `LOGIN_REQUIRED`, 0 formats |
| ANDROID_MUSIC `7.27.52` | `LOGIN_REQUIRED`, 0 formats | `LOGIN_REQUIRED`, 0 formats |

Two things fall straight out of this.

**The client version is not the lever.** The old WEB_REMIX version and the
current one (`1.20261031.09.00`, read from the live web client's own
`INNERTUBE_CLIENT_VERSION`) behave identically. Bumping the version string in
the app changes nothing about whether media is handed out. It is still worth
tracking the live value, but it is not a gate.

**The identity is the lever, and only one identity opens the gate.** Of the
four, ANDROID_VR is the only one that ever returns formats, and it does so for
one of the two videos. The other three never return a single format for either
video.

## Why the version does not matter but the identity does

A `player` request is authorised by a set of signals, and the client identity
decides which signals the server demands:

- **WEB_REMIX** is a browser identity. The server expects a web session:
  cookies, a `visitorData` bound to that session, and — for media — a PO token
  minted by the page's BotGuard. A bare JSON POST has none of that, so the
  server answers `UNPLAYABLE` or `LOGIN_REQUIRED` without ever considering
  `clientVersion`.
- **ANDROID_MUSIC** is a device identity. It expects a signed-out managed
  session with device attestation (DroidGuard). Without attestation the answer
  is `LOGIN_REQUIRED`.
- **ANDROID_VR** is the interesting one. It is a device identity whose
  attestation requirements the server currently does not enforce for playback,
  so an unattested request is sometimes served directly. The catch is the
  "sometimes": it is per-video and deterministic, which is the whitelist
  behaviour measured earlier (`dQw4w9WgXcQ` works repeatedly; a spread of fresh
  videos does not).

So the gate is not "are you recent enough", it is "can you prove you are who
you claim to be". Version strings do not prove anything; attestation does.

## What this means for the request shape

Because no single identity is reliable, asking once is wrong. The correct shape
is a sequence:

1. Ask with the most permissive identity first (ANDROID_VR).
2. If it returns `playabilityStatus == "OK"` and a format carrying a direct
   `url`, use it.
3. Otherwise fall through to the next identity and repeat.
4. If every identity refuses, report the last refusal, because that is the
   server's most informative verdict.

This is what XroTune now does. `ApiConfig.PLAYER_CLIENTS` lists the identities
in order, and `MusicRepository.resolveMedia` walks it:

```kotlin
for (client in ApiConfig.PLAYER_CLIENTS) {
    val body = JSONObject()
        .put("context", ApiConfig.clientContext(client, client.visitorData))
        .put("videoId", videoId)
        .put("contentCheckOk", true)
        .put("racyCheckOk", true)
    val response = try {
        client.post("player", body, headers(),
            baseUrl = client.baseUrl,
            userAgent = client.userAgent,
            origin = client.origin)
    } catch (e: InnerTubeException) {
        lastFailure = PlaybackUnavailableException("ERROR", e.message)
        continue                      // one identity failing is not fatal
    }
    try {
        return@withContext ResponseParser.parsePlayable(response, videoId)
    } catch (e: PlaybackUnavailableException) {
        lastFailure = e
    }
}
throw lastFailure ?: PlaybackUnavailableException("ERROR", "No client returned a playable stream")
```

Two details in there matter:

- A transport failure (`InnerTubeException`, e.g. HTTP 400 for a bad identity)
  does not abort the loop. The real clients treat an identity failure as a
  reason to try another, not as the end of the attempt.
- Only a format with a non-blank `url` counts as playable. A format that is
  present but carries only `signatureCipher` is not usable without a decipher
  step, and `parsePlayable` refuses it rather than handing a dead URL to
  ExoPlayer.

## The honest limit

Client fallback improves the odds and matches how the real clients behave. It
does not defeat the gate. The identities that need attestation still fail
without it, and ANDROID_VR only serves a whitelisted subset. On a real phone
behind a residential IP the outcome will differ from this datacenter, because
the IP is part of what the server weighs, but the structural point stands: to
reliably play arbitrary videos, the request has to be authenticated.

## Reproducing the measurement

```python
import json, urllib.request
KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

def ctx(name, ver, android=False):
    c = {"clientName": name, "clientVersion": ver, "hl": "en", "gl": "US"}
    if android:
        c.update({"androidSdkVersion": 32, "osName": "Android", "osVersion": "12"})
    return {"client": c}

def probe(base, ctxobj, ua, origin, video_id):
    req = urllib.request.Request(
        base + "player?key=" + KEY + "&prettyPrint=false",
        data=json.dumps({"context": ctxobj, "videoId": video_id,
                         "contentCheckOk": True, "racyCheckOk": True}).encode(),
        headers={"Content-Type": "application/json", "User-Agent": ua,
                 "Origin": origin, "X-Goog-Api-Format-Version": "2"})
    with urllib.request.urlopen(req, timeout=25) as r:
        body = json.load(r)
    sd = body.get("streamingData", {}) or {}
    formats = (sd.get("adaptiveFormats", []) or []) + (sd.get("formats", []) or [])
    return body.get("playabilityStatus", {}).get("status"), len(formats)
```

The endpoint path is `player`; posting to the bare base URL returns 404.
