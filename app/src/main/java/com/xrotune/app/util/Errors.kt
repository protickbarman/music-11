package com.xrotune.app.util

import com.xrotune.app.data.model.InnerTubeException
import com.xrotune.app.data.model.PlaybackUnavailableException
import java.io.IOException

/** Maps exceptions to short, user-facing text. */
fun Throwable.toUserMessage(): String = when (this) {
    is PlaybackUnavailableException ->
        if (status == "LOGIN_REQUIRED") {
            "YouTube requires bot verification for this specific video. Try another track or version."
        } else {
            "Playback restricted by YouTube ($status). Try another track or search."
        }
    is InnerTubeException ->
        message ?: "The music service returned an unexpected response."
    is IOException ->
        "No network connection. Check your internet and try again."
    else ->
        message ?: "Something went wrong. Please try again."
}
