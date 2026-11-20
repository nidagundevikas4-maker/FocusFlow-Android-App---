package com.example.di

import android.content.Context
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.rules.InstalledAppsRepository
import com.example.data.rules.NotificationRuleRepository
import com.example.data.session.SessionRepository
import com.example.data.timer.FocusTimerRepository
import com.example.database.FocusFlowDatabase
import com.example.notification.DndPolicyManager
import com.example.permissions.SystemPermissionReader

class AppContainer(context: Context) {
  private val appContext = context.applicationContext

  val database: FocusFlowDatabase by lazy {
    FocusFlowDatabase.getInstance(appContext)
  }

  val preferencesRepository: FocusPreferencesRepository by lazy {
    FocusPreferencesRepository(appContext)
  }

  val permissionReader: SystemPermissionReader by lazy {
    SystemPermissionReader(appContext)
  }

  val dndPolicyManager: DndPolicyManager by lazy {
    DndPolicyManager(appContext)
  }

  val installedAppsRepository: InstalledAppsRepository by lazy {
    InstalledAppsRepository(appContext)
  }

  val sessionRepository: SessionRepository by lazy {
    SessionRepository(
      sessionDao = database.focusSessionDao(),
      distractionEventDao = database.distractionEventDao(),
    )
  }

  val notificationRuleRepository: NotificationRuleRepository by lazy {
    NotificationRuleRepository(
      ruleDao = database.appNotificationRuleDao(),
      distractionEventDao = database.distractionEventDao(),
    )
  }

  val focusTimerRepository: FocusTimerRepository by lazy {
    FocusTimerRepository(sessionRepository)
  }
}
