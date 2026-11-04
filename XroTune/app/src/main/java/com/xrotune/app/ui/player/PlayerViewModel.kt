package com.xrotune.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xrotune.app.data.model.PlayableMedia
import com.xrotune.app.data.model.Track
import com.xrotune.app.data.repo.MusicRepository
import com.xrotune.app.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val track: Track? = null,
    val media: PlayableMedia? = null,
    val isResolving: Boolean = false,
    val error: String? = null,
)

class PlayerViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /**
     * Resolves the stream for [track] and reports it through [onReady].
     * Resolution failures are surfaced in state instead of thrown, because the
     * player endpoint legitimately refuses unverified clients.
     */
    fun play(track: Track, onReady: (PlayableMedia) -> Unit) {
        _state.value = PlayerUiState(track = track, isResolving = true)
        viewModelScope.launch {
            try {
                val media = repository.resolveMedia(track.videoId)
                _state.value = PlayerUiState(track = track, media = media)
                onReady(media)
            } catch (e: Exception) {
                _state.value = PlayerUiState(track = track, error = e.toUserMessage())
            }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}
