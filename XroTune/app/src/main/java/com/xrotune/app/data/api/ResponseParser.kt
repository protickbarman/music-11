package com.xrotune.app.data.api

import com.xrotune.app.data.model.MusicSection
import com.xrotune.app.data.model.PlayableMedia
import com.xrotune.app.data.model.PlaybackUnavailableException
import com.xrotune.app.data.model.Track
import com.xrotune.app.util.Json
import org.json.JSONObject

/**
 * Turns InnerTube renderer trees into app models.
 *
 * YouTube has two shapes for list rows: `musicResponsiveListItemRenderer`
 * (songs, videos) and `musicTwoRowItemRenderer` (albums, artists, playlists).
 * Both carry a `navigationEndpoint.watchEndpoint.videoId` when they are playable.
 */
object ResponseParser {

    fun parseSections(root: JSONObject): List<MusicSection> {
        val sections = ArrayList<MusicSection>()

        Json.findAll(root) { it.has("musicShelfRenderer") }.forEach { holder ->
            val shelf = holder.optJSONObject("musicShelfRenderer") ?: return@forEach
            val title = Json.runsText(shelf.optJSONObject("title")) ?: return@forEach
            val items = collectTracks(shelf.opt("contents"))
            if (items.isNotEmpty()) sections += MusicSection(title, items)
        }

        Json.findAll(root) { it.has("musicCarouselShelfRenderer") }.forEach { holder ->
            val shelf = holder.optJSONObject("musicCarouselShelfRenderer") ?: return@forEach
            val title = Json.runsText(shelf.optJSONObject("header")?.let {
                it.optJSONObject("musicCarouselShelfBasicHeaderRenderer")?.optJSONObject("title")
            }) ?: return@forEach
            val items = collectTracks(shelf.opt("contents"))
            if (items.isNotEmpty()) sections += MusicSection(title, items)
        }

        return sections
    }

    fun parseTracks(root: JSONObject): List<Track> = collectTracks(root)

    /**
     * Collects every row that resolves to a playable videoId. Rows that only
     * carry a browseEndpoint (albums, artists, podcasts) are dropped, because
     * the player endpoint cannot resolve them.
     */
    private fun collectTracks(node: Any?): List<Track> {
        val out = ArrayList<Track>()
        val seen = HashSet<String>()

        fun add(id: String?, title: String?, subtitle: String?, duration: String?, thumb: String?) {
            if (id.isNullOrBlank() || !seen.add(id)) return
            out += Track(
                videoId = id,
                title = title ?: "Unknown",
                subtitle = subtitle,
                durationText = duration,
                thumbnailUrl = thumb,
            )
        }

        Json.findAll(node) { it.has("musicResponsiveListItemRenderer") }.forEach { holder ->
            val r = holder.optJSONObject("musicResponsiveListItemRenderer") ?: return@forEach
            add(videoIdOf(r), titleOf(r), subtitleOf(r), durationOf(r), thumbnailOf(r))
        }

        Json.findAll(node) { it.has("musicTwoRowItemRenderer") }.forEach { holder ->
            val r = holder.optJSONObject("musicTwoRowItemRenderer") ?: return@forEach
            add(
                videoIdOf(r),
                Json.runsText(r.optJSONObject("title")),
                Json.runsText(r.optJSONObject("subtitle")),
                null,
                thumbnailOf(r),
            )
        }

        return out
    }

    private fun videoIdOf(renderer: JSONObject): String? {
        Json.first(renderer) { it.optString("videoId").isNotBlank() }
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return Json.first(renderer) { it.has("watchEndpoint") }
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
    }

    private fun titleOf(renderer: JSONObject): String? {
        val cols = renderer.optJSONArray("flexColumns") ?: return null
        for (i in 0 until cols.length()) {
            val text = Json.runsText(
                cols.optJSONObject(i)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
            )
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    private fun subtitleOf(renderer: JSONObject): String? {
        val cols = renderer.optJSONArray("flexColumns") ?: return null
        for (i in 1 until cols.length()) {
            val text = Json.runsText(
                cols.optJSONObject(i)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
            )
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    private fun durationOf(renderer: JSONObject): String? {
        val timePattern = Regex("\\d+:\\d{2}")
        for (node in Json.findAll(renderer) { it.optString("label").isNotBlank() }) {
            timePattern.find(node.optString("label"))?.let { return it.value }
        }
        return null
    }

    private fun thumbnailOf(renderer: JSONObject): String? {
        val thumbs = Json.first(renderer) { it.has("thumbnails") }?.optJSONArray("thumbnails") ?: return null
        if (thumbs.length() == 0) return null
        return thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")?.takeIf { it.isNotBlank() }
    }

    /** Extracts playability status, failing loudly when the server refuses. */
    fun parsePlayable(root: JSONObject, requestedVideoId: String): PlayableMedia {
        val statusObj = root.optJSONObject("playabilityStatus")
        val status = statusObj?.optString("status").orEmpty()
        if (status.isNotEmpty() && status != "OK") {
            val reason = Json.runsText(statusObj?.optJSONObject("reason"))
                ?: statusObj?.optString("reason")?.takeIf { it.isNotBlank() }
            throw PlaybackUnavailableException(status, reason)
        }

        val streaming = root.optJSONObject("streamingData")
            ?: throw PlaybackUnavailableException("ERROR", "No streaming data returned")

        val formats = ArrayList<JSONObject>()
        streaming.optJSONArray("adaptiveFormats")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { formats += it }
        }
        streaming.optJSONArray("formats")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { formats += it }
        }

        val audio = formats.filter {
            it.optString("mimeType").startsWith("audio") && it.optString("url").isNotBlank()
        }
        if (audio.isEmpty()) {
            throw PlaybackUnavailableException("ERROR", "No directly playable audio stream")
        }

        val best = audio.maxByOrNull { it.optInt("bitrate", 0) }!!
        val details = root.optJSONObject("videoDetails")

        return PlayableMedia(
            videoId = requestedVideoId,
            url = best.optString("url"),
            mimeType = best.optString("mimeType"),
            bitrate = best.optInt("bitrate", 0),
            itag = best.optInt("itag", 0),
            title = details?.optString("title")?.takeIf { it.isNotBlank() },
            artist = details?.optString("author")?.takeIf { it.isNotBlank() },
            thumbnailUrl = thumbnailOf(root),
        )
    }
}
