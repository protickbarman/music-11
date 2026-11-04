package com.xrotune.app.util

import com.xrotune.app.data.model.InnerTubeException
import com.xrotune.app.data.model.PlaybackUnavailableException
import java.io.IOException

/** Maps exceptions to short, user-facing text. */
fun Throwable.toUserMessage(): String = when (this) {
    is PlaybackUnavailableException ->
        "This track can't be played right now (${status}). Google may require an extra verification step."
    is InnerTubeException ->
        message ?: "The music service returned an unexpected response."
    is IOException ->
        "No network connection. Check your internet and try again."
    else ->
        message ?: "Something went wrong. Please try again."
}
