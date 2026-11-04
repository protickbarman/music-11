package com.xrotune.app.data.api

import org.json.JSONObject

/**
 * InnerTube endpoint configuration.
 *
 * The APK analysed earlier talks to youtubei.googleapis.com with the ANDROID_MUSIC
 * client. Here the WEB_REMIX client is used because it is the same backend the
 * music.youtube.com web player uses and it accepts a browser-style request without
 * device attestation for public (non-streaming) endpoints.
 */
object ApiConfig {

    const val BASE_URL = "https://music.youtube.com/youtubei/v1/"

    /** Public key shipped with the YouTube Music web client. */
    const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

    const val CLIENT_NAME = "WEB_REMIX"
    const val CLIENT_VERSION = "1.20241104.01.00"

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /**
     * A playback identity. The server decides whether to hand out media based
     * on which client is asking, so the resolver tries several in turn, the
     * same way the real clients do, and keeps the first one that answers with
     * a playable stream.
     */
    data class PlayerClient(
        val name: String,
        val version: String,
        val userAgent: String,
        val origin: String,
        val baseUrl: String,
        val android: Boolean = false,
    )

    /** Order matters: cheapest and most permissive first. */
    val PLAYER_CLIENTS: List<PlayerClient> = listOf(
        PlayerClient(
            name = "ANDROID_VR",
            version = "1.60.19",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 " +
                "(Linux; U; Android 12; GB) gzip",
            origin = "https://www.youtube.com",
            baseUrl = "https://www.youtube.com/youtubei/v1/",
            android = true,
        ),
        PlayerClient(
            name = "ANDROID_MUSIC",
            version = "7.27.52",
            userAgent = "com.google.android.apps.youtube.music/7.27.52 " +
                "(Linux; U; Android 11; GB) gzip",
            origin = "https://music.youtube.com",
            baseUrl = "https://music.youtube.com/youtubei/v1/",
            android = true,
        ),
        PlayerClient(
            name = "WEB_REMIX",
            version = CLIENT_VERSION,
            userAgent = USER_AGENT,
            origin = "https://music.youtube.com",
            baseUrl = "https://music.youtube.com/youtubei/v1/",
        ),
        PlayerClient(
            name = "WEB",
            version = "2.20241104.01.00",
            userAgent = USER_AGENT,
            origin = "https://www.youtube.com",
            baseUrl = "https://www.youtube.com/youtubei/v1/",
        ),
    )

    /**
     * Search filter params. An unfiltered search returns mostly artists,
     * profiles and podcasts (which carry only a browseEndpoint and cannot be
     * played), so the default is to ask for videos, which always yields
     * directly playable rows.
     */
    enum class SearchFilter(val params: String) {
        VIDEOS("EgWKAQIQAWoKEAkQChAFEAMQBA=="),
        ALL(""),
    }

    fun clientContext(visitorData: String?): JSONObject {
        val client = JSONObject()
            .put("clientName", CLIENT_NAME)
            .put("clientVersion", CLIENT_VERSION)
            .put("hl", "en")
            .put("gl", "US")
        if (!visitorData.isNullOrBlank()) client.put("visitorData", visitorData)
        return JSONObject().put("client", client)
    }

    /** Context for a specific playback identity, including Android-only fields. */
    fun clientContext(client: PlayerClient, visitorData: String?): JSONObject {
        val c = JSONObject()
            .put("clientName", client.name)
            .put("clientVersion", client.version)
            .put("hl", "en")
            .put("gl", "US")
        if (client.android) {
            c.put("androidSdkVersion", 32)
            c.put("osName", "Android")
            c.put("osVersion", "12")
        }
        if (!visitorData.isNullOrBlank()) c.put("visitorData", visitorData)
        return JSONObject().put("client", c)
    }
}
