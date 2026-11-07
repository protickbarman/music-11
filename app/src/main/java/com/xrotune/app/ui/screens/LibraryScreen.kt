package com.xrotune.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xrotune.app.data.model.Track
import com.xrotune.app.ui.UiState
import com.xrotune.app.ui.components.EmptyBox
import com.xrotune.app.ui.components.ErrorBox
import com.xrotune.app.ui.components.LoadingBox
import com.xrotune.app.ui.components.SectionRow

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onTrackClick: (Track) -> Unit,
    isSignedIn: Boolean = false,
    onSignIn: () -> Unit = {},
) {
    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(s.message, onRetry = viewModel::refresh)
        is UiState.Success ->
            if (s.data.isEmpty()) {
                if (!isSignedIn) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "Your cloud library is empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Sign in with Google to sync your favorite songs, created playlists, and recommendations across devices.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = onSignIn) {
                                Text("Sign in with Google")
                            }
                        }
                    }
                } else {
                    EmptyBox("No liked songs or playlists yet. Tap heart on any song to save it!")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(s.data, key = { it.title }) { section ->
                        SectionRow(section, onTrackClick)
                    }
                }
            }
    }
}
