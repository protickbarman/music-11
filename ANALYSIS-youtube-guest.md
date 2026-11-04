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
