package com.xrotune.app.data.repo

import com.xrotune.app.data.api.ApiConfig
import com.xrotune.app.data.api.InnerTubeClient
import com.xrotune.app.data.api.ResponseParser
import com.xrotune.app.data.model.InnerTubeException
import com.xrotune.app.data.model.MusicSection
import com.xrotune.app.data.model.PlaybackUnavailableException
import com.xrotune.app.data.model.PlayableMedia
import com.xrotune.app.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Music operations built on the InnerTube RPCs.
 *
 * `authHeader` is an optional `Authorization: Bearer <token>` value. It is
 * attached when the user signed in with Google, which is what lets the server
 * return account-scoped data (library, subscriptions) instead of a public feed.
 */
class MusicRepository(
    private val client: InnerTubeClient = InnerTubeClient(),
) {

    @Volatile
    var authHeader: String? = null

    private fun headers(): Map<String, String> =
        authHeader?.let { mapOf("Authorization" to it) } ?: emptyMap()

    private fun context(): JSONObject = ApiConfig.clientContext(client.visitorData)

    /**
     * Always load FEmusic_explore to yield playable music tracks, trending charts,
     * and videos across all guest and signed-in sessions without requiring YouTube cookies.
     */
    suspend fun getHome(): List<MusicSection> = withContext(Dispatchers.Default) {
        val body = JSONObject()
            .put("context", context())
            .put("browseId", "FEmusic_explore")
        val response = try {
            client.post("browse", body, headers())
        } catch (e: InnerTubeException) {
            if (e.httpStatus == 401 && headers().isNotEmpty()) {
                client.post("browse", body, emptyMap())
            } else {
                throw e
            }
        }
        ResponseParser.parseSections(response)
    }

    suspend fun getLibrary(): List<MusicSection> = withContext(Dispatchers.Default) {
        val body = JSONObject()
            .put("context", context())
            .put("browseId", "FEmusic_library_landing")
        val response = runCatching {
            client.post("browse", body, headers())
        }.getOrNull()
        if (response != null) {
            ResponseParser.parseSections(response)
        } else {
            emptyList()
        }
    }

    suspend fun search(
        query: String,
        filter: ApiConfig.SearchFilter = ApiConfig.SearchFilter.VIDEOS,
    ): List<Track> = withContext(Dispatchers.Default) {
        val body = JSONObject()
            .put("context", context())
            .put("query", query)
        if (filter.params.isNotEmpty()) body.put("params", filter.params)
        val response = client.post("search", body, headers())
        ResponseParser.parseTracks(response)
    }

    suspend fun suggestions(input: String): List<String> = withContext(Dispatchers.Default) {
        if (input.isBlank()) return@withContext emptyList()
        val body = JSONObject()
            .put("context", context())
            .put("input", input)
        runCatching {
            val response = client.post("music/get_search_suggestions", body, headers())
            com.xrotune.app.util.Json
                .findAll(response) { it.has("searchSuggestionsSectionRenderer") }
                .flatMap { section ->
                    com.xrotune.app.util.Json
                        .findAll(section) { it.has("suggestionRenderer") }
                        .mapNotNull { it.optJSONObject("suggestionRenderer") }
                        .mapNotNull { com.xrotune.app.util.Json.runsText(it.optJSONObject("suggestion")) }
                }
        }.getOrDefault(emptyList())
    }

    /**
     * Resolves a playable stream. The server gates this endpoint behind
     * account verification, so [PlaybackUnavailableException] is a normal,
     * expected outcome that the UI must surface rather than a crash.
     *
     * Playback identities are tried in order (see [ApiConfig.PLAYER_CLIENTS])
     * and the first that returns a playable stream wins. If every one refuses,
     * the last refusal is thrown so the message reflects the server's verdict.
     */
    suspend fun resolveMedia(videoId: String): PlayableMedia = withContext(Dispatchers.Default) {
        var lastFailure: PlaybackUnavailableException? = null
        for (client in ApiConfig.PLAYER_CLIENTS) {
            val body = JSONObject()
                .put("context", ApiConfig.clientContext(client, this@MusicRepository.client.visitorData))
                .put("videoId", videoId)
                .put("contentCheckOk", true)
                .put("racyCheckOk", true)
            val response = try {
                this@MusicRepository.client.post(
                    "player",
                    body,
                    headers(),
                    baseUrl = client.baseUrl,
                    userAgent = client.userAgent,
                    origin = client.origin,
                )
            } catch (e: InnerTubeException) {
                // A transport-level refusal for one identity should not abort
                // the whole resolution; try the next one.
                lastFailure = PlaybackUnavailableException("ERROR", e.message)
                continue
            }
            try {
                return@withContext ResponseParser.parsePlayable(response, videoId)
            } catch (e: PlaybackUnavailableException) {
                lastFailure = e
            }
        }
        throw lastFailure
            ?: PlaybackUnavailableException("ERROR", "No client returned a playable stream")
    }
}
