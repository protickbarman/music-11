#!/usr/bin/env python3
"""
Live test of the InnerTube API that the YouTube Music APK uses.
Exercises: search -> player -> streaming URL, mirroring the APK's flow.
Only verifies reachability; does not download full tracks.
"""
import json, urllib.request, urllib.error, gzip, io, sys

# WEB_REMIX = the YouTube Music web client identifier
CLIENT = {
    "clientName": "WEB_REMIX",
    "clientVersion": "1.20241104.01.00",
    "hl": "en",
    "gl": "US",
}
# Public YouTube Music web API key (same one the web app ships)
API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
BASE = "https://music.youtube.com/youtubei/v1"

def call(endpoint, body, key=API_KEY):
    url = f"{BASE}/{endpoint}?key={key}&prettyPrint=false"
    data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept-Encoding", "gzip")
    req.add_header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
    req.add_header("Origin", "https://music.youtube.com")
    req.add_header("X-Goog-Api-Format-Version", "2")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read()
            if r.headers.get("Content-Encoding") == "gzip":
                raw = gzip.decompress(raw)
            return r.status, json.loads(raw)
    except urllib.error.HTTPError as e:
        return e.code, {"error": e.read()[:300].decode("utf-8", "replace")}

print("=" * 60)
print("TEST 1: search endpoint")
print("=" * 60)
st, res = call("search", {"context": {"client": CLIENT}, "query": "Daft Punk"})
print("HTTP status:", st)
if st == 200:
    # walk the response to find song titles
    def walk(o, out):
        if isinstance(o, dict):
            if "musicResponsiveListItemRenderer" in o or "musicTwoRowItemRenderer" in o:
                pass
            for k, v in o.items():
                if k == "title" and isinstance(v, dict) and "runs" in v:
                    out.append("".join(r.get("text", "") for r in v["runs"]))
                walk(v, out)
        elif isinstance(o, list):
            for i in o: walk(i, out)
    titles = []
    walk(res, titles)
    print("results found:", len(titles))
    for t in titles[:8]:
        print("  -", t)
    # find a videoId
    vids = []
    def walk2(o, out):
        if isinstance(o, dict):
            if "videoId" in o and isinstance(o["videoId"], str):
                out.append(o["videoId"])
            for v in o.values(): walk2(v, out)
        elif isinstance(o, list):
            for i in o: walk2(i, out)
    walk2(res, vids)
    uniq = list(dict.fromkeys(vids))
    print("videoIds found:", len(uniq))
    print("first videoId:", uniq[0] if uniq else None)
    VIDEO = uniq[0] if uniq else None
else:
    print("response:", json.dumps(res)[:500])
    VIDEO = None

print()
print("=" * 60)
print("TEST 2: player endpoint -> streamingData")
print("=" * 60)
if VIDEO:
    st, pres = call("player", {"context": {"client": CLIENT}, "videoId": VIDEO, "contentCheckOk": True, "racyCheckOk": True})
    print("HTTP status:", st)
    if st == 200:
        ps = pres.get("playbackTracking") is not None
        sd = pres.get("streamingData")
        print("has streamingData:", sd is not None)
        if sd:
            fmts = sd.get("adaptiveFormats", []) + sd.get("formats", [])
            print("total formats:", len(fmts))
            audio = [f for f in fmts if f.get("mimeType", "").startswith("audio")]
            print("audio formats:", len(audio))
            for f in audio[:6]:
                print(f"  itag={f.get('itag')} mime={f.get('mimeType')} bitrate={f.get('bitrate')} "
                      f"has_url={'url' in f} has_cipher={'signatureCipher' in f or 'cipher' in f}")
            # pick highest-bitrate audio with a direct url
            direct = [f for f in audio if "url" in f]
            if direct:
                best = max(direct, key=lambda f: f.get("bitrate", 0))
                url = best["url"]
                host = url.split("/")[2]
                print("chosen audio host:", host)
                print("chosen itag:", best.get("itag"), "bitrate:", best.get("bitrate"))
                print()
                print("=" * 60)
                print("TEST 3: media stream reachability (googlevideo)")
                print("=" * 60)
                # HEAD request only - verify the stream is served, do not download
                req = urllib.request.Request(url, method="HEAD")
                req.add_header("User-Agent", CLIENT["clientName"])
                try:
                    with urllib.request.urlopen(req, timeout=30) as r:
                        print("HTTP status:", r.status)
                        print("Content-Type:", r.headers.get("Content-Type"))
                        print("Content-Length:", r.headers.get("Content-Length"))
                        print("Accept-Ranges:", r.headers.get("Accept-Ranges"))
                        print(">> stream endpoint IS reachable and serves audio")
                except Exception as e:
                    print("HEAD failed:", e)
            else:
                print("no direct-url audio formats (all ciphered)")
        else:
            print("player returned no streamingData (keys:", list(pres.keys())[:12], ")")
    else:
        print("response:", json.dumps(res)[:400])
else:
    print("skipped: no videoId from search")
print()
print("DONE")
