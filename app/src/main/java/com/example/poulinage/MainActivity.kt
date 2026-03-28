package com.example.poulinage

import android.Manifest
import android.app.ActivityManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    var phoneNumber by remember { mutableStateOf(prefs.getString(Constants.PREF_PHONE_NUMBER, "") ?: "") }
    var accelMagnitude by remember { mutableStateOf(0f) }
    var gyroMagnitude  by remember { mutableStateOf(0f) }

    val historyEvents = remember { mutableStateListOf<AlertHistory.Event>() }

    fun reloadHistory() {
        historyEvents.clear()
        historyEvents.addAll(AlertHistory.load(context))
    }

    fun saveConfig() {
        prefs.edit()
            .putString(Constants.PREF_PHONE_NUMBER, phoneNumber.trim())
            .apply()
    }

    // -------------------------------------------------------------------------
    // Chargement initial
    // -------------------------------------------------------------------------
    LaunchedEffect(Unit) {
        reloadHistory()
        status = if (isServiceRunning(context)) "Surveillance active" else "Inactif"
    }

    // -------------------------------------------------------------------------
    // Permission notification (runtime sur API 33+)
    // -------------------------------------------------------------------------
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

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
    // BroadcastReceiver — mises à jour du service
    // -------------------------------------------------------------------------
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Constants.ACTION_STATUS_UPDATE -> {
                        status = intent.getStringExtra(Constants.EXTRA_STATUS_MESSAGE) ?: status
                    }
                    Constants.ACTION_ALERT_FIRED -> {
                        reloadHistory()
                    }
                    Constants.ACTION_SENSOR_VALUES -> {
                        accelMagnitude = intent.getFloatExtra(Constants.EXTRA_ACCEL_MAGNITUDE, 0f)
                        gyroMagnitude  = intent.getFloatExtra(Constants.EXTRA_GYRO_MAGNITUDE, 0f)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Constants.ACTION_STATUS_UPDATE)
            addAction(Constants.ACTION_ALERT_FIRED)
            addAction(Constants.ACTION_SENSOR_VALUES)
        }
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

        // ---- Titre ----------------------------------------------------------
        Text(
            text = "Détection Poulinage",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(text = "Statut : $status", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Valeurs capteur en temps réel (visibles uniquement si le service est actif)
        if (accelMagnitude > 0f || gyroMagnitude > 0f) {
            Spacer(Modifier.height(6.dp))
            SensorGauge(
                label     = "Accéléromètre",
                value     = accelMagnitude,
                threshold = Constants.THRESHOLD_MS2,
                unit      = "m/s²"
            )
            Spacer(Modifier.height(4.dp))
            SensorGauge(
                label     = "Gyroscope",
                value     = gyroMagnitude,
                threshold = Constants.ROTATION_THRESHOLD_RADS,
                unit      = "rad/s"
            )
        }

        Spacer(Modifier.height(4.dp))

        // ---- Boutons Démarrer / Arrêter ------------------------------------
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        // ---- Configuration messagerie --------------------------------------
        Text(text = "Configuration messagerie", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Numéro de téléphone destinataire") },
            placeholder = { Text("ex: +33612345678") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { saveConfig(); status = "Configuration sauvegardée" },
                modifier = Modifier.weight(1f)
            ) { Text("Sauvegarder") }

            TextButton(
                onClick = {
                    saveConfig()
                    status = "Envoi SMS test..."
                    MessageSender.sendAlertSms(
                        context     = context,
                        timestampMs = System.currentTimeMillis(),
                        alertType   = Constants.ALERT_TYPE_MOVEMENT,
                        detail      = "Test manuel depuis l'app",
                        onResult    = { success, error ->
                            mainHandler.post {
                                status = if (success) "SMS test envoyé (simulation)"
                                         else "Erreur : $error"
                            }
                        }
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("Tester SMS") }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        // ---- Historique des alertes ----------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Historique (${historyEvents.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (historyEvents.isNotEmpty()) {
                TextButton(onClick = {
                    AlertHistory.clear(context)
                    reloadHistory()
                }) {
                    Text("Vider", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }

        if (historyEvents.isEmpty()) {
            Text(
                text = "Aucune alerte enregistrée.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            historyEvents.forEach { event ->
                AlertEventRow(event = event, dateFormat = dateFormat)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// -------------------------------------------------------------------------
// Vérifie si FoalingDetectionService est en cours d'exécution.
// -------------------------------------------------------------------------
@Suppress("DEPRECATION")
private fun isServiceRunning(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == FoalingDetectionService::class.java.name }
}

// -------------------------------------------------------------------------
// Composant : jauge capteur temps réel
// -------------------------------------------------------------------------

@Composable
private fun SensorGauge(
    label: String,
    value: Float,
    threshold: Float,
    unit: String
) {
    val ratio = (value / threshold).coerceIn(0f, 1f)
    val isOver = value >= threshold
    val barColor = when {
        ratio > 0.85f -> Color(0xFFD32F2F)
        ratio > 0.5f  -> Color(0xFFF57C00)
        else          -> Color(0xFF388E3C)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "%.2f / %.1f %s".format(value, threshold, unit),
                fontSize = 12.sp,
                fontWeight = if (isOver) FontWeight.Bold else FontWeight.Normal,
                color = if (isOver) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}

// -------------------------------------------------------------------------
// Composant : une ligne d'historique
// -------------------------------------------------------------------------

@Composable
private fun AlertEventRow(
    event: AlertHistory.Event,
    dateFormat: SimpleDateFormat
) {
    val isMovement = event.type == Constants.ALERT_TYPE_MOVEMENT
    val badgeColor = if (isMovement) Color(0xFF1565C0) else Color(0xFF6A1B9A)
    val badgeLabel = if (isMovement) "MOUVEMENT" else "ROTATION"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(badgeColor)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(text = badgeLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateFormat.format(Date(event.timestamp)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = event.detail,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
