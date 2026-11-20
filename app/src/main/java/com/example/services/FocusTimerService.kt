package com.example.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.FocusFlowApplication
import com.example.MainActivity
import com.example.R
import com.example.model.FocusMode
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusTimerService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var tickJob: Job? = null

  private var targetEndElapsedMs: Long = 0L
  private var pausedRemainingSeconds: Int = 0
  private var totalSessionSeconds: Int = 25 * 60
  private var activeMode: FocusMode = FocusMode.STUDY_MODE
  private var activeDurationMinutes: Int = 25

  private val appContainer by lazy { (application as FocusFlowApplication).appContainer }
  private val timerRepository by lazy { appContainer.focusTimerRepository }

  override fun onCreate() {
    super.onCreate()
    ensureNotificationChannels()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START -> {
        val modeName = intent.getStringExtra(EXTRA_MODE_NAME) ?: FocusMode.STUDY_MODE.name
        activeMode = FocusMode.entries.firstOrNull { it.name == modeName } ?: FocusMode.STUDY_MODE
        activeDurationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 25).coerceIn(1, 180)
        totalSessionSeconds = activeDurationMinutes * 60
        pausedRemainingSeconds = totalSessionSeconds
        targetEndElapsedMs = SystemClock.elapsedRealtime() + totalSessionSeconds * 1000L

        timerRepository.onTimerStarted(activeMode, activeDurationMinutes)
        applyModeDndAsync(activeMode)
        startForegroundWithNotification(
          remainingSeconds = totalSessionSeconds,
          isPaused = false,
        )
        startTickLoop()
      }

      ACTION_PAUSE -> {
        tickJob?.cancel()
        val remainingMs = (targetEndElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        pausedRemainingSeconds = ((remainingMs + 999L) / 1000L).toInt()
        timerRepository.onTimerPaused(pausedRemainingSeconds)
        appContainer.dndPolicyManager.restorePreviousDndIfManaged()
        updateOngoingNotification(
          remainingSeconds = pausedRemainingSeconds,
          isPaused = true,
        )
      }

      ACTION_RESUME -> {
        val secondsToRun =
          if (pausedRemainingSeconds > 0) pausedRemainingSeconds
          else timerRepository.timerState.value.remainingSeconds
        targetEndElapsedMs = SystemClock.elapsedRealtime() + secondsToRun * 1000L
        timerRepository.onTimerResumed()
        applyModeDndAsync(activeMode)
        startForegroundWithNotification(
          remainingSeconds = secondsToRun,
          isPaused = false,
        )
        startTickLoop()
      }

      ACTION_RESET -> {
        tickJob?.cancel()
        val elapsedSeconds =
          (totalSessionSeconds - timerRepository.timerState.value.remainingSeconds)
            .coerceAtLeast(0)
        appContainer.dndPolicyManager.restorePreviousDndIfManaged()
        timerRepository.onTimerReset(
          mode = activeMode,
          durationMinutes = activeDurationMinutes,
          elapsedSeconds = elapsedSeconds,
        )
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
    }

    return START_NOT_STICKY
  }

  private fun applyModeDndAsync(mode: FocusMode) {
    serviceScope.launch {
      val prefs = appContainer.preferencesRepository.preferencesFlow.first()
      appContainer.dndPolicyManager.applyDndForSession(mode, prefs)
    }
  }

  private fun startTickLoop() {
    tickJob?.cancel()
    var lastDisplayedSecond = -1
    tickJob =
      serviceScope.launch {
        while (isActive) {
          val remainingMs = (targetEndElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
          val remainingSeconds =
            if (remainingMs == 0L) 0 else ((remainingMs + 999L) / 1000L).toInt()

          if (remainingSeconds != lastDisplayedSecond) {
            lastDisplayedSecond = remainingSeconds
            timerRepository.onTimerTick(remainingSeconds)
            updateOngoingNotification(
              remainingSeconds = remainingSeconds,
              isPaused = false,
            )
          }

          if (remainingMs <= 0L) {
            handleSessionCompleted()
            break
          }

          delay(250L)
        }
      }
  }

  private fun handleSessionCompleted() {
    appContainer.dndPolicyManager.restorePreviousDndIfManaged()
    timerRepository.onTimerCompleted()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    postCompletionNotification()
    stopSelf()
  }

  private fun startForegroundWithNotification(
    remainingSeconds: Int,
    isPaused: Boolean,
  ) {
    val notification = buildOngoingNotification(remainingSeconds, isPaused)
    val fgsType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
      } else {
        0
      }
    ServiceCompat.startForeground(this, ONGOING_NOTIFICATION_ID, notification, fgsType)
  }

  private fun updateOngoingNotification(
    remainingSeconds: Int,
    isPaused: Boolean,
  ) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    val notification = buildOngoingNotification(remainingSeconds, isPaused)
    NotificationManagerCompat.from(this).notify(ONGOING_NOTIFICATION_ID, notification)
  }

  private fun buildOngoingNotification(
    remainingSeconds: Int,
    isPaused: Boolean,
  ): Notification {
    val formattedTime = formatCountdown(remainingSeconds)
    val title =
      if (isPaused) "${activeMode.title} · Paused"
      else "${activeMode.title} · Focusing"
    val statusText =
      if (isPaused) "$formattedTime remaining · Tap Resume to continue"
      else "$formattedTime remaining"

    val openAppIntent =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val toggleAction =
      if (isPaused) {
        NotificationCompat.Action.Builder(
          0,
          "Resume",
          servicePendingIntent(ACTION_RESUME, 101),
        ).build()
      } else {
        NotificationCompat.Action.Builder(
          0,
          "Pause",
          servicePendingIntent(ACTION_PAUSE, 102),
        ).build()
      }

    val resetAction =
      NotificationCompat.Action.Builder(
        0,
        "Reset",
        servicePendingIntent(ACTION_RESET, 103),
      ).build()

    return NotificationCompat.Builder(this, CHANNEL_ONGOING_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText(statusText)
      .setContentIntent(openAppIntent)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .addAction(toggleAction)
      .addAction(resetAction)
      .build()
  }

  private fun postCompletionNotification() {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      return
    }

    val openAppIntent =
      PendingIntent.getActivity(
        this,
        2,
        Intent(this, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(this, CHANNEL_COMPLETE_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Focus session complete")
        .setContentText(
          "Completed ${activeDurationMinutes}m in ${activeMode.title}. Take a mindful break."
        )
        .setContentIntent(openAppIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()

    NotificationManagerCompat.from(this).notify(COMPLETION_NOTIFICATION_ID, notification)
  }

  private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
    val intent = Intent(this, FocusTimerService::class.java).apply { this.action = action }
    return PendingIntent.getService(
      this,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun ensureNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      val ongoingChannel =
        NotificationChannel(
          CHANNEL_ONGOING_ID,
          "Active Focus Timer",
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = "Shows the live countdown while a focus session is active."
          setShowBadge(false)
        }

      val completeChannel =
        NotificationChannel(
          CHANNEL_COMPLETE_ID,
          "Session Completion Alerts",
          NotificationManager.IMPORTANCE_HIGH,
        ).apply {
          description = "Notifies you when your scheduled focus session finishes."
        }

      manager.createNotificationChannel(ongoingChannel)
      manager.createNotificationChannel(completeChannel)
    }
  }

  override fun onDestroy() {
    tickJob?.cancel()
    appContainer.dndPolicyManager.restorePreviousDndIfManaged()
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val ACTION_START = "com.example.action.FOCUS_TIMER_START"
    const val ACTION_PAUSE = "com.example.action.FOCUS_TIMER_PAUSE"
    const val ACTION_RESUME = "com.example.action.FOCUS_TIMER_RESUME"
    const val ACTION_RESET = "com.example.action.FOCUS_TIMER_RESET"

    const val EXTRA_MODE_NAME = "extra_mode_name"
    const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"

    private const val CHANNEL_ONGOING_ID = "focus_timer_ongoing"
    private const val CHANNEL_COMPLETE_ID = "focus_timer_complete"
    private const val ONGOING_NOTIFICATION_ID = 1001
    private const val COMPLETION_NOTIFICATION_ID = 1002

    fun startSession(context: Context, mode: FocusMode, durationMinutes: Int) {
      val intent =
        Intent(context, FocusTimerService::class.java).apply {
          action = ACTION_START
          putExtra(EXTRA_MODE_NAME, mode.name)
          putExtra(EXTRA_DURATION_MINUTES, durationMinutes)
        }
      ContextCompat.startForegroundService(context, intent)
    }

    fun pauseSession(context: Context) {
      val intent = Intent(context, FocusTimerService::class.java).apply { action = ACTION_PAUSE }
      context.startService(intent)
    }

    fun resumeSession(context: Context) {
      val intent = Intent(context, FocusTimerService::class.java).apply { action = ACTION_RESUME }
      ContextCompat.startForegroundService(context, intent)
    }

    fun resetSession(context: Context) {
      val intent = Intent(context, FocusTimerService::class.java).apply { action = ACTION_RESET }
      context.startService(intent)
    }

    fun formatCountdown(totalSeconds: Int): String {
      val safeSeconds = totalSeconds.coerceAtLeast(0)
      val minutes = safeSeconds / 60
      val seconds = safeSeconds % 60
      return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
  }
}
