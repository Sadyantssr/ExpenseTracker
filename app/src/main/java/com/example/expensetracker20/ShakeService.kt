package com.example.expensetracker20

import android.R
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class ShakeService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f
    private var lastShakeTimestamp = 0L

    companion object {
        private const val CHANNEL_ID = "shake_tracker_channel"
        private const val ALERT_CHANNEL_ID = "shake_alert_channel"
        private const val NOTIFICATION_ID = 1001
        private const val SHAKE_ALERT_NOTIF_ID = 2002
        private const val SHAKE_COOLDOWN_MS = 1500L
        private const val SHAKE_THRESHOLD = 13f
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        acceleration = 10f
        currentAcceleration = SensorManager.GRAVITY_EARTH
        lastAcceleration = SensorManager.GRAVITY_EARTH

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        registerSensorListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        registerSensorListener()

        return START_STICKY
    }

    private fun registerSensorListener() {
        accelerometer?.let {
            sensorManager.unregisterListener(this)
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (ExpenseRepository.isShakeTrackingEnabled(this)) {
            val restartServiceIntent = Intent(applicationContext, ShakeService::class.java)
            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_ONE_SHOT
            }

            val restartServicePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    applicationContext,
                    1,
                    restartServiceIntent,
                    pendingFlags
                )
            } else {
                PendingIntent.getService(
                    applicationContext,
                    1,
                    restartServiceIntent,
                    pendingFlags
                )
            }

            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!ExpenseRepository.isShakeTrackingEnabled(this)) return
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = currentAcceleration - lastAcceleration
            acceleration = acceleration * 0.9f + delta

            val currentTime = System.currentTimeMillis()
            val threshold = ExpenseRepository.getShakeThreshold(this)
            if (acceleration > threshold && (currentTime - lastShakeTimestamp) > SHAKE_COOLDOWN_MS) {
                lastShakeTimestamp = currentTime
                openExpensePrompt()
            }
        }
    }

    private fun openExpensePrompt() {
        // Send high priority heads-up notification with FullScreenIntent so OS shows banner/popup on homescreen
        sendShakeAlertNotification()

        // Direct Activity Launch
        try {
            val intent = Intent(this, ExpenseDialogActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendShakeAlertNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Shake Expense Prompt",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows heads-up banner when phone is shaken"
            }
            notificationManager.createNotificationChannel(alertChannel)
        }

        val promptIntent = Intent(this, ExpenseDialogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, SHAKE_ALERT_NOTIF_ID, promptIntent, pendingFlags)

        val notif = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_alert)
            .setContentTitle("Shake Detected! 📱")
            .setContentText("Tap to record your quick expense")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(SHAKE_ALERT_NOTIF_ID, notif)
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Expense Tracker Active")
            .setContentText("Shake detection is running in the background.")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Expense Shake Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps shake detection alive"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }
}
