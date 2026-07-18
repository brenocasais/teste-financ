package com.example.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "meu_financeiro_preferences")

class UserPreferences(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        private val IS_GUEST_MODE_KEY = booleanPreferencesKey("is_guest_mode")
    }

    // "SYSTEM", "LIGHT", "DARK"
    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: "SYSTEM"
    }

    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    val isGuestModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_GUEST_MODE_KEY] ?: false
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    suspend fun setGuestMode(isGuest: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_GUEST_MODE_KEY] = isGuest
        }
    }

    fun isMonthReviewedFlow(userId: String, month: String): Flow<Boolean> {
        val key = booleanPreferencesKey("reviewed_month_${userId}_${month}")
        return context.dataStore.data.map { preferences ->
            preferences[key] ?: false
        }
    }

    suspend fun setMonthReviewed(userId: String, month: String, reviewed: Boolean) {
        val key = booleanPreferencesKey("reviewed_month_${userId}_${month}")
        context.dataStore.edit { preferences ->
            preferences[key] = reviewed
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
