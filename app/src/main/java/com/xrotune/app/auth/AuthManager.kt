package com.xrotune.app.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.xrotune.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom

private const val TAG = "AuthManager"

/** Outcome of a sign-in attempt. */
sealed interface SignInResult {
    data class Success(val account: UserAccount) : SignInResult
    data object Cancelled : SignInResult
    data class Failure(val message: String) : SignInResult
}

data class UserAccount(
    val id: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val idToken: String?,
)

/**
 * Google Sign-In via Jetpack Credential Manager and Firebase Authentication.
 */
class AuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(): SignInResult = withContext(Dispatchers.IO) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            Log.e(TAG, "Missing default_web_client_id resource", e)
            return@withContext SignInResult.Failure(
                "Google Sign-In configuration missing: default_web_client_id not found in google-services.json",
            )
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
            .setNonce(generateNonce())
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        try {
            val response = credentialManager.getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                val idTokenStr = googleIdToken.idToken
                if (idTokenStr.isNullOrBlank()) {
                    return@withContext SignInResult.Failure("Google Sign-In returned an empty ID token")
                }

                // Authenticate with Firebase Authentication
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val authCredential = GoogleAuthProvider.getCredential(idTokenStr, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val firebaseUser = authResult.user

                SignInResult.Success(
                    UserAccount(
                        id = firebaseUser?.uid ?: googleIdToken.id,
                        displayName = firebaseUser?.displayName ?: googleIdToken.displayName,
                        email = firebaseUser?.email ?: googleIdToken.id,
                        photoUrl = firebaseUser?.photoUrl?.toString() ?: googleIdToken.profilePictureUri?.toString(),
                        idToken = idTokenStr,
                    ),
                )
            } else {
                SignInResult.Failure("Unexpected credential type: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            SignInResult.Cancelled
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential manager exception", e)
            SignInResult.Failure(e.message ?: "Sign-in failed")
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            SignInResult.Failure(e.message ?: "Sign-in failed")
        }
    }

    suspend fun signOut(): Unit = withContext(Dispatchers.IO) {
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear credential state", e)
        }
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
