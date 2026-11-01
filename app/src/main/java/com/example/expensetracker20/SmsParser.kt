package com.example.expensetracker20

import java.util.regex.Pattern

data class ParsedSmsExpense(
    val amount: Double,
    val merchant: String,
    val paymentMethod: String
)

object SmsParser {

    fun parseDebitSms(messageBody: String): ParsedSmsExpense? {
        val lower = messageBody.lowercase()

        // Ignore credit/deposit SMS
        if (lower.contains("credited") || lower.contains("received") || lower.contains("refund")) {
            return null
        }

        // Check if it's a debit SMS
        val isDebit = lower.contains("debited") || lower.contains("debit") ||
                lower.contains("spent") || lower.contains("paid") ||
                lower.contains("sent") || lower.contains("transferred") ||
                lower.contains("txn") || lower.contains("vpa") || lower.contains("dbt")

        if (!isDebit) return null

        // 1. Extract Amount
        val amountPattern = Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)")
        val amountMatcher = amountPattern.matcher(messageBody)
        var amount = 0.0

        if (amountMatcher.find()) {
            val amountStr = amountMatcher.group(1)?.replace(",", "") ?: ""
            amount = amountStr.toDoubleOrNull() ?: 0.0
        }

        if (amount <= 0.0) return null

        // 2. Extract Merchant
        var merchant = "Bank Expense"
        val merchantPattern = Pattern.compile("(?i)(?:at|to|vpa|for|info)\\s+([A-Za-z0-9\\.\\_\\-\\s]{2,20})")
        val merchantMatcher = merchantPattern.matcher(messageBody)

        if (merchantMatcher.find()) {
            val rawName = merchantMatcher.group(1)?.trim() ?: ""
            if (rawName.isNotBlank() && !rawName.lowercase().contains("ref") && !rawName.lowercase().contains("bal")) {
                merchant = rawName
            }
        }

        // 3. Infer Payment Method
        val paymentMethod = when {
            lower.contains("upi") || lower.contains("vpa") || lower.contains("gpay") || lower.contains("phonepe") || lower.contains("paytm") -> "UPI"
            lower.contains("card") || lower.contains("debit card") || lower.contains("credit card") -> "Card"
            else -> "Cash"
        }

        return ParsedSmsExpense(
            amount = amount,
            merchant = merchant,
            paymentMethod = paymentMethod
        )
    }
}
