package com.xrotune.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xrotune.app.auth.AuthManager
import com.xrotune.app.auth.SessionStore
import com.xrotune.app.auth.SignInResult
import com.xrotune.app.auth.UserAccount
import com.xrotune.app.data.repo.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AuthUiState(
    val account: UserAccount? = null,
    val restoring: Boolean = true,
    val signingIn: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(
    private val authManager: AuthManager,
    private val sessionStore: SessionStore,
    private val repository: MusicRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = sessionStore.account.first()
            saved?.let { applyAccount(it) }
            _state.value = _state.value.copy(restoring = false)
        }
    }

    fun signIn() {
        if (_state.value.signingIn) return
        _state.value = _state.value.copy(signingIn = true, error = null)
        viewModelScope.launch {
            when (val result = authManager.signIn()) {
                is SignInResult.Success -> {
                    sessionStore.save(result.account)
                    applyAccount(result.account)
                    _state.value = _state.value.copy(signingIn = false, error = null)
                }
                SignInResult.Cancelled ->
                    _state.value = _state.value.copy(signingIn = false)
                is SignInResult.Failure ->
                    _state.value = _state.value.copy(signingIn = false, error = result.message)
            }
        }
    }

    fun continueAsGuest() {
        _state.value = _state.value.copy(error = null)
    }

    fun signOut() {
        viewModelScope.launch {
            sessionStore.clear()
            repository.authHeader = null
            _state.value = AuthUiState(restoring = false)
        }
    }

    private fun applyAccount(account: UserAccount) {
        // The Google ID token is what the server uses to scope data to the user.
        account.idToken?.let { repository.authHeader = "Bearer $it" }
        _state.value = _state.value.copy(account = account)
    }
}
