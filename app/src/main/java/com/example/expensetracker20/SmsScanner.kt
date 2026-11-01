package com.example.expensetracker20

import android.content.Context
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsScanner {

    suspend fun scanInbox(context: Context): Int = withContext(Dispatchers.IO) {
        var addedCount = 0
        try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("body", "date"),
                null,
                null,
                "date DESC"
            )

            cursor?.use {
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")

                val account = GoogleSignIn.getLastSignedInAccount(context)
                var sheetsManager: SheetsManager? = null
                if (account != null) {
                    sheetsManager = SheetsManager(context, account)
                }

                while (it.moveToNext()) {
                    val body = if (bodyIndex >= 0) it.getString(bodyIndex) ?: "" else continue
                    val dateLong = if (dateIndex >= 0) it.getLong(dateIndex) else System.currentTimeMillis()

                    val parsed = SmsParser.parseDebitSms(body) ?: continue
                    val dateStr = ExpenseRepository.getDateStringFromTimestamp(dateLong)

                    // Check if already logged
                    val existing = ExpenseRepository.getExpensesForDate(context, dateStr)
                    val isAlreadyLogged = existing.any { item ->
                        item.name == parsed.merchant && item.amount == parsed.amount
                    }

                    if (!isAlreadyLogged) {
                        ExpenseRepository.saveExpense(
                            context = context,
                            name = parsed.merchant,
                            amount = parsed.amount,
                            paymentMethod = parsed.paymentMethod,
                            customTimestamp = dateLong
                        )

                        sheetsManager?.appendExpense(
                            amount = parsed.amount.toString(),
                            name = parsed.merchant,
                            paymentMethod = parsed.paymentMethod
                        )

                        addedCount++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        addedCount
    }
}
