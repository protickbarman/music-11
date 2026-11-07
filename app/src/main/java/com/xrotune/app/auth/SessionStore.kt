package com.xrotune.app.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xrotune_session")

/** Persists the signed-in account so the app does not ask on every launch. */
class SessionStore(private val context: Context) {

    private object Keys {
        val id = stringPreferencesKey("account_id")
        val name = stringPreferencesKey("account_name")
        val email = stringPreferencesKey("account_email")
        val photo = stringPreferencesKey("account_photo")
        val token = stringPreferencesKey("account_token")
        val guest = androidx.datastore.preferences.core.booleanPreferencesKey("guest_mode")
    }

    val isGuest: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.guest] ?: false
    }

    val account: Flow<UserAccount?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.id] ?: return@map null
        UserAccount(
            id = id,
            displayName = prefs[Keys.name],
            email = prefs[Keys.email],
            photoUrl = prefs[Keys.photo],
            idToken = prefs[Keys.token],
        )
    }

    suspend fun save(account: UserAccount) {
        context.dataStore.edit { prefs ->
            prefs[Keys.id] = account.id
            account.displayName?.let { prefs[Keys.name] = it }
            account.email?.let { prefs[Keys.email] = it }
            account.photoUrl?.let { prefs[Keys.photo] = it }
            account.idToken?.let { prefs[Keys.token] = it }
            prefs[Keys.guest] = false
        }
    }

    suspend fun setGuestMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.guest] = enabled
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
