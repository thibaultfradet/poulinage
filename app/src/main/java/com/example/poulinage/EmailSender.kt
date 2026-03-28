package com.example.poulinage

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.concurrent.Executors
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Envoie des emails via SMTP sur un thread d'arrière-plan dédié.
 *
 * Utilise JavaMail pour Android (com.sun.mail:android-mail).
 * Le réseau n'est JAMAIS accédé sur le thread principal.
 *
 * Configuration Gmail recommandée :
 *   - Serveur : smtp.gmail.com
 *   - Port    : 587 (STARTTLS)
 *   - Mot de passe : App Password (Google > Sécurité > Mots de passe des applications)
 */
object EmailSender {

    // Thread unique pour tous les envois (évite les envois simultanés)
    private val executor = Executors.newSingleThreadExecutor()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE)

    /**
     * Envoie l'email d'alerte en arrière-plan.
     *
     * @param context      Contexte pour lire la config SMTP dans SharedPreferences
     * @param timestampMs  Horodatage de la détection (epoch ms)
     * @param onResult     Callback appelé sur le thread SMTP (pas le main thread)
     *                     avec (success, errorMessage)
     */
    fun sendAlertAsync(
        context: Context,
        timestampMs: Long,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null
    ) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val host      = prefs.getString(Constants.PREF_SMTP_HOST, "smtp.gmail.com") ?: "smtp.gmail.com"
        val port      = prefs.getString(Constants.PREF_SMTP_PORT, "587") ?: "587"
        val user      = prefs.getString(Constants.PREF_SMTP_USER, "") ?: ""
        val password  = prefs.getString(Constants.PREF_SMTP_PASSWORD, "") ?: ""
        val destEmail = prefs.getString(Constants.PREF_EMAIL_DEST, "") ?: ""

        if (user.isBlank() || password.isBlank() || destEmail.isBlank()) {
            Log.e(Constants.TAG, "Configuration SMTP incomplète, email non envoyé")
            onResult?.invoke(false, "Configuration SMTP incomplète")
            return
        }

        val humanTimestamp = dateFormat.format(Date(timestampMs))

        executor.submit {
            try {
                val props = Properties().apply {
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")    // chiffrement STARTTLS (port 587)
                    put("mail.smtp.host", host)
                    put("mail.smtp.port", port)
                    put("mail.smtp.ssl.trust", host)             // faire confiance au serveur configuré
                    put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
                    put("mail.smtp.connectiontimeout", "15000")  // 15s timeout connexion
                    put("mail.smtp.timeout", "15000")            // 15s timeout lecture
                    put("mail.smtp.writetimeout", "15000")       // 15s timeout écriture
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(user, password)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(user))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(destEmail))
                    subject = "ALERTE POULINAGE"
                    setText(buildEmailBody(humanTimestamp), "UTF-8")
                }

                Transport.send(message)
                Log.i(Constants.TAG, "Email d'alerte envoyé à $humanTimestamp → $destEmail")
                onResult?.invoke(true, null)

            } catch (e: MessagingException) {
                Log.e(Constants.TAG, "Échec envoi email (MessagingException) : ${e.message}", e)
                onResult?.invoke(false, e.message)
            } catch (e: Exception) {
                Log.e(Constants.TAG, "Échec envoi email (inattendu) : ${e.message}", e)
                onResult?.invoke(false, e.message)
            }
        }
    }

    private fun buildEmailBody(humanTimestamp: String): String = """
        ALERTE POULINAGE DÉTECTÉE

        Date et heure : $humanTimestamp

        Des mouvements inhabituels et répétés ont été détectés par le capteur.
        Veuillez vérifier immédiatement l'état de la jument.

        --
        Envoyé automatiquement par l'application Détection Poulinage.
    """.trimIndent()
}
