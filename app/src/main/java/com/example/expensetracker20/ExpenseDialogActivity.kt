package com.example.expensetracker20

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.example.expensetracker20.ui.theme.ExpenseTrackerTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch

class ExpenseDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val account = GoogleSignIn.getLastSignedInAccount(this)

        setContent {
            val themeMode = remember { ExpenseRepository.getAppThemeMode(this) }
            ExpenseTrackerTheme(themeMode = themeMode) {
                ExpenseEntryDialog(
                    onDismiss = { finish() },
                    onSubmit = { amount, name, paymentMethod, onComplete ->
                        if (account != null) {
                            saveExpenseToSheets(account, amount, name, paymentMethod, onComplete)
                        } else {
                            Toast.makeText(this, "Please sign in with Google in the app first", Toast.LENGTH_LONG).show()
                            onComplete(false)
                        }
                    }
                )
            }
        }
    }

    private fun saveExpenseToSheets(
        account: GoogleSignInAccount,
        amount: String,
        name: String,
        paymentMethod: String,
        onComplete: (Boolean) -> Unit
    ) {
        lifecycleScope.launch {
            val sheetsManager = SheetsManager(this@ExpenseDialogActivity, account)
            val success = sheetsManager.appendExpense(amount, name, paymentMethod)

            if (success) {
                ExpenseRepository.saveExpense(
                    this@ExpenseDialogActivity,
                    name,
                    amount.toDoubleOrNull() ?: 0.0,
                    paymentMethod
                )
                Toast.makeText(this@ExpenseDialogActivity, "Saved ($paymentMethod)!", Toast.LENGTH_SHORT).show()
                onComplete(true)
                finish()
            } else {
                Toast.makeText(this@ExpenseDialogActivity, "Error saving to Google Sheet", Toast.LENGTH_LONG).show()
                onComplete(false)
            }
        }
    }
}

@Composable
fun ExpenseEntryDialog(
    onDismiss: () -> Unit,
    onSubmit: (amount: String, name: String, paymentMethod: String, onComplete: (Boolean) -> Unit) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val paymentOptions = listOf("UPI", "Cash", "Card")
    var selectedOption by remember { mutableStateOf(paymentOptions[0]) }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Quick Expense Entry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        if (it.isNotEmpty()) amountError = false
                    },
                    label = { Text("Amount (₹)") },
                    isError = amountError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotEmpty()) nameError = false
                    },
                    label = { Text("Expense Name (e.g. Coffee, Fuel)") },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Payment Method",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    paymentOptions.forEach { text ->
                        Row(
                            Modifier
                                .selectable(
                                    selected = (text == selectedOption),
                                    onClick = { selectedOption = text },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (text == selectedOption),
                                onClick = null
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        var hasError = false
                        if (amount.isBlank()) {
                            amountError = true
                            hasError = true
                        }
                        if (name.isBlank()) {
                            nameError = true
                            hasError = true
                        }

                        if (!hasError && !isSubmitting) {
                            isSubmitting = true
                            onSubmit(amount.trim(), name.trim(), selectedOption) { success ->
                                if (!success) isSubmitting = false
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSubmitting) "Saving..." else "Save to Google Sheets")
                }
            }
        }
    }
}
