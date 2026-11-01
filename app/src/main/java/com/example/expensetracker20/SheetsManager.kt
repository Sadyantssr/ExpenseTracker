package com.example.expensetracker20

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ClearValuesRequest
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SheetsManager(private val context: Context, private val account: GoogleSignInAccount) {

    private val prefs: SharedPreferences = context.getSharedPreferences("expense_prefs", Context.MODE_PRIVATE)

    private val credential = GoogleAccountCredential.usingOAuth2(
        context,
        listOf(SheetsScopes.SPREADSHEETS, "https://www.googleapis.com/auth/drive.file")
    ).apply {
        selectedAccount = account.account
    }

    private val sheetsService: Sheets = Sheets.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance(),
        credential
    ).setApplicationName("ExpenseTracker").build()

    private val driveService: Drive = Drive.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance(),
        credential
    ).setApplicationName("ExpenseTracker").build()

    suspend fun getOrCreateSpreadsheetId(): String = withContext(Dispatchers.IO) {
        val cachedId = prefs.getString("spreadsheet_id", null)
        if (!cachedId.isNullOrEmpty()) return@withContext cachedId

        val result = driveService.files().list()
            .setQ("name = 'Daily Expenses' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false")
            .setFields("files(id, name)")
            .execute()

        val existingFile = result.files.firstOrNull()
        if (existingFile != null) {
            prefs.edit().putString("spreadsheet_id", existingFile.id).apply()
            return@withContext existingFile.id
        }

        val fileMetadata = File().apply {
            name = "Daily Expenses"
            mimeType = "application/vnd.google-apps.spreadsheet"
        }
        val createdFile = driveService.files().create(fileMetadata).setFields("id").execute()
        val newId = createdFile.id

        val headerValues = listOf(listOf("Date", "Amount", "Expense Name", "Payment Method"))
        val body = ValueRange().setValues(headerValues)
        sheetsService.spreadsheets().values()
            .append(newId, "A1", body)
            .setValueInputOption("USER_ENTERED")
            .execute()

        prefs.edit().putString("spreadsheet_id", newId).apply()
        return@withContext newId
    }

    suspend fun appendExpense(amount: String, name: String, paymentMethod: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val spreadsheetId = getOrCreateSpreadsheetId()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())

            val row = listOf(listOf(timestamp, amount.toDoubleOrNull() ?: amount, name, paymentMethod))
            val body = ValueRange().setValues(row)

            sheetsService.spreadsheets().values()
                .append(spreadsheetId, "A:D", body)
                .setValueInputOption("USER_ENTERED")
                .execute()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateExpenseInSheet(oldExpense: ExpenseItem, updatedExpense: ExpenseItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val spreadsheetId = getOrCreateSpreadsheetId()
            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "A2:D")
                .execute()

            val rows = response.getValues() ?: return@withContext false
            val oldDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(oldExpense.timestamp))

            var rowIndexToUpdate = -1
            for (i in rows.indices) {
                val row = rows[i]
                if (row.size >= 3) {
                    val rowDateStr = row[0]?.toString() ?: ""
                    val rowName = row[2]?.toString() ?: ""
                    if (rowName == oldExpense.name && rowDateStr.startsWith(oldDateStr)) {
                        rowIndexToUpdate = i + 2 // Sheet 1-based index starting from row 2
                        break
                    }
                }
            }

            if (rowIndexToUpdate != -1) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val timestampStr = dateFormat.format(Date(updatedExpense.timestamp))
                val updatedRow = listOf(listOf(timestampStr, updatedExpense.amount, updatedExpense.name, updatedExpense.paymentMethod))
                val body = ValueRange().setValues(updatedRow)

                sheetsService.spreadsheets().values()
                    .update(spreadsheetId, "A$rowIndexToUpdate:D$rowIndexToUpdate", body)
                    .setValueInputOption("USER_ENTERED")
                    .execute()
                true
            } else {
                appendExpense(updatedExpense.amount.toString(), updatedExpense.name, updatedExpense.paymentMethod)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteExpenseFromSheet(expense: ExpenseItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val spreadsheetId = getOrCreateSpreadsheetId()
            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "A2:D")
                .execute()

            val rows = response.getValues() ?: return@withContext false
            val oldDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(expense.timestamp))

            var rowIndexToClear = -1
            for (i in rows.indices) {
                val row = rows[i]
                if (row.size >= 3) {
                    val rowDateStr = row[0]?.toString() ?: ""
                    val rowName = row[2]?.toString() ?: ""
                    if (rowName == expense.name && rowDateStr.startsWith(oldDateStr)) {
                        rowIndexToClear = i + 2 // Sheet 1-based index starting from row 2
                        break
                    }
                }
            }

            if (rowIndexToClear != -1) {
                sheetsService.spreadsheets().values()
                    .clear(spreadsheetId, "A$rowIndexToClear:D$rowIndexToClear", ClearValuesRequest())
                    .execute()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun syncAllRowsToLocal(): Boolean = withContext(Dispatchers.IO) {
        try {
            val spreadsheetId = getOrCreateSpreadsheetId()
            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "A2:D")
                .execute()

            val rows = response.getValues() ?: return@withContext true
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val altDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            for (row in rows) {
                if (row.size < 3) continue
                val rawDate = row[0]?.toString() ?: ""
                val amount = row[1]?.toString()?.toDoubleOrNull() ?: 0.0
                val name = row[2]?.toString() ?: "Expense"
                val paymentMethod = if (row.size >= 4) row[3]?.toString() ?: "UPI" else "UPI"

                val parsedTime: Long = try {
                    dateFormat.parse(rawDate)?.time ?: altDateFormat.parse(rawDate)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                val dateStr = ExpenseRepository.getDateStringFromTimestamp(parsedTime)
                val existingList = ExpenseRepository.getExpensesForDate(context, dateStr)
                val exists = existingList.any { it.name == name && it.amount == amount }

                if (!exists) {
                    ExpenseRepository.saveExpense(context, name, amount, paymentMethod, customTimestamp = parsedTime)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getTotals(): Pair<Double, Double> = withContext(Dispatchers.IO) {
        try {
            val spreadsheetId = getOrCreateSpreadsheetId()
            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "A2:B")
                .execute()

            val rows = response.getValues() ?: return@withContext Pair(0.0, 0.0)
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val monthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

            var todaySum = 0.0
            var monthSum = 0.0

            for (row in rows) {
                if (row.size < 2) continue
                val rowDate = row[0]?.toString() ?: ""
                val amount = row[1]?.toString()?.toDoubleOrNull() ?: 0.0

                if (rowDate.startsWith(monthStr)) {
                    monthSum += amount
                    if (rowDate.startsWith(todayStr)) {
                        todaySum += amount
                    }
                }
            }
            Pair(todaySum, monthSum)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(0.0, 0.0)
        }
    }
}
