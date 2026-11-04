package com.xrotune.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xrotune.app.data.model.MusicSection
import com.xrotune.app.data.model.Track

@Composable
fun SectionRow(section: MusicSection, onTrackClick: (Track) -> Unit) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(section.items, key = { it.videoId }) { track ->
                VideoCard(
                    track = track,
                    onClick = { onTrackClick(track) },
                    modifier = Modifier.width(220.dp),
                )
            }
        }
    }
}
