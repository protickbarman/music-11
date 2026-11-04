package com.xrotune.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import com.xrotune.app.data.model.Track
import com.xrotune.app.ui.UiState
import com.xrotune.app.ui.components.EmptyBox
import com.xrotune.app.ui.components.ErrorBox
import com.xrotune.app.ui.components.LoadingBox
import com.xrotune.app.ui.components.TrackRow

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onTrackClick: (Track) -> Unit,
) {
    val query by viewModel.query.collectAsState()
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true,
            placeholder = { Text("Songs, artists, albums…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search,
            ),
        )

        when (val s = state) {
            null -> EmptyBox("Search for music to get started.")
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(s.message, onRetry = viewModel::retry)
            is UiState.Success ->
                if (s.data.isEmpty()) {
                    EmptyBox("No results found.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(s.data, key = { it.videoId }) { track ->
                            TrackRow(track = track, onClick = { onTrackClick(track) })
                        }
                    }
                }
        }
    }
}
