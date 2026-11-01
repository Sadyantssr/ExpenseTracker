package com.example.expensetracker20

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.messageBody ?: continue
                val parsed = SmsParser.parseDebitSms(body) ?: continue

                // Save locally
                ExpenseRepository.saveExpense(
                    context = context,
                    name = parsed.merchant,
                    amount = parsed.amount,
                    paymentMethod = parsed.paymentMethod
                )

                // Sync to Google Sheets if signed in
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val sheetsManager = SheetsManager(context, account)
                        sheetsManager.appendExpense(
                            amount = parsed.amount.toString(),
                            name = parsed.merchant,
                            paymentMethod = parsed.paymentMethod
                        )
                    }
                }

                // Show notification & Toast
                val notifMessage = "Auto-Logged: ₹%.2f at %s (%s)".format(
                    parsed.amount,
                    parsed.merchant,
                    parsed.paymentMethod
                )
                Toast.makeText(context, notifMessage, Toast.LENGTH_LONG).show()
                showSmsNotification(context, notifMessage)
            }
        }
    }

    private fun showSmsNotification(context: Context, messageText: String) {
        val channelId = "sms_tracker_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Bank SMS Tracker",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when a bank debit SMS is auto-logged"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle("Bank Expense Auto-Logged! 💳")
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notif)
    }
}
