package com.cabin.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cabin.app.data.model.User
import com.cabin.app.data.remote.CabinJson
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cabin_session")

/**
 * Persists the auth token and cached user with DataStore, and keeps an
 * in-memory copy of the token so the OkHttp interceptor can read it synchronously.
 */
class SessionStore(private val context: Context) {

    private val tokenKey = stringPreferencesKey("token")
    private val userKey = stringPreferencesKey("user")

    @Volatile
    var currentToken: String? = null
        private set

    @Volatile
    var cachedUser: User? = null
        private set

    /** Loads persisted values into memory. Call once at startup. */
    suspend fun load() {
        val prefs = context.dataStore.data.first()
        currentToken = prefs[tokenKey]
        cachedUser = prefs[userKey]?.let {
            runCatching { CabinJson.decodeFromString<User>(it) }.getOrNull()
        }
    }

    suspend fun save(token: String, user: User) {
        currentToken = token
        cachedUser = user
        context.dataStore.edit {
            it[tokenKey] = token
            it[userKey] = CabinJson.encodeToString(user)
        }
    }

    /** Updates the cached user (e.g. after verification) without touching the token. */
    suspend fun updateUser(user: User) {
        cachedUser = user
        context.dataStore.edit { it[userKey] = CabinJson.encodeToString(user) }
    }

    suspend fun clear() {
        currentToken = null
        cachedUser = null
        context.dataStore.edit { it.clear() }
    }
}
