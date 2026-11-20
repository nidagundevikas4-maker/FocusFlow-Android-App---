package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.FocusMode
import com.example.model.FocusModeConfig
import com.example.model.ThemePreference
import com.example.model.UserPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.focusDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "focusflow_preferences")

class FocusPreferencesRepository(context: Context) {

  private val dataStore = context.applicationContext.focusDataStore

  private object Keys {
    val ACTIVE_FOCUS_MODE = stringPreferencesKey("active_focus_mode")
    val DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
    val SELECTED_DURATION_MINUTES = intPreferencesKey("selected_duration_minutes")
    val THEME_PREFERENCE = stringPreferencesKey("theme_preference")

    val DEEP_AUTO_DND = booleanPreferencesKey("deep_auto_dnd")
    val DEEP_STRICT_ALARMS_DND = booleanPreferencesKey("deep_strict_alarms_dnd")
    val DEEP_ACADEMIC_PRIORITY = booleanPreferencesKey("deep_academic_priority")

    val STUDY_AUTO_DND = booleanPreferencesKey("study_auto_dnd")
    val STUDY_ACADEMIC_PRIORITY = booleanPreferencesKey("study_academic_priority")

    val LIGHT_AUTO_DND = booleanPreferencesKey("light_auto_dnd")
    val LIGHT_ACADEMIC_PRIORITY = booleanPreferencesKey("light_academic_priority")
  }

  val preferencesFlow: Flow<UserPreferences> =
    dataStore.data
      .catch { exception ->
        if (exception is IOException) {
          emit(emptyPreferences())
        } else {
          throw exception
        }
      }
      .map { prefs ->
        val mode =
          prefs[Keys.ACTIVE_FOCUS_MODE]?.let { raw ->
            FocusMode.entries.firstOrNull { it.name == raw }
          } ?: FocusMode.STUDY_MODE

        val theme =
          prefs[Keys.THEME_PREFERENCE]?.let { raw ->
            ThemePreference.entries.firstOrNull { it.name == raw }
          } ?: ThemePreference.SYSTEM

        val dailyGoal = (prefs[Keys.DAILY_GOAL_MINUTES] ?: 120).coerceIn(30, 360)
        val duration =
          (prefs[Keys.SELECTED_DURATION_MINUTES] ?: mode.defaultDurationMinutes).coerceIn(5, 180)

        UserPreferences(
          activeFocusMode = mode,
          dailyGoalMinutes = dailyGoal,
          selectedDurationMinutes = duration,
          themePreference = theme,
          deepFocusConfig =
            FocusModeConfig(
              autoEnableDnd = prefs[Keys.DEEP_AUTO_DND] ?: true,
              strictAlarmsOnlyDnd = prefs[Keys.DEEP_STRICT_ALARMS_DND] ?: false,
              prioritizeAcademicNotifications = prefs[Keys.DEEP_ACADEMIC_PRIORITY] ?: true,
            ),
          studyModeConfig =
            FocusModeConfig(
              autoEnableDnd = prefs[Keys.STUDY_AUTO_DND] ?: true,
              strictAlarmsOnlyDnd = false,
              prioritizeAcademicNotifications = prefs[Keys.STUDY_ACADEMIC_PRIORITY] ?: true,
            ),
          lightFocusConfig =
            FocusModeConfig(
              autoEnableDnd = prefs[Keys.LIGHT_AUTO_DND] ?: false,
              strictAlarmsOnlyDnd = false,
              prioritizeAcademicNotifications = prefs[Keys.LIGHT_ACADEMIC_PRIORITY] ?: true,
            ),
        )
      }

  suspend fun setActiveFocusMode(mode: FocusMode) {
    dataStore.edit { prefs ->
      prefs[Keys.ACTIVE_FOCUS_MODE] = mode.name
      prefs[Keys.SELECTED_DURATION_MINUTES] = mode.defaultDurationMinutes
    }
  }

  suspend fun setDailyGoalMinutes(minutes: Int) {
    dataStore.edit { prefs ->
      prefs[Keys.DAILY_GOAL_MINUTES] = minutes.coerceIn(30, 360)
    }
  }

  suspend fun setSelectedDurationMinutes(minutes: Int) {
    dataStore.edit { prefs ->
      prefs[Keys.SELECTED_DURATION_MINUTES] = minutes.coerceIn(5, 180)
    }
  }

  suspend fun setThemePreference(theme: ThemePreference) {
    dataStore.edit { prefs ->
      prefs[Keys.THEME_PREFERENCE] = theme.name
    }
  }

  suspend fun updateModeAutoDnd(mode: FocusMode, enabled: Boolean) {
    dataStore.edit { prefs ->
      when (mode) {
        FocusMode.DEEP_FOCUS -> prefs[Keys.DEEP_AUTO_DND] = enabled
        FocusMode.STUDY_MODE -> prefs[Keys.STUDY_AUTO_DND] = enabled
        FocusMode.LIGHT_FOCUS -> prefs[Keys.LIGHT_AUTO_DND] = enabled
      }
    }
  }

  suspend fun updateDeepFocusAlarmsOnlyDnd(enabled: Boolean) {
    dataStore.edit { prefs ->
      prefs[Keys.DEEP_STRICT_ALARMS_DND] = enabled
    }
  }

  suspend fun updateModeAcademicPriority(mode: FocusMode, enabled: Boolean) {
    dataStore.edit { prefs ->
      when (mode) {
        FocusMode.DEEP_FOCUS -> prefs[Keys.DEEP_ACADEMIC_PRIORITY] = enabled
        FocusMode.STUDY_MODE -> prefs[Keys.STUDY_ACADEMIC_PRIORITY] = enabled
        FocusMode.LIGHT_FOCUS -> prefs[Keys.LIGHT_ACADEMIC_PRIORITY] = enabled
      }
    }
  }
}
