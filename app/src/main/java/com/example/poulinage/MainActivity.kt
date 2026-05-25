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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class Screen { Home, History, Settings }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoulinageTheme {
                PoulinageScreen()
            }
        }
    }
}

@Composable
fun PoulinageScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var status by remember { mutableStateOf("Inactif") }
    var phoneNumber by remember { mutableStateOf(prefs.getString(Constants.PREF_PHONE_NUMBER, "") ?: "") }
    var accelMagnitude by remember { mutableStateOf(0f) }
    var gyroMagnitude by remember { mutableStateOf(0f) }

    var accelThreshold by remember { mutableStateOf(prefs.getFloat(Constants.PREF_ACCEL_THRESHOLD, Constants.DEFAULT_ACCEL_THRESHOLD)) }
    var rotationThreshold by remember { mutableStateOf(prefs.getFloat(Constants.PREF_ROTATION_THRESHOLD, Constants.DEFAULT_ROTATION_THRESHOLD)) }
    var minCrossings by remember { mutableStateOf(prefs.getInt(Constants.PREF_MIN_CROSSINGS, Constants.DEFAULT_MIN_CROSSINGS)) }
    var minRotationSamples by remember { mutableStateOf(prefs.getInt(Constants.PREF_MIN_ROTATION_SAMPLES, Constants.DEFAULT_MIN_ROTATION_SAMPLES)) }
    var windowSize by remember { mutableStateOf(prefs.getLong(Constants.PREF_WINDOW_SIZE_MS, Constants.DEFAULT_WINDOW_SIZE_MS) / 1000L) }

    val historyEvents = remember { mutableStateListOf<AlertHistory.Event>() }

    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun reloadHistory() {
        historyEvents.clear()
        historyEvents.addAll(AlertHistory.load(context))
    }

    fun saveConfig() {
        prefs.edit().putString(Constants.PREF_PHONE_NUMBER, phoneNumber.trim()).apply()
    }

    fun saveAdvancedSettings() {
        prefs.edit()
            .putFloat(Constants.PREF_ACCEL_THRESHOLD, accelThreshold)
            .putFloat(Constants.PREF_ROTATION_THRESHOLD, rotationThreshold)
            .putInt(Constants.PREF_MIN_CROSSINGS, minCrossings)
            .putInt(Constants.PREF_MIN_ROTATION_SAMPLES, minRotationSamples)
            .putLong(Constants.PREF_WINDOW_SIZE_MS, windowSize * 1000L)
            .apply()
        AppLogger.i(Constants.TAG, "Advanced settings sauvegardés")
    }

    LaunchedEffect(Unit) {
        reloadHistory()
        status = if (isServiceRunning(context)) "Surveillance active" else "Inactif"
    }

    val multiPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (perm, granted) ->
            if (!granted) AppLogger.w(Constants.TAG, "Permission refusée : $perm")
        }
    }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) multiPermLauncher.launch(needed.toTypedArray())
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Constants.ACTION_STATUS_UPDATE ->
                        status = intent.getStringExtra(Constants.EXTRA_STATUS_MESSAGE) ?: status
                    Constants.ACTION_ALERT_FIRED -> reloadHistory()
                    Constants.ACTION_SENSOR_VALUES -> {
                        accelMagnitude = intent.getFloatExtra(Constants.EXTRA_ACCEL_MAGNITUDE, 0f)
                        gyroMagnitude = intent.getFloatExtra(Constants.EXTRA_GYRO_MAGNITUDE, 0f)
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Accueil") },
                    label = { Text("Accueil") },
                    selected = currentScreen == Screen.Home,
                    onClick = { currentScreen = Screen.Home }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.History, contentDescription = "Historique") },
                    label = { Text("Historique") },
                    selected = currentScreen == Screen.History,
                    onClick = { currentScreen = Screen.History }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Paramètres") },
                    label = { Text("Paramètres") },
                    selected = currentScreen == Screen.Settings,
                    onClick = { currentScreen = Screen.Settings }
                )
            }
        }
    ) { innerPadding ->

        when (currentScreen) {

            // =================================================================
            // ACCUEIL
            // =================================================================
            Screen.Home -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Détection Poulinage",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = "Statut : $status",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (accelMagnitude > 0f || gyroMagnitude > 0f) {
                    Spacer(Modifier.height(6.dp))
                    SensorGauge("Accéléromètre", accelMagnitude, accelThreshold, "m/s²")
                    Spacer(Modifier.height(4.dp))
                    SensorGauge("Gyroscope", gyroMagnitude, rotationThreshold, "rad/s")
                }

                Spacer(Modifier.height(4.dp))

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
                            if (isServiceRunning(context)) {
                                MessageSender.sendAlertSms(
                                    context     = context,
                                    timestampMs = System.currentTimeMillis(),
                                    alertType   = Constants.ALERT_TYPE_STOP,
                                    detail      = "Surveillance poulinage arrêtée"
                                )
                            }
                            context.stopService(Intent(context, FoalingDetectionService::class.java))
                            status = "Arrêté"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        modifier = Modifier.weight(1f)
                    ) { Text("Arrêter") }
                }

                // Temps d'attente (anciennement "cooldown")
                val cooldownRemaining = run {
                    val last = prefs.getLong(Constants.PREF_LAST_ALERT_TIME, 0L)
                    val remaining = Constants.COOLDOWN_MS - (System.currentTimeMillis() - last)
                    if (remaining > 0) remaining else 0L
                }
                if (cooldownRemaining > 0L) {
                    val mins = cooldownRemaining / 60_000
                    val secs = (cooldownRemaining % 60_000) / 1_000
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3E0))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Temps d'attente actif : ${mins}m ${secs}s restant",
                            fontSize = 13.sp,
                            color = Color(0xFFE65100)
                        )
                        TextButton(onClick = {
                            prefs.edit().putLong(Constants.PREF_LAST_ALERT_TIME, 0L).apply()
                            status = "Temps d'attente réinitialisé"
                        }) {
                            Text("Reset", color = Color(0xFFE65100), fontSize = 13.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                Text(
                    text = "Configuration messagerie",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Numéro de téléphone destinataire") },
                    placeholder = { Text("ex: +33612345678") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { saveConfig(); status = "Configuration sauvegardée" },
                        modifier = Modifier.weight(1f)
                    ) { Text("Sauvegarder") }

                    TextButton(
                        onClick = {
                            saveConfig()
                            status = "Envoi SMS test..."
                            MessageSender.sendAlertSms(
                                context = context,
                                timestampMs = System.currentTimeMillis(),
                                alertType = Constants.ALERT_TYPE_MOVEMENT,
                                detail = "Test manuel depuis l'app",
                                onResult = { success, error ->
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

                val todayEvents = historyEvents.filter { it.timestamp >= todayStart }
                Text(
                    text = "Alertes du jour (${todayEvents.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (todayEvents.isEmpty()) {
                    Text(
                        text = "Aucune alerte aujourd'hui.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    todayEvents.forEach { event ->
                        AlertEventRow(event = event, dateFormat = dateFormat)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            // =================================================================
            // HISTORIQUE
            // =================================================================
            Screen.History -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Historique (${historyEvents.size})",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
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

            // =================================================================
            // PARAMÈTRES
            // =================================================================
            Screen.Settings -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = "Paramètres avancés", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Ces réglages contrôlent la sensibilité de la détection. Modifie-les avec précaution — une mauvaise valeur peut générer des fausses alertes ou en rater de vraies.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                // ---- Seuil accéléromètre ----
                Text(
                    text = "Seuil accéléromètre : %.2f m/s² (%.2f g)".format(
                        accelThreshold,
                        accelThreshold / 9.81f
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Niveau de secousse à partir duquel l'app considère qu'il se passe quelque chose. " +
                            "Trop bas = fausses alertes à la moindre vibration. " +
                            "Trop haut = alertes manquées si la jument bouge doucement. " +
                            "La valeur par défaut (1,8g) correspond à un mouvement fort typique d'une jument qui se couche ou se lève.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = accelThreshold,
                    onValueChange = { accelThreshold = it },
                    valueRange = 4.9f..24.5f,
                    modifier = Modifier.fillMaxWidth(),
                    steps = 29
                )
                Text(
                    text = "Plage : 0,5g (4,9 m/s²)  ←  valeur actuelle  →  2,5g (24,5 m/s²)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // ---- Seuil gyroscope ----
                Text(
                    text = "Seuil gyroscope : %.2f rad/s (≈%.0f°/s)".format(
                        rotationThreshold,
                        rotationThreshold * 57.3f
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Vitesse de rotation du téléphone à partir de laquelle l'app détecte un retournement. " +
                            "Trop bas = l'app se déclenche pour rien (même un léger déplacement du téléphone). " +
                            "Trop haut = la jument peut se retourner sans que l'app le voie. " +
                            "La valeur par défaut (≈115°/s) est calibrée pour un roulement typique lors d'une mise-bas.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = rotationThreshold,
                    onValueChange = { rotationThreshold = it },
                    valueRange = 0.5f..3.0f,
                    modifier = Modifier.fillMaxWidth(),
                    steps = 24
                )
                Text(
                    text = "Plage : 0,5 rad/s (≈29°/s)  ←  valeur actuelle  →  3,0 rad/s (≈172°/s)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // ---- Min crossings ----
                Text(
                    text = "Dépassements minimum : $minCrossings",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Combien de fois le seuil d'accélération doit être franchi dans la fenêtre de temps avant d'envoyer une alerte. " +
                            "Plus ce nombre est élevé, moins il y a de fausses alertes — mais plus on risque d'en manquer. " +
                            "3 est un bon compromis : il faut 3 secousses fortes en peu de temps pour déclencher l'alerte.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = minCrossings.toFloat(),
                    onValueChange = { minCrossings = it.toInt() },
                    valueRange = 1f..10f,
                    modifier = Modifier.fillMaxWidth(),
                    steps = 8
                )
                Text(
                    text = "Plage : 1 (très sensible)  ←  valeur actuelle  →  10 (très strict)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // ---- Min rotation samples ----
                Text(
                    text = "Échantillons de rotation minimum : $minRotationSamples",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Combien d'instants consécutifs de rotation forte sont nécessaires pour déclencher une alerte. " +
                            "Fonctionne exactement comme le réglage précédent, mais pour la rotation plutôt que les secousses. " +
                            "Plus c'est élevé, plus la détection est stricte et moins il y a de fausses alertes.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = minRotationSamples.toFloat(),
                    onValueChange = { minRotationSamples = it.toInt() },
                    valueRange = 1f..10f,
                    modifier = Modifier.fillMaxWidth(),
                    steps = 8
                )
                Text(
                    text = "Plage : 1 (très sensible)  ←  valeur actuelle  →  10 (très strict)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // ---- Fenêtre de temps ----
                Text(
                    text = "Fenêtre de temps : ${windowSize}s",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Durée pendant laquelle l'app regarde en arrière pour compter les dépassements. " +
                            "Exemple avec 10s : les 3 secousses doivent toutes arriver en moins de 10 secondes pour déclencher l'alerte. " +
                            "Plus grande = plus permissif (détecte des mouvements étalés). " +
                            "Plus petite = plus strict (détecte seulement des mouvements rapides et concentrés).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = windowSize.toFloat(),
                    onValueChange = { windowSize = it.toLong() },
                    valueRange = 5f..20f,
                    modifier = Modifier.fillMaxWidth(),
                    steps = 14
                )
                Text(
                    text = "Plage : 5s (strict)  ←  valeur actuelle  →  20s (permissif)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        saveAdvancedSettings()
                        status = "Paramètres sauvegardés"
                        if (isServiceRunning(context)) {
                            context.stopService(Intent(context, FoalingDetectionService::class.java))
                            Handler(Looper.getMainLooper()).postDelayed({
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, FoalingDetectionService::class.java)
                                )
                            }, 500)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sauvegarder les paramètres")
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun isServiceRunning(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == FoalingDetectionService::class.java.name }
}

@Composable
private fun SensorGauge(label: String, value: Float, threshold: Float, unit: String) {
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

@Composable
private fun AlertEventRow(event: AlertHistory.Event, dateFormat: SimpleDateFormat) {
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
