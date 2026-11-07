package com.xrotune.app.data.repo

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.xrotune.app.R
import com.xrotune.app.data.model.FirestoreAiRecommendation
import com.xrotune.app.data.model.FirestoreLikedSong
import com.xrotune.app.data.model.FirestorePlaylist
import com.xrotune.app.data.model.FirestoreUserProfile
import com.xrotune.app.util.OperationType
import com.xrotune.app.util.handleFirestoreError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
    )

    private val auth get() = com.google.firebase.auth.FirebaseAuth.getInstance()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun saveUserProfile(displayName: String, email: String, photoUrl: String?) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val path = docRef.path
        try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val data = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
                if (photoUrl != null) data["photoUrl"] = photoUrl
                docRef.set(data).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to displayName,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (photoUrl != null) updates["photoUrl"] = photoUrl
                docRef.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            throw e
        }
    }

    fun observeLikedSongs(): Flow<List<FirestoreLikedSong>> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("liked_songs")
        return colRef.snapshots().map { snapshot ->
            snapshot.toObjects(FirestoreLikedSong::class.java)
        }.catch { e ->
            if (e is Exception) handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }

    suspend fun likeSong(
        songId: String,
        title: String,
        artist: String,
        thumbnailUrl: String?,
        duration: String?,
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("liked_songs").document(songId)
        val data = mutableMapOf<String, Any>(
            "id" to songId,
            "userId" to uid,
            "title" to title,
            "artist" to artist,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        if (thumbnailUrl != null) data["thumbnailUrl"] = thumbnailUrl
        if (duration != null) data["duration"] = duration

        try {
            docRef.set(data).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun unlikeSong(songId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("liked_songs").document(songId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observePlaylists(): Flow<List<FirestorePlaylist>> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("playlists")
        return colRef.snapshots().map { snapshot ->
            snapshot.toObjects(FirestorePlaylist::class.java)
        }.catch { e ->
            if (e is Exception) handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }

    suspend fun createPlaylist(playlistId: String, title: String, description: String?) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("playlists").document(playlistId)
        val data = mutableMapOf<String, Any>(
            "id" to playlistId,
            "userId" to uid,
            "title" to title,
            "trackCount" to 0,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        if (description != null) data["description"] = description

        try {
            docRef.set(data).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun saveAiRecommendation(recId: String, prompt: String, mood: String?, suggestions: List<String>) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("ai_recommendations").document(recId)
        val data = mutableMapOf<String, Any>(
            "id" to recId,
            "userId" to uid,
            "prompt" to prompt,
            "suggestions" to suggestions,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        if (mood != null) data["mood"] = mood

        try {
            docRef.set(data).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }
}
