package com.example.model

enum class ThemePreference(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark"),
}

data class FocusModeConfig(
  val autoEnableDnd: Boolean,
  val strictAlarmsOnlyDnd: Boolean,
  val prioritizeAcademicNotifications: Boolean,
)

data class UserPreferences(
  val activeFocusMode: FocusMode = FocusMode.STUDY_MODE,
  val dailyGoalMinutes: Int = 120,
  val selectedDurationMinutes: Int = 25,
  val themePreference: ThemePreference = ThemePreference.SYSTEM,
  val deepFocusConfig: FocusModeConfig =
    FocusModeConfig(
      autoEnableDnd = true,
      strictAlarmsOnlyDnd = false,
      prioritizeAcademicNotifications = true,
    ),
  val studyModeConfig: FocusModeConfig =
    FocusModeConfig(
      autoEnableDnd = true,
      strictAlarmsOnlyDnd = false,
      prioritizeAcademicNotifications = true,
    ),
  val lightFocusConfig: FocusModeConfig =
    FocusModeConfig(
      autoEnableDnd = false,
      strictAlarmsOnlyDnd = false,
      prioritizeAcademicNotifications = true,
    ),
) {
  fun configFor(mode: FocusMode): FocusModeConfig =
    when (mode) {
      FocusMode.DEEP_FOCUS -> deepFocusConfig
      FocusMode.STUDY_MODE -> studyModeConfig
      FocusMode.LIGHT_FOCUS -> lightFocusConfig
    }
}
