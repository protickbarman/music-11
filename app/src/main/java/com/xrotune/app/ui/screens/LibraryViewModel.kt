package com.xrotune.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.xrotune.app.data.model.MusicSection
import com.xrotune.app.data.model.Track
import com.xrotune.app.data.repo.FirestoreRepository
import com.xrotune.app.data.repo.MusicRepository
import com.xrotune.app.ui.UiState
import com.xrotune.app.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: MusicRepository,
    private val firestoreRepository: FirestoreRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<MusicSection>>>(UiState.Loading)
    val state: StateFlow<UiState<List<MusicSection>>> = _state.asStateFlow()

    private var loaded = false

    fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        refresh()
    }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val sections = mutableListOf<MusicSection>()

                // Load cloud-synced Firestore liked songs & playlists if signed in
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (currentUser != null && firestoreRepository != null) {
                    val likedSongs = runCatching {
                        firestoreRepository.observeLikedSongs().firstOrNull()
                    }.getOrNull().orEmpty()

                    if (likedSongs.isNotEmpty()) {
                        val tracks = likedSongs.map { song ->
                            Track(
                                videoId = song.id,
                                title = song.title,
                                subtitle = song.artist,
                                thumbnailUrl = song.thumbnailUrl,
                                durationText = song.duration,
                            )
                        }
                        sections.add(
                            MusicSection(
                                title = "Cloud Favorites (Firestore)",
                                items = tracks,
                            ),
                        )
                    }

                    val playlists = runCatching {
                        firestoreRepository.observePlaylists().firstOrNull()
                    }.getOrNull().orEmpty()

                    if (playlists.isNotEmpty()) {
                        val playlistTracks = playlists.map { p ->
                            Track(
                                videoId = p.id,
                                title = p.title,
                                subtitle = p.description ?: "${p.trackCount} tracks",
                                thumbnailUrl = null,
                                durationText = null,
                            )
                        }
                        sections.add(
                            MusicSection(
                                title = "Your Playlists (Firestore)",
                                items = playlistTracks,
                            ),
                        )
                    }
                }

                // Also load InnerTube library sections if available
                val ytSections = runCatching { repository.getLibrary() }.getOrDefault(emptyList())
                sections.addAll(ytSections)

                _state.value = UiState.Success(sections)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.toUserMessage())
            }
        }
    }
}
