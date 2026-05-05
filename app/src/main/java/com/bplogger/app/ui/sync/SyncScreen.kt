package com.bplogger.app.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bplogger.app.ViewModelFactory
import com.bplogger.app.ui.theme.BPGreen
import com.bplogger.app.ui.theme.BPRed
import com.bplogger.app.ui.theme.BPYellow
import com.bplogger.app.ui.theme.accentColor
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SyncScreen(factory: ViewModelFactory) {
    val viewModel: SyncViewModel = viewModel(factory = factory)
    val settings by viewModel.settings.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val googleAccount by viewModel.googleAccount.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.checkSignedInAccount(context)
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) {
                viewModel.onSignInSuccess(account)
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val msg = when (e.statusCode) {
                7 -> "Network Error. Please check your internet connection."
                10 -> "Developer Error. Please check SHA-1 and package name in Google Cloud Console."
                12500 -> "Sign-in failed (12500). If you are a new tester, please contact the developer to be added to the authorized list."
                12501 -> "Sign-in cancelled by user."
                else -> "Sign-in failed (code: ${e.statusCode})."
            }
            android.util.Log.e("SyncScreen", msg)
            viewModel.onSignInError(msg)
        } catch (e: Exception) {
            android.util.Log.e("SyncScreen", "Sign-in failed with error: ${e.message}")
            viewModel.onSignInError("Sign-in failed: ${e.message}")
        }
    }

    val lastSyncTs = settings?.lastSyncedAt ?: 0L
    val lastSyncText = if (lastSyncTs == 0L) "Never" else
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(lastSyncTs))

    val accent = MaterialTheme.accentColor

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Google Sheets Sync",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = accent
        )

        // Account card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Google Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                
                if (googleAccount == null) {
                    Text("Not signed in. Sign in to sync your data with Google Sheets.")
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val signInIntent = viewModel.getGoogleSignInClient(context).signInIntent
                            signInLauncher.launch(signInIntent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign in with Google")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(googleAccount?.displayName ?: "Google User", fontWeight = FontWeight.Bold)
                            Text(googleAccount?.email ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.signOut(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign Out")
                    }
                }
            }
        }

        // Sync actions
        if (googleAccount != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Last synced: $lastSyncText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::manualSync,
                        enabled = syncStatus !is SyncStatus.Syncing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        if (syncStatus is SyncStatus.Syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Sync, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Sync Now")
                    }
                    
                    OutlinedButton(
                        onClick = viewModel::restoreFromSheet,
                        enabled = syncStatus !is SyncStatus.Syncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Restore from Cloud")
                    }
                }
            }
        }


        // Status message
        when (val status = syncStatus) {
            is SyncStatus.Success -> {
                Card(colors = CardDefaults.cardColors(containerColor = BPGreen.copy(alpha = 0.1f))) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = BPGreen)
                        Spacer(Modifier.width(8.dp))
                        Text(status.message, color = BPGreen)
                    }
                }
            }
            is SyncStatus.Error -> {
                Card(colors = CardDefaults.cardColors(containerColor = BPRed.copy(alpha = 0.1f))) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, null, tint = BPRed)
                        Spacer(Modifier.width(8.dp))
                        Text(status.message, color = BPRed)
                    }
                }
            }
            is SyncStatus.Syncing -> {
                Card(colors = CardDefaults.cardColors(containerColor = BPYellow.copy(alpha = 0.1f))) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.HourglassEmpty, null, tint = BPYellow)
                        Spacer(Modifier.width(8.dp))
                        Text(status.message, color = BPYellow)
                    }
                }
            }
            else -> {}
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = BPYellow.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "How Sync Works",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = BPYellow
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your data is synced directly to a private Google Sheet named 'BPLogger_Data' in your Google Drive. " +
                            "This avoids using any third-party scripts and keeps your data entirely within your Google account.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BPYellow.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Authorized Testers",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = BPYellow
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "To use the Google Sync function, your email must be added to the developer's authorized list.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Send your email to the developer:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "WhatsApp: 0572742826\nEmail: micorouell@yahoo.com",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}