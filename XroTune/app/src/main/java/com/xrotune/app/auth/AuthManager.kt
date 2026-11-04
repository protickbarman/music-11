package com.xrotune.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.xrotune.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom

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
 * Google Sign-In via Credential Manager.
 *
 * Requires an Android OAuth client registered in Google Cloud Console for this
 * package name and signing certificate, plus the web client ID in
 * `BuildConfig.GOOGLE_WEB_CLIENT_ID`.
 */
class AuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(): SignInResult = withContext(Dispatchers.IO) {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.startsWith("REPLACE_WITH")) {
            return@withContext SignInResult.Failure(
                "Google Sign-In is not configured. Add your web client ID to " +
                    "GOOGLE_WEB_CLIENT_ID in app/build.gradle.kts.",
            )
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(generateNonce())
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val response = credentialManager.getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data)
                SignInResult.Success(
                    UserAccount(
                        id = idToken.id,
                        displayName = idToken.displayName,
                        email = idToken.id,
                        photoUrl = idToken.profilePictureUri?.toString(),
                        idToken = idToken.idToken,
                    ),
                )
            } else {
                SignInResult.Failure("Unexpected credential type: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: GetCredentialException) {
            SignInResult.Failure(e.message ?: "Sign-in failed")
        } catch (e: Exception) {
            SignInResult.Failure(e.message ?: "Sign-in failed")
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
