package com.xrotune.app.data.api

import com.xrotune.app.data.model.InnerTubeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin transport over the InnerTube RPC surface. Sends and receives JSON; the
 * protobuf variant used by the native app is intentionally not reproduced here.
 */
class InnerTubeClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    @Volatile
    var visitorData: String? = null

    /** Issues a POST to `endpoint` (e.g. "search") and parses the JSON body. */
    suspend fun post(
        endpoint: String,
        body: JSONObject,
        extraHeaders: Map<String, String> = emptyMap(),
        baseUrl: String = ApiConfig.BASE_URL,
        userAgent: String = ApiConfig.USER_AGENT,
        origin: String = "https://music.youtube.com",
    ): JSONObject = withContext(Dispatchers.IO) {
        try {
            executePost(endpoint, body, extraHeaders, baseUrl, userAgent, origin)
        } catch (e: InnerTubeException) {
            if (e.httpStatus == 401 && extraHeaders.isNotEmpty()) {
                // If extra headers (e.g. expired or incompatible auth) caused 401, retry as public guest
                executePost(endpoint, body, emptyMap(), baseUrl, userAgent, origin)
            } else {
                throw e
            }
        }
    }

    private fun executePost(
        endpoint: String,
        body: JSONObject,
        extraHeaders: Map<String, String>,
        baseUrl: String,
        userAgent: String,
        origin: String,
    ): JSONObject {
        val url = baseUrl + endpoint + "?key=" + ApiConfig.API_KEY + "&prettyPrint=false"
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMedia))
            .header("Content-Type", "application/json")
            .header("User-Agent", userAgent)
            .header("Origin", origin)
            .header("X-Goog-Api-Format-Version", "2")
            .apply { extraHeaders.forEach { (k, v) -> header(k, v) } }
            .build()

        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw InnerTubeException(
                        "Request to $endpoint failed (HTTP ${response.code})",
                        response.code,
                    )
                }
                if (text.isBlank()) {
                    throw InnerTubeException("Empty response from $endpoint", response.code)
                }
                val json = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    throw InnerTubeException("Malformed JSON from $endpoint: ${e.message}")
                }
                json.optJSONObject("responseContext")
                    ?.optString("visitorData")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { visitorData = it }
                return json
            }
        } catch (e: InnerTubeException) {
            throw e
        } catch (e: IOException) {
            throw InnerTubeException("Network error: ${e.message ?: "unknown"}")
        }
    }
}
