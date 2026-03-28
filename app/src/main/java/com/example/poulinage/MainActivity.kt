package com.example.poulinage

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.poulinage.ui.theme.PoulinageTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoulinageTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PoulinageScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun PoulinageScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // -------------------------------------------------------------------------
    // État de l'UI
    // -------------------------------------------------------------------------
    var status by remember { mutableStateOf("Inactif") }
    var lastAlert by remember { mutableStateOf("Aucune alerte") }

    // Champs SMTP — initialisés depuis SharedPreferences
    var smtpHost     by remember { mutableStateOf(prefs.getString(Constants.PREF_SMTP_HOST, "smtp.gmail.com") ?: "smtp.gmail.com") }
    var smtpPort     by remember { mutableStateOf(prefs.getString(Constants.PREF_SMTP_PORT, "587") ?: "587") }
    var smtpUser     by remember { mutableStateOf(prefs.getString(Constants.PREF_SMTP_USER, "") ?: "") }
    var smtpPassword by remember { mutableStateOf(prefs.getString(Constants.PREF_SMTP_PASSWORD, "") ?: "") }
    var emailDest    by remember { mutableStateOf(prefs.getString(Constants.PREF_EMAIL_DEST, "") ?: "") }

    // -------------------------------------------------------------------------
    // Helper — sauvegarde dans SharedPreferences
    // -------------------------------------------------------------------------
    fun saveConfig() {
        prefs.edit()
            .putString(Constants.PREF_SMTP_HOST, smtpHost.trim())
            .putString(Constants.PREF_SMTP_PORT, smtpPort.trim())
            .putString(Constants.PREF_SMTP_USER, smtpUser.trim())
            .putString(Constants.PREF_SMTP_PASSWORD, smtpPassword)
            .putString(Constants.PREF_EMAIL_DEST, emailDest.trim())
            .apply()
    }

    // -------------------------------------------------------------------------
    // Chargement initial : heure de la dernière alerte
    // -------------------------------------------------------------------------
    LaunchedEffect(Unit) {
        val lastAlertMs = prefs.getLong(Constants.PREF_LAST_ALERT_TIME, 0L)
        if (lastAlertMs > 0) {
            lastAlert = dateFormat.format(Date(lastAlertMs))
        }
    }

    // -------------------------------------------------------------------------
    // Permission notification (runtime sur API 33+)
    // -------------------------------------------------------------------------
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* accordée ou refusée — la notification s'affiche si accordée */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // -------------------------------------------------------------------------
    // BroadcastReceiver — mises à jour du service foreground
    // -------------------------------------------------------------------------
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Constants.ACTION_STATUS_UPDATE -> {
                        status = intent.getStringExtra(Constants.EXTRA_STATUS_MESSAGE) ?: status
                    }
                    Constants.ACTION_ALERT_FIRED -> {
                        val ts = intent.getLongExtra(Constants.EXTRA_ALERT_TIMESTAMP, 0L)
                        if (ts > 0) lastAlert = dateFormat.format(Date(ts))
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Constants.ACTION_STATUS_UPDATE)
            addAction(Constants.ACTION_ALERT_FIRED)
        }
        // RECEIVER_NOT_EXPORTED : seule notre appli peut envoyer ces broadcasts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // -------------------------------------------------------------------------
    // Interface utilisateur
    // -------------------------------------------------------------------------
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Détection Poulinage",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(text = "Statut : $status", fontSize = 14.sp)
        Text(
            text = "Dernière alerte : $lastAlert",
            fontSize = 14.sp,
            color = if (lastAlert == "Aucune alerte") MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(4.dp))

        // Boutons Démarrer / Arrêter
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    saveConfig()
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, FoalingDetectionService::class.java)
                    )
                    status = "Démarrage..."
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                modifier = Modifier.weight(1f)
            ) { Text("Démarrer") }

            Button(
                onClick = {
                    context.stopService(Intent(context, FoalingDetectionService::class.java))
                    status = "Arrêté"
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                modifier = Modifier.weight(1f)
            ) { Text("Arrêter") }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Configuration SMTP",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = smtpHost,
            onValueChange = { smtpHost = it },
            label = { Text("Serveur SMTP (ex: smtp.gmail.com)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = smtpPort,
            onValueChange = { smtpPort = it },
            label = { Text("Port (ex: 587)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = smtpUser,
            onValueChange = { smtpUser = it },
            label = { Text("Email expéditeur") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = smtpPassword,
            onValueChange = { smtpPassword = it },
            label = { Text("Mot de passe (App Password Gmail)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = emailDest,
            onValueChange = { emailDest = it },
            label = { Text("Email destinataire") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
            onClick = { saveConfig(); status = "Configuration sauvegardée" },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Sauvegarder la configuration") }

        // Test email — le callback SMTP est sur un thread background,
        // on repasse sur le main thread via Handler avant de toucher l'état Compose
        TextButton(
            onClick = {
                saveConfig()
                status = "Envoi email test..."
                EmailSender.sendAlertAsync(
                    context = context,
                    timestampMs = System.currentTimeMillis(),
                    onResult = { success, error ->
                        mainHandler.post {
                            status = if (success) "Email test envoyé !"
                                     else "Erreur : $error"
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Tester l'envoi email") }

        Spacer(Modifier.height(16.dp))
    }
}
