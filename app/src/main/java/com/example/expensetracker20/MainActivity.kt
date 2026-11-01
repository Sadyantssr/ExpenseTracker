package com.example.expensetracker20

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.expensetracker20.ui.theme.ExpenseTrackerTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var refreshTrigger = mutableIntStateOf(0)
    private var isSignedIn = mutableStateOf(false)
    private var signedInAccount = mutableStateOf<GoogleSignInAccount?>(null)

    private var sheetsTodayTotal = mutableDoubleStateOf(0.0)
    private var sheetsMonthTotal = mutableDoubleStateOf(0.0)
    private var isLoadingTotals = mutableStateOf(false)

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                signedInAccount.value = account
                isSignedIn.value = true
                Toast.makeText(this, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()
                fetchSheetsTotals(account)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Sign-In Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startTrackingService()
        } else {
            Toast.makeText(this, "Notification permission required for shake tracking", Toast.LENGTH_SHORT).show()
            ExpenseRepository.setShakeTrackingEnabled(this, false)
            refreshTrigger.intValue++
        }
    }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "SMS Bank Tracker Activated!", Toast.LENGTH_SHORT).show()
            refreshTrigger.intValue++
        } else {
            Toast.makeText(this, "SMS Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if already signed in to Google
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            signedInAccount.value = account
            isSignedIn.value = true
            fetchSheetsTotals(account)
        }

        // Start background service on launch if shake tracking is enabled
        if (ExpenseRepository.isShakeTrackingEnabled(this)) {
            checkAndStartService()
        }

        setContent {
            val trigger = refreshTrigger.intValue
            val currentThemeMode = remember(trigger) { ExpenseRepository.getAppThemeMode(this) }

            ExpenseTrackerTheme(themeMode = currentThemeMode) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        refreshKey = trigger,
                        isSignedIn = isSignedIn.value,
                        accountEmail = signedInAccount.value?.email ?: "",
                        sheetsTodayTotal = sheetsTodayTotal.doubleValue,
                        sheetsMonthTotal = sheetsMonthTotal.doubleValue,
                        isLoadingTotals = isLoadingTotals.value,
                        modifier = Modifier.padding(innerPadding),
                        onGoogleSignInClick = { launchGoogleSignIn() },
                        onGoogleSignOutClick = { signOutGoogle() },
                        onRefreshTotalsClick = {
                            signedInAccount.value?.let { fetchSheetsTotals(it) }
                        },
                        onToggleShake = { enabled -> toggleShakeTracking(enabled) },
                        onRequestSmsPermission = {
                            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        },
                        onAddExpenseClick = {
                            startActivity(Intent(this, ExpenseDialogActivity::class.java))
                        },
                        onHistoryClick = {
                            startActivity(Intent(this, HistoryActivity::class.java))
                        },
                        onSettingsClick = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                        onExpenseUpdated = { refreshTrigger.intValue++ }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTrigger.intValue++
        signedInAccount.value?.let { fetchSheetsTotals(it) }
    }

    private fun launchGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(SheetsScopes.SPREADSHEETS),
                Scope("https://www.googleapis.com/auth/drive.file")
            )
            .build()

        val client = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    private fun signOutGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val client = GoogleSignIn.getClient(this, gso)
        client.signOut().addOnCompleteListener {
            signedInAccount.value = null
            isSignedIn.value = false
            sheetsTodayTotal.doubleValue = 0.0
            sheetsMonthTotal.doubleValue = 0.0
            Toast.makeText(this, "Signed Out", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchSheetsTotals(account: GoogleSignInAccount) {
        isLoadingTotals.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            val sheetsManager = SheetsManager(this@MainActivity, account)
            // Sync all online rows to local repository
            sheetsManager.syncAllRowsToLocal()
            val totals = sheetsManager.getTotals()
            launch(Dispatchers.Main) {
                sheetsTodayTotal.doubleValue = totals.first
                sheetsMonthTotal.doubleValue = totals.second
                isLoadingTotals.value = false
                refreshTrigger.intValue++
            }
        }
    }

    private fun toggleShakeTracking(enabled: Boolean) {
        ExpenseRepository.setShakeTrackingEnabled(this, enabled)
        if (enabled) {
            checkAndStartService()
        } else {
            stopTrackingService()
        }
    }

    private fun checkAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                startTrackingService()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startTrackingService()
        }
    }

    private fun startTrackingService() {
        val serviceIntent = Intent(this, ShakeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Shake tracker activated", Toast.LENGTH_SHORT).show()
    }

    private fun stopTrackingService() {
        val serviceIntent = Intent(this, ShakeService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, "Shake tracker disabled", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MainScreen(
    refreshKey: Int,
    isSignedIn: Boolean,
    accountEmail: String,
    sheetsTodayTotal: Double,
    sheetsMonthTotal: Double,
    isLoadingTotals: Boolean,
    modifier: Modifier = Modifier,
    onGoogleSignInClick: () -> Unit,
    onGoogleSignOutClick: () -> Unit,
    onRefreshTotalsClick: () -> Unit,
    onToggleShake: (Boolean) -> Unit,
    onRequestSmsPermission: () -> Unit,
    onAddExpenseClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onExpenseUpdated: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isShakeEnabled by remember(refreshKey) { mutableStateOf(ExpenseRepository.isShakeTrackingEnabled(context)) }
    val todayExpenses = remember(refreshKey) { ExpenseRepository.getTodayExpenses(context) }
    val localTodayTotal = remember(refreshKey) { ExpenseRepository.getTodayTotal(context) }

    val hasSmsPermission = remember(refreshKey) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    var isScanningSms by remember { mutableStateOf(false) }

    var editingItem by remember { mutableStateOf<ExpenseItem?>(null) }

    if (editingItem != null) {
        val targetItem = editingItem!!
        EditExpenseDialog(
            item = targetItem,
            onDismiss = { editingItem = null },
            onDelete = {
                val dateStr = ExpenseRepository.getDateStringFromTimestamp(targetItem.timestamp)
                ExpenseRepository.deleteExpense(context, dateStr, targetItem.id)

                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    coroutineScope.launch {
                        val sheetsManager = SheetsManager(context, account)
                        sheetsManager.deleteExpenseFromSheet(targetItem)
                    }
                }

                editingItem = null
                onExpenseUpdated()
                Toast.makeText(context, "Deleted transaction", Toast.LENGTH_SHORT).show()
            },
            onSave = { newName, newAmount, newPaymentMethod ->
                val dateStr = ExpenseRepository.getDateStringFromTimestamp(targetItem.timestamp)
                val updatedItem = targetItem.copy(
                    name = newName,
                    amount = newAmount,
                    paymentMethod = newPaymentMethod
                )
                ExpenseRepository.updateExpense(context, dateStr, updatedItem)

                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    coroutineScope.launch {
                        val sheetsManager = SheetsManager(context, account)
                        sheetsManager.updateExpenseInSheet(targetItem, updatedItem)
                    }
                }

                editingItem = null
                onExpenseUpdated()
                Toast.makeText(context, "Transaction updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    val canDrawOverlays = remember(refreshKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    val todayDateString = remember {
        SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header with Settings Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Expense Tracker 2.0",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = todayDateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Google Sign-In Card / Account Info
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSignedIn) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Account",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = accountEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onGoogleSignOutClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Sign Out", fontSize = 11.sp)
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect Google Sheets",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sign in to sync expenses with 'Daily Expenses' sheet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onGoogleSignInClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("Sign In", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SMS Tracker Permission & Scan Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Auto SMS Bank Tracker 💬",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = if (hasSmsPermission) "Listens for bank SMS & logs debit expenses." else "Enable SMS permission to auto-detect bank debit SMS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                if (!hasSmsPermission) {
                    Button(
                        onClick = onRequestSmsPermission,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("Enable", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            if (!isScanningSms) {
                                isScanningSms = true
                                coroutineScope.launch {
                                    val count = SmsScanner.scanInbox(context)
                                    isScanningSms = false
                                    Toast.makeText(context, "Scan Complete: $count new expense(s) found!", Toast.LENGTH_LONG).show()
                                    onExpenseUpdated()
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        if (isScanningSms) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("🔍 Scan Inbox", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Check Overlay Permission Card
        if (!canDrawOverlays) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Display Over Other Apps Needed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Grant this permission so the shake window can pop up on your homescreen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("Grant", fontSize = 12.sp)
                    }
                }
            }
        }

        // Shake Gesture On/Off Toggle Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Shake Gesture Detection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isShakeEnabled) "Active: Shake phone to log expense" else "Disabled: Turn on to enable shake prompt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isShakeEnabled,
                    onCheckedChange = { enabled ->
                        isShakeEnabled = enabled
                        onToggleShake(enabled)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Spending Summary Card (Google Sheets & Local Totals)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Spent Today",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    if (isSignedIn) {
                        IconButton(
                            onClick = onRefreshTotalsClick,
                            modifier = Modifier.height(28.dp)
                        ) {
                            if (isLoadingTotals) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Totals",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                val displayTodayTotal = if (isSignedIn && sheetsTodayTotal > 0.0) sheetsTodayTotal else localTodayTotal

                Text(
                    text = "₹ %.2f".format(displayTodayTotal),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                if (isSignedIn) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This Month Total: ₹ %.2f".format(sheetsMonthTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${todayExpenses.size} transaction${if (todayExpenses.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Button(
                        onClick = onAddExpenseClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("+ Add Expense")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Today's Transactions Header + History Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(
                onClick = onHistoryClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("📅 History")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Today's Transactions List
        if (todayExpenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No expenses recorded today.\nShake your phone or tap '+ Add Expense' to log one!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(todayExpenses) { item ->
                    ExpenseItemRow(
                        item = item,
                        onEditClick = { editingItem = item }
                    )
                }
            }
        }
    }
}

@Composable
fun ExpenseItemRow(
    item: ExpenseItem,
    onEditClick: (() -> Unit)? = null
) {
    val timeFormatted = remember(item.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.timestamp))
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onEditClick != null) Modifier.clickable { onEditClick() } else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = item.paymentMethod,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "₹ %.2f".format(item.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (onEditClick != null) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Transaction",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
