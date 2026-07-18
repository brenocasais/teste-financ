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

        private val NOTIFY_LIMITS_KEY = booleanPreferencesKey("notify_limits")
        private val NOTIFY_CREDIT_CARD_KEY = booleanPreferencesKey("notify_credit_card")
        private val NOTIFY_INSTALLMENT_KEY = booleanPreferencesKey("notify_installment")
        private val NOTIFY_GOAL_KEY = booleanPreferencesKey("notify_goal")
        private val NOTIFY_WEEKLY_REVIEW_KEY = booleanPreferencesKey("notify_weekly_review")
        private val CREDIT_CARD_DAYS_BEFORE_KEY = intPreferencesKey("credit_card_days_before")
        private val WEEKLY_REVIEW_DAY_KEY = intPreferencesKey("weekly_review_day") // 1 = Sunday, 2 = Monday, ..., 7 = Saturday
        private val WEEKLY_REVIEW_TIME_KEY = stringPreferencesKey("weekly_review_time") // HH:mm
    }

    // Settings Flows
    val notifyLimitsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFY_LIMITS_KEY] ?: true
    }

    val notifyCreditCardFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFY_CREDIT_CARD_KEY] ?: true
    }

    val notifyInstallmentFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFY_INSTALLMENT_KEY] ?: true
    }

    val notifyGoalFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFY_GOAL_KEY] ?: true
    }

    val notifyWeeklyReviewFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFY_WEEKLY_REVIEW_KEY] ?: true
    }

    val creditCardDaysBeforeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CREDIT_CARD_DAYS_BEFORE_KEY] ?: 3
    }

    val weeklyReviewDayFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[WEEKLY_REVIEW_DAY_KEY] ?: 1 // Sunday
    }

    val weeklyReviewTimeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[WEEKLY_REVIEW_TIME_KEY] ?: "20:00"
    }

    suspend fun setNotifyLimits(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[NOTIFY_LIMITS_KEY] = enabled }
    }

    suspend fun setNotifyCreditCard(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[NOTIFY_CREDIT_CARD_KEY] = enabled }
    }

    suspend fun setNotifyInstallment(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[NOTIFY_INSTALLMENT_KEY] = enabled }
    }

    suspend fun setNotifyGoal(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[NOTIFY_GOAL_KEY] = enabled }
    }

    suspend fun setNotifyWeeklyReview(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[NOTIFY_WEEKLY_REVIEW_KEY] = enabled }
    }

    suspend fun setCreditCardDaysBefore(days: Int) {
        context.dataStore.edit { preferences -> preferences[CREDIT_CARD_DAYS_BEFORE_KEY] = days }
    }

    suspend fun setWeeklyReviewDay(day: Int) {
        context.dataStore.edit { preferences -> preferences[WEEKLY_REVIEW_DAY_KEY] = day }
    }

    suspend fun setWeeklyReviewTime(time: String) {
        context.dataStore.edit { preferences -> preferences[WEEKLY_REVIEW_TIME_KEY] = time }
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
