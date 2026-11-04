package com.xrotune.app.data.model

/** A single playable item. */
data class Track(
    val videoId: String,
    val title: String,
    val subtitle: String? = null,
    val durationText: String? = null,
    val thumbnailUrl: String? = null,
) {
    val artist: String get() = subtitle?.split("•")?.firstOrNull()?.trim().orEmpty()
}

/** A titled group of tracks, used by home and library feeds. */
data class MusicSection(
    val title: String,
    val items: List<Track>,
)

/** A single playable audio stream resolved from the player endpoint. */
data class PlayableMedia(
    val videoId: String,
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val itag: Int,
    val title: String? = null,
    val artist: String? = null,
    val thumbnailUrl: String? = null,
)

/** Raised when the player endpoint refuses to hand out a stream. */
class PlaybackUnavailableException(val status: String, val reason: String?) :
    Exception(reason ?: status)

/** Raised for transport and protocol failures. */
class InnerTubeException(message: String, val httpStatus: Int? = null) : Exception(message)
