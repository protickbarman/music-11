package com.xrotune.app.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import com.xrotune.app.data.repo.FirestoreRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

private const val TAG = "AiDjViewModel"

data class SuggestedTrack(
    val title: String,
    val artist: String,
    val reason: String,
)

data class AiPlaylistResponse(
    val playlistTitle: String,
    val moodDescription: String,
    val tracks: List<SuggestedTrack>,
)

sealed interface AiDjUiState {
    data object Idle : AiDjUiState
    data object Loading : AiDjUiState
    data class Success(val playlist: AiPlaylistResponse) : AiDjUiState
    data class Error(val message: String) : AiDjUiState
}

class AiDjViewModel(
    private val firestoreRepository: FirestoreRepository,
) : ViewModel() {

    private val playlistSchema = Schema.obj(
        properties = mapOf(
            "playlistTitle" to Schema.string(description = "Catchy playlist title"),
            "moodDescription" to Schema.string(description = "Short vibrant description of the vibe and theme"),
            "tracks" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Song title"),
                        "artist" to Schema.string(description = "Artist name"),
                        "reason" to Schema.string(description = "Why this track fits the vibe"),
                    ),
                ),
                description = "5 to 8 song recommendations matching the prompt",
            ),
        ),
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = playlistSchema
            },
        )
    }

    private val _uiState = MutableStateFlow<AiDjUiState>(AiDjUiState.Idle)
    val uiState: StateFlow<AiDjUiState> = _uiState.asStateFlow()

    fun generatePlaylist(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _uiState.value = AiDjUiState.Loading
            try {
                val systemPrompt = "You are an expert Music DJ and curator. Recommend real, popular, streaming-friendly music based on this request: '$prompt'."
                val response = withTimeout(60_000L) {
                    model.generateContent(systemPrompt)
                }

                val jsonString = response.text ?: throw IllegalStateException("Model returned empty text.")
                val json = JSONObject(jsonString)
                val playlistTitle = json.optString("playlistTitle", "Custom AI Mix")
                val moodDescription = json.optString("moodDescription", "")
                val tracksJson = json.optJSONArray("tracks")

                val tracks = mutableListOf<SuggestedTrack>()
                if (tracksJson != null) {
                    for (i in 0 until tracksJson.length()) {
                        val trackObj = tracksJson.optJSONObject(i) ?: continue
                        tracks.add(
                            SuggestedTrack(
                                title = trackObj.optString("title"),
                                artist = trackObj.optString("artist"),
                                reason = trackObj.optString("reason"),
                            ),
                        )
                    }
                }

                val result = AiPlaylistResponse(
                    playlistTitle = playlistTitle,
                    moodDescription = moodDescription,
                    tracks = tracks,
                )
                _uiState.value = AiDjUiState.Success(result)

                // Persist recommendation history to Firestore
                runCatching {
                    firestoreRepository.saveAiRecommendation(
                        recId = UUID.randomUUID().toString(),
                        prompt = prompt,
                        mood = moodDescription,
                        suggestions = tracks.map { "${it.title} - ${it.artist}" },
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI generation timed out", e)
                _uiState.value = AiDjUiState.Error("Generation timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI playlist generation failed", e)
                _uiState.value = AiDjUiState.Error(e.localizedMessage ?: "Failed to generate AI recommendations.")
            }
        }
    }

    fun saveToUserPlaylists(title: String, description: String?, onSaved: () -> Unit) {
        viewModelScope.launch {
            try {
                firestoreRepository.createPlaylist(
                    playlistId = UUID.randomUUID().toString(),
                    title = title,
                    description = description,
                )
                onSaved()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save playlist", e)
            }
        }
    }
}
