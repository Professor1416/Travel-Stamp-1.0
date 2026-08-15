package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

interface UserPreferencesRepository {
    val hasCompletedOnboarding: StateFlow<Boolean>
    val themeMode: StateFlow<AppThemeMode>
    fun setOnboardingCompleted(completed: Boolean)
    fun setThemeMode(mode: AppThemeMode)
}

class UserPreferencesRepositoryImpl(context: Context) : UserPreferencesRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("travel_stamp_prefs", Context.MODE_PRIVATE)

    private val _hasCompletedOnboarding = MutableStateFlow(
        prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    )
    override val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private val _themeMode = MutableStateFlow(
        loadThemeMode()
    )
    override val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private fun loadThemeMode(): AppThemeMode {
        val saved = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        return try {
            AppThemeMode.valueOf(saved)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    override fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _hasCompletedOnboarding.value = completed
    }

    override fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
        private const val KEY_THEME_MODE = "key_theme_mode"
    }
}
