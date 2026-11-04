package com.xrotune.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xrotune.app.data.model.Track
import com.xrotune.app.data.repo.MusicRepository
import com.xrotune.app.ui.UiState
import com.xrotune.app.util.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<UiState<List<Track>>?>(null)
    val state: StateFlow<UiState<List<Track>>?> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _query.value = value
        searchJob?.cancel()
        if (value.isBlank()) {
            _state.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce so we do not fire a request per keystroke
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(repository.search(value.trim()))
            } catch (e: Exception) {
                UiState.Error(e.toUserMessage())
            }
        }
    }

    fun retry() {
        val current = _query.value
        if (current.isNotBlank()) {
            _query.value = current
            onQueryChange(current)
        }
    }
}
