# The playback flow, step by step

Fixed video IDs only. No random sampling. Every token and challenge below is
copied from a live response.

## The flow has 3 stages, and stage 3 has 3 gates

```
STAGE 1  session identity        POST /youtubei/v1/visitor_id
STAGE 2  find something to play  POST /youtubei/v1/search
STAGE 3  ask to play it          POST /youtubei/v1/player
           GATE 1  playabilityStatus   is this video allowed at all?
           GATE 2  attestation         prove you are a real client
           GATE 3  streamingData       here is how to fetch the media
```

## Stage 1 — session identity

```
POST https://music.youtube.com/youtubei/v1/visitor_id?key=<KEY>
{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20241104.01.00",
 "hl":"en","gl":"US"}}}
```

Response:

```
visitorData = CgtPa3BaUjhWc2Y3YyiQzqzXBg%3D%3D
length      = 32 chars
decoded hex = 0a0b4f6b705a523856736637632890ceacd7060dc3dc
```

That decoded blob is the session id. The app sends it on every later request. It
is **not** a login and carries no rights.

## Stage 2 — search works

```
POST https://music.youtube.com/youtubei/v1/search?key=<KEY>
{"context":{"client":{"clientName":"WEB_REMIX",...}},
 "query":"bengali lofi","params":"EgWKAQIQAWoKEAkQChAFEAMQBA=="}
```

The `params` value is the videos filter. The response contained 20 distinct
videoIds:

```
LxUU1yf4auI  770vBnXoKk8  NZo3bWV5W5A  _TkpMkmLtP4  -YvD_acvuRI
Cslx5mP28XA  IKX7uQzwxQI  Zl8VP3wZqi8  dPCNiMzYW_E  gBKiEETrWDY
O7WnA8AU4O8  9mpu0pZfmVA  B6Ib8KKOeNM  ksiSu8K2lZg  QJQ_gX3_78M
```

Search and browse are fully functional as a guest.

## Stage 3 — the player request, and its three gates

```
POST https://www.youtube.com/youtubei/v1/player?key=<KEY>
{"context":{"client":{...}},"videoId":"<id>",
 "contentCheckOk":true,"racyCheckOk":true}
```

### GATE 1 — playabilityStatus

The server's first answer, before anything else. Refusal looks like:

```
status = LOGIN_REQUIRED
reason = Sign in to confirm you're not a bot
errorScreen present = True
```

On a refusal the response has only these keys:

```
adBreakHeartbeatParams, playabilityStatus, responseContext, trackingParams
```

No `videoDetails`, no `streamingData`, no `attestation`.

### GATE 2 — attestation

On an allowed request the server hands out a challenge:

```
attestation.playerAttestationRenderer.challenge =
  a=6&a2=1&b=l0r3iKC_D7D9pNIcbkibvwwzFJM&c=1793795859&d=28&e=dQw4w9WgXcQ&hh=FXVGJC5rm7tXkRyZJ6ZcLPXlM8bh5EUoYfXDqbMMyIE
```

| field | value | meaning |
|---|---|---|
| `a` | 6 | challenge version |
| `a2` | 1 | challenge sub-version |
| `b` | `l0r3iKC_D7D9pNIcbkibvwwzFJM` | server nonce, single use |
| `c` | 1793795859 | issue timestamp |
| `d` | 28 | client id (28 = ANDROID_VR, 67 = WEB_REMIX) |
| `e` | `dQw4w9WgXcQ` | the videoId this challenge is bound to |
| `hh` | `FXVGJC5r...` | integrity hash over the above |

The challenge is per video and per client. The client must solve it with
DroidGuard / BotGuard and return a token, which then travels as
`serviceIntegrityDimensions.poToken` in later requests and as `&pot=` on media
URLs.

**On a refused video this block is not sent at all.** The server declines before
offering a challenge, so there is nothing to solve.

### GATE 3 — streamingData

Only on success:

```
keys                  = [expiresInSeconds, formats, adaptiveFormats, serverAbrStreamingUrl]
serverAbrStreamingUrl = present
formats               = 27   (4 of them audio with a plain url)
```

## The decisive run

Search returned 20 ids. The first 15 were then played, **fixed order, no
randomness**, on two clients.

WEB_REMIX:

```
  1 LxUU1yf4auI   LOGIN_REQUIRED     0 formats
  2 770vBnXoKk8   LOGIN_REQUIRED     0
  3 NZo3bWV5W5A   LOGIN_REQUIRED     0
  4 _TkpMkmLtP4   LOGIN_REQUIRED     0
  5 -YvD_acvuRI   LOGIN_REQUIRED     0
  6 Cslx5mP28XA   LOGIN_REQUIRED     0
  7 IKX7uQzwxQI   LOGIN_REQUIRED     0
  8 Zl8VP3wZqi8   LOGIN_REQUIRED     0
  9 dPCNiMzYW_E   LOGIN_REQUIRED     0
 10 gBKiEETrWDY   LOGIN_REQUIRED     0
 11 O7WnA8AU4O8   LOGIN_REQUIRED     0
 12 9mpu0pZfmVA   LOGIN_REQUIRED     0
 13 B6Ib8KKOeNM   LOGIN_REQUIRED     0
 14 ksiSu8K2lZg   LOGIN_REQUIRED     0
 15 QJQ_gX3_78M   LOGIN_REQUIRED     0
```

**0 of 15.** The same 15 through ANDROID_VR: **also 0 of 15.**

So the very videos the search itself returned cannot be played. Nothing random
about it, nothing cache-related: these are the ids the app would show the user,
and every one is refused.

## What this means

Stage 3 is refused at **GATE 1**, before GATE 2 is even offered. That is the
whole answer: no token can help, because the server never asks for one. To get
past GATE 1 you must be a client the server already trusts, which means a
signed-in session with device attestation available — not a request shape tweak.

The count, therefore:

- **3 stages**
- **3 gates inside stage 3**
- **1 required token** (the poToken), which is only requested once GATE 1 passes
- **0 of 15** search results playable as a guest
