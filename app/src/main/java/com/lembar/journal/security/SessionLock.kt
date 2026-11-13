package com.lembar.journal.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One unlock per visit: after the user passes fingerprint/face/PIN, locked
 * entries stay readable until the app sits in the background for
 * [RELOCK_AFTER_MS].
 */
class SessionLock {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private var backgroundedAt = 0L

    fun markUnlocked() { _unlocked.value = true }
    fun lockNow() { _unlocked.value = false }

    fun onBackground() { backgroundedAt = System.currentTimeMillis() }

    fun onForeground() {
        if (backgroundedAt != 0L && System.currentTimeMillis() - backgroundedAt > RELOCK_AFTER_MS) lockNow()
        backgroundedAt = 0L
    }

    companion object {
        const val RELOCK_AFTER_MS = 60_000L
    }
}

/** Wraps BiometricPrompt. Falls back to the phone's PIN/pattern when biometrics fail. */
class Authenticator(private val activity: FragmentActivity, private val session: SessionLock) {

    private val allowed: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }

    /** False when the phone has no screen lock at all, so nothing could protect the journal. */
    fun isAvailable(): Boolean =
        BiometricManager.from(activity).canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = {},
        force: Boolean = false,
    ) {
        if (!force && session.unlocked.value) { onSuccess(); return }
        if (!isAvailable()) {
            onFailure("Aktifkan kunci layar (PIN, pola, atau sidik jari) di Pengaturan HP dulu.")
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    session.markUnlocked()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure(errString.toString())
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowed)
            .build()
        prompt.authenticate(info)
    }
}
