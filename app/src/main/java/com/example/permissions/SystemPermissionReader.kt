package com.example.permissions

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.notification.FocusNotificationListenerService

data class SystemPermissionSnapshot(
  val notificationAccessGranted: Boolean = false,
  val notificationListenerBound: Boolean = false,
  val dndAccessGranted: Boolean = false,
)

class SystemPermissionReader(private val context: Context) {

  fun readSnapshot(): SystemPermissionSnapshot {
    val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
    val hasNotificationListener = enabledPackages.contains(context.packageName)
    val listenerBound = FocusNotificationListenerService.isListenerBound.value

    val notificationManager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    val hasDndAccess = notificationManager?.isNotificationPolicyAccessGranted == true

    return SystemPermissionSnapshot(
      notificationAccessGranted = hasNotificationListener,
      notificationListenerBound = listenerBound,
      dndAccessGranted = hasDndAccess,
    )
  }

  fun openNotificationListenerSettings(callerContext: Context) {
    val intent =
      Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { callerContext.startActivity(intent) }
  }

  fun openDndPolicySettings(callerContext: Context) {
    val intent =
      Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { callerContext.startActivity(intent) }
  }

  fun openAppNotificationSettings(callerContext: Context) {
    val intent =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
          putExtra(Settings.EXTRA_APP_PACKAGE, callerContext.packageName)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }
    runCatching { callerContext.startActivity(intent) }
  }
}
