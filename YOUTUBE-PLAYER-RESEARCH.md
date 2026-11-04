# How YouTube's player actually works

Research notes from the decompiled YouTube 21.44.534 APK plus live captures of
the InnerTube endpoints. Every JSON block below is real output, not an
illustration. The goal is to explain the whole chain: how the client calls, what
the server answers, and how media is handled.

## 0. The three layers

1. **Control plane** — InnerTube JSON over HTTPS (`/youtubei/v1/*`). Decides
   whether you may play, and returns metadata plus instructions for getting
   media.
2. **Attestation layer** — the server hands out a challenge; the client must
   solve it (BotGuard / DroidGuard) and return a token. Without a token most
   videos are refused.
3. **Media plane** — either plain signed URLs under `/videoplayback`, or, the
   current default, a stateful streaming session (SABR) over a binary protocol
   (UMP) on `/initplayback`.

## 1. The player request

```
POST https://www.youtube.com/youtubei/v1/player?key=<INNERTUBE_API_KEY>&prettyPrint=false
Content-Type: application/json
```

```json
{
  "context": {
    "client": {
      "clientName": "ANDROID_VR",
      "clientVersion": "1.60.19",
      "androidSdkVersion": 32,
      "osName": "Android",
      "osVersion": "12",
      "hl": "en",
      "gl": "US"
    }
  },
  "videoId": "dQw4w9WgXcQ",
  "contentCheckOk": true,
  "racyCheckOk": true
}
```

`context.client` is the identity, and it decides which media pipeline the server
selects. A real client also sends `visitorData`, `clientScreen`,
`utcOffsetMinutes`, and — when signed in — a `user` object; when it holds a token
it adds `serviceIntegrityDimensions.poToken`.

## 2. The response

Top-level keys on a working fetch:

```
['adBreakHeartbeatParams', 'attestation', 'captions', 'playabilityStatus',
 'playbackTracking', 'playerConfig', 'responseContext', 'storyboards',
 'streamingData', 'trackingParams', 'videoDetails']
```

- **`playabilityStatus`** — the gate. `status` is `OK`, `UNPLAYABLE`,
  `LOGIN_REQUIRED`, `ERROR`, `AGE_CHECK_REQUIRED`, `LIVE_STREAM_OFFLINE`,
  `FULLSCREEN_ONLY`, and so on, with `reason` and `messages`.
- **`videoDetails`** — `title`, `lengthSeconds`, `author`, `channelId`,
  `isLiveContent`.
- **`streamingData`** — how to obtain media (section 4).
- **`playerConfig`** — player tuning (section 5).
- **`attestation`** — the challenge (section 3).

## 3. Attestation — the part that is easy to miss

A working fetch returned this:

```json
"attestation": {
  "playerAttestationRenderer": {
    "challenge": "a=6&a2=1&b=VGGR4CnceZwVc9a0aM1rW_Tzlrg&c=1793794541&d=28&e=dQw4w9WgXcQ&hh=AGYh8WXFgAn6JmdwGzqpjj8a11c1zIo_Xm5GEqVc7VM"
  }
}
```

Read the fields:

| field | meaning |
|---|---|
| `a=6` | challenge version |
| `a2=1` | challenge sub-version |
| `b` | server nonce |
| `c` | issue timestamp |
| `d=28` | client id (28 is ANDROID_VR) |
| `e=dQw4w9WgXcQ` | the **videoId** |
| `hh` | integrity hash |

So the challenge is issued **per video and per client**. The client must run it
through the attestation engine and return a response that the server can verify
against the nonce. On Android that engine is DroidGuard, reached through Play
Services:

```
com.google.android.gms.potokens.internal.IPoTokensService
com.google.android.gms.potokens.service.START
com.google.android.gms.droidguard.internal.IDroidGuardService
com.google.android.gms.droidguard.internal.IDroidGuardHandle
com.google.ccc.abuse.droidguard.DroidGuard
DROIDGUARD_ONDEVICE / DROIDGUARD_ERROR / STREAMZ_DROIDGUARD
"DroidGuard snapshot failed"
"%Timeout waiting for DroidGuard handle"
```

The resulting token is held and refreshed by:

```
com.google.android.libraries.youtube.media.interfaces.ProofOfOriginTokenManager
    byte[] b()                          -> the current token bytes
    void   a(OnPoTokenMintedCallback)   -> mint asynchronously
PlatypusProofOfOriginTokenManager        -> the implementation
potoken.nocallback / potoken.nulloninit / missingpotoken   -> failure logs
```

and it is attached in **two** places:

**a) In subsequent player requests** as `serviceIntegrityDimensions.poToken`.

**b) On playback URLs** as a `pot` query parameter. From `ainp.j(Uri, String)`:

```java
apna token = this.c.b(bati.e(str).d());          // look up token for this video
if (token == null) return uri;                   // none -> URL left untouched
String pot = Base64.encodeToString(token.b, 10);
agap out = new agap(uri);
out.f("pot", pot);                               // append &pot=<base64>
return out.a();
```

That `if (token == null) return uri;` is the crux: without a token the URL goes
out unchanged, and the server can tell.

## 4. Media delivery — two paths

### Path A: plain signed URLs

`streamingData.formats[]` and `adaptiveFormats[]` each carry `itag`, `mimeType`,
`bitrate`, `contentLength`, `approxDurationMs`, and either `url` or
`signatureCipher`.

Real format list from one fetch (27 entries):

```
 itag type   codec                   bitrate  url?
  313 video  vp9                    18076636   yes
  401 video  av01.0.12M.08          17400774   yes
  271 video  vp9                     8978328   yes
  137 video  avc1.640028             4334157   yes
  136 video  avc1.4D401F             1058310   yes
  139 audio  mp4a.40.5                 50152   yes
  140 audio  mp4a.40.2                130677   yes
  249 audio  opus                      49496   yes
  251 audio  opus                     136544   yes
   18 video  avc1.42001E, mp4a.40.2   444226   yes
```

A `url` decodes to:

```
host: rr6---sn-ajab55-5c.googlevideo.com
path: /videoplayback
  itag         = 139
  mime         = audio/mp4
  clen         = 1300631
  dur          = 213.159
  fexp         = 51565116,52184236,52271469
  lsig         = APaTxxMwRAIgXaJAuuE-xpQq8N5z_nVjrdn3s4hhrO3_Lbv-ld_ecikCIEiq
  requiressl   = yes
  gir          = yes
  expire       = 1793816128
  ip           = fda3:e722:ac3:10:149:44c9:a02:3004
full params: bui, c, clen, cps, dur, ei, epbp, expire, fexp, fvip, gir, id, ip,
             itag, keepalive, lmt, lsig, lsparams, met, mh, mime, mm, mn, ms,
             mt, mv, mvi, pcm2, pl, requiressl
```

What matters:

- `lsig` / `sig` — the URL signature. When a format arrives as
  `signatureCipher` instead of `url`, the signature is enciphered and the client
  must fetch the player script (`base.js`) and apply the signature and `n`
  transforms. When you are given `url`, it is already signed.
- `expire` — the URL dies after a few hours (`expiresInSeconds: 21540`).
- `ip` — the URL is **bound to the requesting IP address**.
- `requiressl=yes`, `gir=yes` — TLS is mandatory.
- `range` — byte ranges; the player issues `Range:` requests and gets
  `206 Partial Content`.

This is the path third-party clients historically used: take the format list,
pick an `itag`, GET it with a Range header, hand bytes to the decoder.

### Path B: SABR — server-side adaptive bitrate

`streamingData.serverAbrStreamingUrl`:

```
host: rr6---sn-ajab55-5c.googlevideo.com
params: c, cps, ei, epbp, expire, fexp, fvip, id, ip, keepalive, lsig, lsparams,
        met, mh, mm, mn, ms, mt, mv, mvi, pcm2, pl, requiressl, rms, rqh,
        sabr, sig, source, sparams, spc, svpuc, xpc
  sabr = 1
```

Note `sabr=1`, and the control parameters `rms` (request media segment), `rqh`
(request header), `spc`, `svpuc`, `xpc`.

With SABR there is no downloadable file. The client holds a long-lived
connection and exchanges a binary protocol:

1. The client opens the URL; the response content type is
   `application/vnd.yt-ump`.
2. The client sends **UMP requests**: a media request carrying a playback
   position (a `SabrSeek`) and the `MediaCapabilities` it wants.
3. The server streams back **UMP frames**: length-prefixed parts including
   `MediaHeader` (per itag: init range, index range, format id), `Media` (the
   segment bytes, possibly interleaved across itags), `NextRequestPolicy` (what
   to ask for next), `SabrContextUpdate`, and `LiveMetadata`.
4. The client feeds those parts into the player as if they were segments of an
   ordinary stream.

The protocol is driven by a **timestamp in milliseconds**, not byte offsets. The
client says "give me around t=12345ms" and the server decides which audio and
video segments to send. It is closer to an alternative to DASH/HLS than to a
file download.

The real app implements this in native code:

```
com.google.android.libraries.youtube.media.interfaces.VideoplaybackUmpParser
    public static native VideoplaybackUmpParser obffa8847b...(callbacks)
    private native void native_obfe6f9b8...(long ref, ByteBuffer data)
com.google.android.libraries.youtube.media.interfaces.VideoplaybackUmpParserCallbacks
com.google.android.libraries.youtube.media.interfaces.MediaFetchController      (CppProxy)
com.google.android.libraries.youtube.media.interfaces.MediaFetchCallbacks
com.google.android.libraries.youtube.media.interfaces.SabrDataProviderWrapper
com.google.android.libraries.youtube.media.interfaces.MediaFetchPlayerConfig
com.google.android.apps.youtube.proto.streaming.SabrSeekOuterClass$SabrSeek
com.google.android.apps.youtube.proto.SabrLiveProtos$SabrLiveMetadata
com.google.android.libraries.youtube.player.service.SabrPrefetchController
enableSabrPrefetch / androidSabrDontCancelFuturePrefetch
SEEK_SOURCE_SABR_RELOAD_PLAYER_RESPONSE_TOKEN_SEEK
DISABLED_BY_SABR_STREAMING_URI
```

The transport underneath is "Onesie":

```
OnesieRequestPreparer
OnesieController
^https://.*\.googlevideo\.com/initplayback      <- session open
"Failed to build the Onesie request."
"OnesieRetryingDataSource time out."
"Giving up on OnesieVideoBuffer-only request"
"Non-OK Onesie proxy status received:"
```

So a playback session is: open `/initplayback`, then exchange UMP frames on that
connection for the life of the stream.

## 5. playerConfig — the tuning the server dictates

```json
"playerConfig": {
  "audioConfig": {
    "loudnessDb": 0.9899998,
    "perceptualLoudnessDb": -13.01,
    "enablePerFormatLoudness": true
  },
  "exoPlayerConfig": {
    "useExoPlayer": true,
    "useAdaptiveBitrate": true,
    "maxInitialByteRate": 91136,
    "minDurationForQualityIncreaseMs": 12000,
    "maxDurationForQualityDecreaseMs": 9000,
    "minDurationToRetainAfterDiscardMs": 21000,
    "lowWatermarkMs": ...
  }
}
```

This is the ABR policy. The server sets the watermarks and the dwell times that
must pass before quality may shift up or down. Even quality adaptation is
server-steered rather than purely client-chosen, and loudness normalisation is
specified per format.

## 6. Playability and error taxonomy

`playabilityStatus.status` values seen or referenced:
`OK`, `PLAYABLE`, `UNPLAYABLE`, `LOGIN_REQUIRED`, `ERROR`, `AGE_CHECK_REQUIRED`,
`AGE_VERIFICATION_REQUIRED`, `CONTENT_CHECK_REQUIRED`, `LIVE_STREAM_OFFLINE`,
`FULLSCREEN_ONLY`.

Client-side playback exception reasons compiled into the app:

```
PLAYBACK_EXCEPTION_ERROR_REASON_UNPLAYABLE
PLAYBACK_EXCEPTION_ERROR_REASON_UNPLAYABLE_BY_APP_POLICY
PLAYBACK_EXCEPTION_ERROR_REASON_UNPLAYABLE_IN_BACKGROUND
PLAYBACK_EXCEPTION_ERROR_REASON_UNPLAYABLE_IN_PICTURE_IN_PICTURE
PLAYBACK_EXCEPTION_ERROR_REASON_USER_AGE_CHECK_FAILED
PLAYBACK_EXCEPTION_ERROR_REASON_USER_CONTENT_CHECK_FAILED
PLAYBACK_EXCEPTION_ERROR_REASON_NO_STREAMS
PLAYBACK_EXCEPTION_ERROR_REASON_EMBARGOED
PLAYBACK_EXCEPTION_ERROR_REASON_LICENSE_SERVER_ERROR
PLAYBACK_EXCEPTION_ERROR_REASON_LICENSE_SERVER_NET_ERROR
PLAYBACK_EXCEPTION_ERROR_REASON_LICENSE_SERVER_CONCURRENT_PLAYBACK_ERROR
PLAYBACK_EXCEPTION_ERROR_REASON_PARTIAL_PLAYBACK_DATA_EXHAUSTED
PLAYBACK_EXCEPTION_ERROR_REASON_REQUEST_FAILED
PLAYBACK_EXCEPTION_ERROR_REASON_VIDEO_ERROR
PLAYBACK_EXCEPTION_ERROR_REASON_WATCH_NEXT_ERROR
PLAYBACK_EXCEPTION_ERROR_REASON_PLAYER_ERROR
PLAYBACK_EXCEPTION_ERROR_REASON_UNKNOWN
```

## 7. How the real app treats a not-logged-in user

Signed out is a first-class state, not a fallback to anonymous:

```
SignedOutStateHandler{signedOutState=...}
NoAccountWorkerFactory startWork()
SignedOutComplianceControls{signedOutState=...}
Null mobileSignedOutConsentState
CONSENTFLOW_SIGNEDOUT_EOM_REVISIT          <- a consent flow exists for it
```

It stays identified: a visitor id is still sent
(`dogfood_suggest_send_visitor_id_signed_out`), and `visitorData` round-trips
through the `visitor_id` endpoint. Incognito is a separate explicit mode
(`isIncognito`, `enableIncognitoMode`) — and even then it runs inside the app
with Play Services available.

The decisive point: **a signed-out app can still attest**, because DroidGuard and
the poToken service are on the device. A bare HTTP client cannot, and that is
what separates a real signed-out user from an anonymous scraper.

## 8. Why the anonymous approach fails

Measured against 12 client profiles with no cookies and no auth. Summary:

| client | typical result |
|---|---|
| WEB_REMIX, WEB, TVHTML5 | `UNPLAYABLE` — "Video unavailable", 0 formats |
| MWEB, ANDROID_TESTSUITE | `UNPLAYABLE` — "The page needs to be reloaded" |
| ANDROID_MUSIC, IOS_MUSIC | `LOGIN_REQUIRED` — "Please sign in" |
| ANDROID, IOS | HTTP 400 |
| WEB_EMBEDDED_PLAYER, TVHTML5_SIMPLY_EMBEDDED_PLAYER | `ERROR` |
| ANDROID_VR | `OK`, formats with plain `url` |

`ANDROID_VR` is the only profile that yields plain URLs without auth, and it is
not usable: 1 of 15 videos, deterministic per video (one video `OK` 7/7, another
`UNPLAYABLE` 3/3, alternating in one session), uncorrelated with age or
popularity, and unaffected by `visitorData` or `X-YouTube-Client-*` headers.

The reason is section 3: our requests are un-attested, so the server declines to
issue media instructions.

## 9. What a faithful implementation would require

1. **Answer the attestation challenge.** Run BotGuard in a JS VM or WebView, or
   use Play Integrity plus DroidGuard on device, to satisfy
   `playerAttestationRenderer.challenge` and obtain a poToken.
2. **Bind and attach the poToken** per video, as
   `serviceIntegrityDimensions.poToken` in player requests and `&pot=` on URLs.
3. **Handle `signatureCipher`** when URLs arrive ciphered: fetch the player
   script and apply the signature and `n` transforms.
4. **Implement a SABR/UMP client**: open `/initplayback`, parse the
   length-prefixed UMP frame stream, maintain the itag and segment map, and drive
   requests by timestamp.
5. **Implement an ABR controller** honouring the `exoPlayerConfig` watermarks.

Steps 1 and 4 are precisely the abuse-control boundary and the proprietary
streaming protocol. They are not ordinary integration work.

## 10. Honest options

1. **Use an authenticated session** with a supported player (Media3/ExoPlayer fed
   by a supported source). This is the natural path and bypasses nothing.
2. **Use a supported route**: the official YouTube Data / Player APIs, or an
   embedded supported player.
3. Do not reimplement the gate. Defeating attestation or reimplementing SABR is
   a different class of decision from building a client, and it is against
   YouTube's Terms of Service.

Until one of the first two is chosen, search and browse remain fully functional
as a guest, and playback fails cleanly with `PlaybackUnavailableException`.
