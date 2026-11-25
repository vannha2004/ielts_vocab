package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.IeltsDatabase
import com.example.domain.SystemAppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StudyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = IeltsDatabase.getInstance(context)
                val dao = db.ieltsDao()
                val settings = dao.getUserSettings()
                if (settings != null && settings.reminderEnabled) {
                    val clock = SystemAppClock(settings.simulatedDayOffset)
                    val dueList = dao.getDueProgressList(clock.currentLocalEpochDay())
                    showReminderNotification(
                        context = context,
                        dueCount = dueList.size,
                        dailyGoal = settings.dailyGoal
                    )
                    StudyReminderScheduler.scheduleDailyReminder(
                        context = context,
                        enabled = true,
                        hour = settings.reminderHour,
                        minute = settings.reminderMinute
                    )
                }
            } catch (_: Exception) {
                // Gracefully ignore notification errors under system restrictions
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "ielts_vocab_study_reminders"
        const val NOTIFICATION_ID = 2026

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = context.getString(R.string.notification_channel_name)
                val desc = context.getString(R.string.notification_channel_desc)
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    name,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = desc
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.createNotificationChannel(channel)
            }
        }

        fun showReminderNotification(
            context: Context,
            dueCount: Int,
            dailyGoal: Int
        ): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return false
            }

            ensureChannel(context)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (dueCount > 0) {
                "Bạn có $dueCount từ IELTS đến hạn ôn tập!"
            } else {
                "Đến giờ chinh phục mục tiêu $dailyGoal từ IELTS hôm nay"
            }

            val body = if (dueCount > 0) {
                "Hoàn thành bài kiểm tra ôn tập bắt buộc ($dueCount từ) để củng cố trí nhớ dài hạn và mở khóa bài học mới."
            } else {
                "Duy trì chuỗi học từ vựng học thuật mỗi ngày giúp bạn vững vàng đạt Band 7.0+."
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_vocab)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            return try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
                true
            } catch (_: SecurityException) {
                false
            }
        }
    }
}
