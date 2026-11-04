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
}
