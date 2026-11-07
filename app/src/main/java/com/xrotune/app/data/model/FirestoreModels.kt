package com.xrotune.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FirestoreLikedSong(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val duration: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FirestorePlaylist(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String? = null,
    val trackCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FirestoreAiRecommendation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val prompt: String = "",
    val mood: String? = null,
    val suggestions: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
)
