package com.xrotune.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.xrotune.app.data.model.Track
import com.xrotune.app.ui.UiState
import com.xrotune.app.ui.components.ErrorBox
import com.xrotune.app.ui.components.LoadingBox
import com.xrotune.app.ui.components.SectionRow

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onTrackClick: (Track) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(s.message, onRetry = viewModel::refresh)
        is UiState.Success -> {
            if (s.data.isEmpty()) {
                LoadingBox()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(s.data, key = { it.title }) { section ->
                        SectionRow(section, onTrackClick)
                    }
                }
            }
        }
    }
}
