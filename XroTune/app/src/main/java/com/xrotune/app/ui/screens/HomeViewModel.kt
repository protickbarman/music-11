package com.xrotune.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xrotune.app.data.model.MusicSection
import com.xrotune.app.data.repo.MusicRepository
import com.xrotune.app.ui.UiState
import com.xrotune.app.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<MusicSection>>>(UiState.Loading)
    val state: StateFlow<UiState<List<MusicSection>>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                UiState.Success(repository.getHome())
            } catch (e: Exception) {
                UiState.Error(e.toUserMessage())
            }
        }
    }
}
