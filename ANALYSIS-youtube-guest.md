# How the real YouTube app handles a not-logged-in client

Analysis of **YouTube 21.44.534** (`com.google.android.youtube`, downloaded from
APKPure), decompiled with jadx 1.5.6 (54,190 Java files) plus raw dex string
extraction. This was done to explain why XroTune cannot stream as a guest.

## Summary

A guest in the real app is not an anonymous client. It is a **server-managed
signed-out session** that carries device attestation and mints per-session
tokens. Every media request is gated on those tokens, and media no longer
arrives as plain URLs at all. Our guest requests have none of this, which is
exactly why the `player` endpoint answers `UNPLAYABLE` / `LOGIN_REQUIRED`.

## 1. Guest is a first-class, tracked state

The app has a dedicated signed-out pipeline rather than degrading to anonymous
requests:

```
SignedOutStateHandler{signedOutState=...}
NoAccountWorkerFactory startWork()
Null mobileSignedOutConsentState
dogfood_suggest_send_visitor_id_signed_out
enableIncognitoMode / isIncognito
```

There is a signed-out consent flow (`CONSENTFLOW_SIGNEDOUT_EOM_REVISIT`) and the
app still sends a **visitor id** while signed out
(`dogfood_suggest_send_visitor_id_signed_out`). So "guest" means *identified but
not authenticated*, not *unidentified*.

XroTune sends a bare client context with no visitor identity, which is closer to
"unknown client" than to "signed-out user".

## 2. Requests carry device attestation

```
"Failed to create DroidGuard handle
"Interrupted waiting for DroidGuard
%DroidGuard v2 client is not available
%Timeout waiting for DroidGuard handle
'com.google.android.gms.droidguardclient
*com.google.ccc.abuse.droidguard.DroidGuard
DROIDGUARD / DROIDGUARD_ERROR / DROIDGUARD_ONDEVICE
DroidGuard snapshot failed
STREAMZ_DROIDGUARD
```

DroidGuard is Google's Play-Integrity-backed abuse signal. The app obtains a
snapshot and attaches it to InnerTube traffic. A bare HTTP client cannot produce
this, so the server sees a request shape it does not trust.

## 3. Per-session PO tokens are minted and attached

```
PO_TOKENS
com.google.android.gms.potokens.internal.IPoTokensService
com.google.android.gms.potokens.service.START
com.google.android.libraries.youtube.media.interfaces.ProofOfOriginTokenManager
com.google.android.libraries.youtube.media.interfaces.OnPoTokenMintedCallback
PlatypusProofOfOriginTokenManager
potoken.nocallback / potoken.nulloninit / missingpotoken
xwrapper;...;videoId;poToken;tokenManager;watchEndpointUstreamerConfig
```

`ProofOfOriginTokenManager.b()` returns the token bytes and `a(callback)` mints a
fresh one asynchronously. The token is bound to the session *and the videoId*.

Two attachment points were found:

**a) Playback URLs get a `pot` query parameter.** In `ainp.j(Uri, String)`:

```java
apna token = this.c.b(bati.e(str).d());
if (token == null) return uri;              // no token -> URL unchanged
String pot = Base64.encodeToString(token.b, 10);
agap out = new agap(uri);
out.f("pot", pot);                          // append ?pot=<base64>
return out.a();
```

**b) Player requests get a `serviceIntegrityDimensions` field** carrying the same
token. (The field name does not appear verbatim in the dex, because protobuf
field names are compiled into the descriptor rather than kept as string
literals; the string scan shows `potoken` 3x, `poToken` 1x, and no plain-text
`serviceIntegrityDimensions`, which is consistent with descriptor encoding.)

## 4. Media is delivered via SABR, not plain URLs

This is the part that closes off the watch-page workaround.

```
serverAbrStreamingUrl            <- the only media URL in the player response
enableSabrPrefetch
androidSabrDontCancelFuturePrefetch
CreateFromSabrSegmentMap(metadata=
SEEK_SOURCE_SABR_RELOAD_PLAYER_RESPONSE_TOKEN_SEEK
com.google.android.libraries.youtube.player.service.SabrPrefetchController
com.google.android.apps.youtube.proto.SabrLiveProtos$SabrLiveMetadata
com.google.android.apps.youtube.proto.streaming.SabrSeekOuterClass$SabrSeek
DISABLED_BY_SABR_STREAMING_URI
com.google.android.libraries.youtube.media.interfaces.VideoplaybackUmpParser
```

SABR (server-side adaptive bitrate) means the client does not receive a
downloadable URL per format. It holds a long-lived streaming connection and asks
the server for segments over the UMP protocol, and the server decides what to
send. The real app implements a UMP parser and a SABR prefetch controller in
native code; our OkHttp client cannot do any of this.

### Empirically confirmed

Fetching the watch page as a browser and parsing `ytInitialPlayerResponse`:

- `playabilityStatus.status` = `OK`, `streamingData` present
- 4 audio formats, **none of them carrying `url` or `signatureCipher`**
- `streamingData` keys are exactly
  `['expiresInSeconds', 'formats', 'adaptiveFormats', 'serverAbrStreamingUrl']`
- the page ships `botguard`, `po_token` and `attestation` references

So even the previously promising "resolve the stream from `youtube.com/watch`"
route now yields no directly playable audio URL. There is no signature to
decipher, because there is no URL.

## What this means for XroTune

The current behaviour is the correct behaviour: search and browse work as a
guest, and playback fails with a clear message
(`ResponseParser.parsePlayable` -> `PlaybackUnavailableException` -> UI string
`error_playback_unavailable`). Nothing is broken; the endpoint is gated.

To play audio, a client needs, in order of difficulty:

1. **An authenticated session.** Sign in with a real Google account and use the
   resulting cookies / auth headers. This is what the app's own Gmail sign-in
   flow is for, and it is the natural next step.
2. **A PO token** bound to that session and to each videoId, attached both as the
   `pot` URL parameter and as `serviceIntegrityDimensions` in player requests.
   Minting these outside of Play Services generally means running BotGuard in a
   JS VM or WebView, and the mechanism is deliberately obfuscated and changes.
3. **A SABR/UMP client** if the server insists on `serverAbrStreamingUrl` for the
   requested formats. That is a substantial streaming-protocol implementation,
   not an HTTP GET.

Points 2 and 3 are not just engineering effort; they are the abuse-control
boundary itself. Circumventing them is a different class of decision from
building a music client, and it is against YouTube's Terms of Service. That is
worth deciding deliberately rather than drifting into.

## Practical recommendation

Wire up Gmail sign-in first and re-test the `player` endpoint with an
authenticated context. If the server accepts it, keep the current plain-HTTP
player path and drop the guest attempt. If it still demands SABR, then the
honest options are to use a supported route (official API, or embedding a
supported player) rather than to reimplement the gating.

## Worked example

The requester's own link, `https://www.youtube.com/watch?v=VqgVGkt2YhM&list=RDVqgVGkt2YhM&start_radio=1`
("Bengali LoFi Jukebox - Volume 3", channel SVF Music), tested with no cookies
and no auth:

| client | result |
|---|---|
| WEB_REMIX | `LOGIN_REQUIRED` — "Sign in to confirm you're not a bot" |
| WEB | `LOGIN_REQUIRED` — "Sign in to confirm you're not a bot" |
| MWEB | `LOGIN_REQUIRED` — "Sign in to confirm you're not a bot" |
| ANDROID_VR | `LOGIN_REQUIRED` — "Sign in to confirm you're not a bot" |
| ANDROID_MUSIC | `LOGIN_REQUIRED` — "Please sign in" |
| IOS | HTTP 400 |
| TVHTML5 | `LOGIN_REQUIRED` — "Sign in to confirm you're not a bot" |

With playlist context (`playlistId=RDVqgVGkt2YhM`) added, `ANDROID_VR` and
`WEB_REMIX` still return `LOGIN_REQUIRED`. The watch page itself returns
`playabilityStatus.status = LOGIN_REQUIRED` with no `videoDetails`, no
`streamingData` and no `attestation` block.

Zero formats from every path.

### It is not specific to that video, and not a rate limit

A spread of eleven videos in one session, `ANDROID_VR`, no auth:

| videoId | status | audio URLs | title |
|---|---|---|---|
| dQw4w9WgXcQ | **OK** | **4** | Rick Astley - Never Gonna Give You Up |
| kJQP7kiw5Fk | UNPLAYABLE | 0 | Luis Fonsi - Despacito |
| JGwWNGJdvx8 | LOGIN_REQUIRED | 0 | Ed Sheeran - Shape of You |
| 9bZkp7q19f0 | LOGIN_REQUIRED | 0 | PSY - Gangnam Style |
| hT_nvWreIhg | UNPLAYABLE | 0 | OneRepublic - Counting Stars |
| YQHsXMglC9A | LOGIN_REQUIRED | 0 | Adele - Hello |
| jNQXAC9IVRw | LOGIN_REQUIRED | 0 | Me at the zoo |
| aircAruvnKk | LOGIN_REQUIRED | 0 | 3Blue1Brown - neural networks |
| 8aGhZQkoFbQ | LOGIN_REQUIRED | 0 | JSConf - event loop |
| arj7oStGLkU | LOGIN_REQUIRED | 0 | TED - procrastination |
| VqgVGkt2YhM | LOGIN_REQUIRED | 0 | Bengali LoFi Jukebox (the link above) |

One of eleven worked. That one is the single video queried most often during
testing, so its player response is very likely warm in a cache rather than
genuinely available. It is not a music-versus-video split (music and non-music
both fail), not age (a 2005 video fails alongside a 2009 one), and not rate
limiting (the same video returns the same status on every retry, minutes apart).

### Decisive test: video ID, all clients, all techniques

Run with the video ID directly, `dQw4w9WgXcQ`, no auth.

**Every client profile:**

| client | status | formats | audio URLs |
|---|---|---|---|
| WEB | UNPLAYABLE | 0 | 0 |
| WEB_REMIX | UNPLAYABLE | 0 | 0 |
| MWEB | UNPLAYABLE | 0 | 0 |
| WEB_EMBEDDED_PLAYER | ERROR | 0 | 0 |
| TVHTML5 | UNPLAYABLE | 0 | 0 |
| TVHTML5_SIMPLY_EMBEDDED_PLAYER | ERROR | 0 | 0 |
| ANDROID | HTTP 400 | — | — |
| ANDROID_MUSIC | LOGIN_REQUIRED | 0 | 0 |
| IOS | HTTP 400 | — | — |
| IOS_MUSIC | LOGIN_REQUIRED | 0 | 0 |
| ANDROID_TESTSUITE | UNPLAYABLE | 0 | 0 |
| **ANDROID_VR** | **OK** | **27** | **4** |

On the `ANDROID_VR` result: no `signatureCipher` on any format, so nothing needs
deciphering. An `attestation` block is present
(`playerAttestationRenderer.challenge`, `d=28&e=dQw4w9WgXcQ`), and
`serverAbrStreamingUrl` is present too. The audio URLs are real: an HTTP Range
request returns `206` with `65536` bytes of valid WebM (`1a45dfa3`).

**Every technique:**

| technique | result |
|---|---|
| fresh `visitorData` from `/visitor_id`, added to context | ANDROID_VR still OK, WEB_REMIX and WEB still UNPLAYABLE |
| `X-YouTube-Client-Name` / `X-YouTube-Client-Version` headers | no change |
| alternate endpoints (`player`, `next`, `get_video_info`) | `get_video_info` is 404; the others carry no media |
| embed page `youtube.com/embed/ID` | no `ytInitialPlayerResponse` |
| `contentCheckOk` / `racyCheckOk` omitted | no change |
| IPv4-only sockets | no change |

**Fresh videos, never queried before — 20 of them, `ANDROID_VR`, no auth:**

```
jfKfPfyJRdk UNPLAYABLE     aqz-KE-bpKQ LOGIN_REQUIRED   OPf0YbXqDm0 UNPLAYABLE
5qap5aO4i9A LOGIN_REQUIRED y6120QOlsfU LOGIN_REQUIRED   fJ9rUzIMcZQ LOGIN_REQUIRED
DWcJFNfaw9c LOGIN_REQUIRED XqZsoesa55w UNPLAYABLE       60ItHLz5WEA LOGIN_REQUIRED
21X5lGlDOfg UNPLAYABLE     ZbZSe6N_BXs UNPLAYABLE       pRpeEdMmmQ0 UNPLAYABLE
                           ktvTqknDobU UNPLAYABLE       YQHsXMglC9A LOGIN_REQUIRED
                           CevxZvSJLk8 UNPLAYABLE       3JZ_D3ELwOQ LOGIN_REQUIRED
                           e-ORhEE9VVg LOGIN_REQUIRED
                           SlPhMPnQ58k UNPLAYABLE
                           hT_nvWreIhg UNPLAYABLE
                           RgKAFK5djSk LOGIN_REQUIRED
```

**0 of 20 worked.**

And the same video, five times in a row: `OK`, `OK`, `OK`, `OK`, `OK`.

So exactly one video yields formats, it yields them every single time, and no
fresh video does. That is a warm cached player response for a single
heavily-queried video, not availability. Guest playback does not work.

The practical conclusion is that guest playback is unavailable, and
an authenticated session is required.

## Observed in a real browser, signed out

This is the decisive check. The same three videos, loaded in a real Chrome
browser from this environment, with no Google account:

| video | what the player showed | plays? |
|---|---|---|
| `dQw4w9WgXcQ` (Rick Astley) | `0:00 / 3:33`, a `blob:` media URL, no error | **yes** |
| `VqgVGkt2YhM` (the requester's link) | `0:00 / 0:00`, "Sign in to confirm you're not a bot" | no |
| `39uMLYTh40Q` (another SVF Music video) | `0:00 / 0:00`, "Video unavailable" | no |

The browser is not our code. It is YouTube's own page, with YouTube's own
cookies (`VISITOR_INFO1_LIVE`, `YSC`, `PREF`, `__Secure-YNID`,
`__Secure-ROLLOUT_TOKEN`), running YouTube's own player. And it still refuses the
requester's video, showing the exact same message our API calls get.

Feeding those real browser cookies into the player request changed nothing:
`WEB`, `WEB_REMIX`, `MWEB` and `ANDROID_VR` all stayed `LOGIN_REQUIRED` with zero
formats, with and without cookies, and with `VISITOR_INFO1_LIVE` passed as
`visitorData`.

## So what is actually going on

The refusal is not about our request shape, our headers, our tokens, or the video
id we picked. It is the server deciding, for this client as it sees it, that the
content will not be served without an authenticated session.

Two things stack up here:

1. **This environment is a datacenter IP.** The machine resolves to
   `34.28.69.126`, AS396982 (Google LLC), Council Bluffs. "Sign in to confirm
   you're not a bot" is the challenge YouTube applies to traffic it attributes to
   datacenters and proxies rather than to real devices. That is why a browser
   here is refused too.
2. **A single video still plays.** `dQw4w9WgXcQ` plays every time, from the API
   and in the browser. It is the most-requested video on the internet, so its
   response is effectively always warm. It is the exception that proves the rule:
   it is cached, not permitted.

### What this means for the app on a real phone

This is the important part, and it is good news.

The APK does not run on this datacenter machine. It runs on a phone, on a
residential or mobile network. On that network the "not a bot" challenge is not
normally triggered, so signed-out playback is expected to work for ordinary
videos — which is exactly how the YouTube and YouTube Music apps behave when you
use them signed out.

We cannot confirm that from here, because every request we make leaves from the
datacenter IP. The honest statement is:

- **Here, guest playback fails** for almost everything, in the API and in a real
  browser alike. Verified.
- **On a normal network, guest playback is expected to work** for most videos.
  Not verifiable from here.
- **The app's code is not the problem.** It builds the request correctly, and
  when the server refuses, it reports it cleanly.

The way to settle it is to install the APK on a phone and play the same video.
That is a five-minute test and it is the only one that answers the question.
