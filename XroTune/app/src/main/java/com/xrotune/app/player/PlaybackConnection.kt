package com.xrotune.app.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.xrotune.app.data.model.PlayableMedia
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the MediaController connection and turns resolved streams into
 * MediaItems for the playback service.
 */
class PlaybackConnection(private val context: Context) {

    /** Notified when the player's own state changes, so UI can stay in sync. */
    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onTrackEnded()
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var listener: Listener? = null

    val isConnected: Boolean get() = controller != null

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun currentPositionMs(): Long = controller?.currentPosition ?: 0L
    fun durationMs(): Long = controller?.duration?.takeIf { it > 0 } ?: 0L

    suspend fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        val built = future.await()
        built.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener?.onIsPlayingChanged(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) listener?.onTrackEnded()
            }
        })
        controller = built
    }

    fun play(media: PlayableMedia) {
        val c = controller ?: return
        val item = MediaItem.Builder()
            .setUri(media.url)
            .setMediaId(media.videoId)
            .setMimeType(media.mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(media.title ?: "Unknown")
                    .setArtist(media.artist)
                    .setArtworkUri(media.thumbnailUrl?.let { android.net.Uri.parse(it) })
                    .build(),
            )
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun release() {
        controller?.release()
        controller = null
        controllerFuture = null
    }

    private suspend fun ListenableFuture<MediaController>.await(): MediaController =
        suspendCancellableCoroutine { cont ->
            addListener(
                Runnable {
                    // get() throws if the connection failed; surface it instead
                    // of leaving the coroutine suspended forever.
                    runCatching { get() }
                        .onSuccess { cont.resume(it) }
                        .onFailure { cont.resumeWithException(it) }
                },
                MoreExecutors.directExecutor(),
            )
        }
}
