package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.timer.FocusTimerRepository
import com.example.model.FocusMode
import com.example.model.FocusTimerState
import com.example.services.FocusTimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusTimerUiState(
  val timerState: FocusTimerState = FocusTimerState(),
  val selectedMode: FocusMode = FocusMode.STUDY_MODE,
  val configuredDurationMinutes: Int = 25,
  val isCustomDurationDialogOpen: Boolean = false,
  val showNotificationDeniedBanner: Boolean = false,
)

class FocusTimerViewModel(
  private val appContext: Context,
  private val preferencesRepository: FocusPreferencesRepository,
  private val timerRepository: FocusTimerRepository,
) : ViewModel() {

  private val isCustomDialogOpen = MutableStateFlow(false)
  private val notificationDeniedBanner = MutableStateFlow(false)

  val uiState: StateFlow<FocusTimerUiState> =
    combine(
      timerRepository.timerState,
      preferencesRepository.preferencesFlow,
      isCustomDialogOpen,
      notificationDeniedBanner,
    ) { timer, prefs, dialogOpen, deniedBanner ->
      FocusTimerUiState(
        timerState = timer,
        selectedMode = prefs.activeFocusMode,
        configuredDurationMinutes = prefs.selectedDurationMinutes,
        isCustomDurationDialogOpen = dialogOpen,
        showNotificationDeniedBanner = deniedBanner,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = FocusTimerUiState(),
    )

  init {
    viewModelScope.launch {
      preferencesRepository.preferencesFlow.collect { prefs ->
        timerRepository.syncIdleConfig(
          mode = prefs.activeFocusMode,
          durationMinutes = prefs.selectedDurationMinutes,
        )
      }
    }
  }

  fun selectDurationMinutes(minutes: Int) {
    viewModelScope.launch {
      preferencesRepository.setSelectedDurationMinutes(minutes)
      isCustomDialogOpen.update { false }
    }
  }

  fun openCustomDurationDialog() {
    isCustomDialogOpen.update { true }
  }

  fun dismissCustomDurationDialog() {
    isCustomDialogOpen.update { false }
  }

  fun onNotificationPermissionResult(granted: Boolean) {
    notificationDeniedBanner.update { !granted }
  }

  fun startFocusSession() {
    val current = uiState.value
    FocusTimerService.startSession(
      context = appContext,
      mode = current.selectedMode,
      durationMinutes = current.configuredDurationMinutes,
    )
  }

  fun pauseFocusSession() {
    FocusTimerService.pauseSession(appContext)
  }

  fun resumeFocusSession() {
    FocusTimerService.resumeSession(appContext)
  }

  fun resetFocusSession() {
    FocusTimerService.resetSession(appContext)
  }

  fun dismissCompletionState() {
    val current = uiState.value
    timerRepository.acknowledgeCompletion(
      mode = current.selectedMode,
      durationMinutes = current.configuredDurationMinutes,
    )
  }

  class Factory(
    private val appContext: Context,
    private val preferencesRepository: FocusPreferencesRepository,
    private val timerRepository: FocusTimerRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(FocusTimerViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      return FocusTimerViewModel(appContext, preferencesRepository, timerRepository) as T
    }
  }
}
