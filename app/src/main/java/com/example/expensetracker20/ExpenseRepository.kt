package com.example.expensetracker20

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExpenseItem(
    val id: String,
    val name: String,
    val amount: Double,
    val paymentMethod: String,
    val timestamp: Long
)

enum class AppThemeMode(val label: String) {
    LIGHT("Light Mode"),
    DARK("Dark Mode"),
    AMOLED("AMOLED Black Mode")
}

object ExpenseRepository {

    private const val PREFS_NAME = "expense_tracker_prefs"
    private const val KEY_SHAKE_ENABLED = "shake_tracking_enabled"
    private const val KEY_SHAKE_THRESHOLD = "shake_threshold_float"
    private const val KEY_THEME = "app_theme_mode"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isShakeTrackingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHAKE_ENABLED, true)
    }

    fun setShakeTrackingEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHAKE_ENABLED, enabled).apply()
    }

    fun getShakeThreshold(context: Context): Float {
        return getPrefs(context).getFloat(KEY_SHAKE_THRESHOLD, 13f)
    }

    fun setShakeThreshold(context: Context, threshold: Float) {
        getPrefs(context).edit().putFloat(KEY_SHAKE_THRESHOLD, threshold).apply()
    }

    fun getAppThemeMode(context: Context): AppThemeMode {
        val name = getPrefs(context).getString(KEY_THEME, AppThemeMode.LIGHT.name)
        return try {
            AppThemeMode.valueOf(name ?: AppThemeMode.LIGHT.name)
        } catch (e: Exception) {
            AppThemeMode.LIGHT
        }
    }

    fun setAppThemeMode(context: Context, themeMode: AppThemeMode) {
        getPrefs(context).edit().putString(KEY_THEME, themeMode.name).apply()
    }

    fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun getDateStringFromTimestamp(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }

    private fun getDateKey(dateStr: String): String {
        return "expenses_$dateStr"
    }

    fun saveExpense(context: Context, name: String, amount: Double, paymentMethod: String, customTimestamp: Long? = null): ExpenseItem {
        val prefs = getPrefs(context)
        val timestamp = customTimestamp ?: System.currentTimeMillis()
        val dateStr = getDateStringFromTimestamp(timestamp)
        val dateKey = getDateKey(dateStr)
        val jsonArrayString = prefs.getString(dateKey, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonArrayString)

        val id = timestamp.toString()
        val newExpense = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("amount", amount)
            put("paymentMethod", paymentMethod)
            put("timestamp", timestamp)
        }

        jsonArray.put(newExpense)
        prefs.edit().putString(dateKey, jsonArray.toString()).apply()

        return ExpenseItem(
            id = id,
            name = name,
            amount = amount,
            paymentMethod = paymentMethod,
            timestamp = timestamp
        )
    }

    fun updateExpense(context: Context, dateStr: String, updatedItem: ExpenseItem) {
        val prefs = getPrefs(context)
        val dateKey = getDateKey(dateStr)
        val jsonArrayString = prefs.getString(dateKey, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonArrayString)

        val newArray = JSONArray()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val id = obj.optString("id", i.toString())
            if (id == updatedItem.id) {
                val updatedObj = JSONObject().apply {
                    put("id", updatedItem.id)
                    put("name", updatedItem.name)
                    put("amount", updatedItem.amount)
                    put("paymentMethod", updatedItem.paymentMethod)
                    put("timestamp", updatedItem.timestamp)
                }
                newArray.put(updatedObj)
            } else {
                newArray.put(obj)
            }
        }
        prefs.edit().putString(dateKey, newArray.toString()).apply()
    }

    fun deleteExpense(context: Context, dateStr: String, expenseId: String) {
        val prefs = getPrefs(context)
        val dateKey = getDateKey(dateStr)
        val jsonArrayString = prefs.getString(dateKey, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonArrayString)

        val newArray = JSONArray()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val id = obj.optString("id", i.toString())
            if (id != expenseId) {
                newArray.put(obj)
            }
        }
        prefs.edit().putString(dateKey, newArray.toString()).apply()
    }

    fun getExpensesForDate(context: Context, dateStr: String): List<ExpenseItem> {
        val prefs = getPrefs(context)
        val dateKey = getDateKey(dateStr)
        val jsonArrayString = prefs.getString(dateKey, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonArrayString)

        val list = mutableListOf<ExpenseItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                ExpenseItem(
                    id = obj.optString("id", i.toString()),
                    name = obj.optString("name", "Expense"),
                    amount = obj.optDouble("amount", 0.0),
                    paymentMethod = obj.optString("paymentMethod", "Cash"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
        return list.reversed() // Most recent first
    }

    fun getTotalForDate(context: Context, dateStr: String): Double {
        return getExpensesForDate(context, dateStr).sumOf { it.amount }
    }

    fun getTodayExpenses(context: Context): List<ExpenseItem> {
        return getExpensesForDate(context, getTodayDateString())
    }

    fun getTodayTotal(context: Context): Double {
        return getTotalForDate(context, getTodayDateString())
    }
}
