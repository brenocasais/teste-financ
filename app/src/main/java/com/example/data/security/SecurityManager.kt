package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

class SecurityManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "meu_financeiro_security_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            e.printStackTrace()
            context.getSharedPreferences("meu_financeiro_security_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_SECURITY_ENABLED = "security_enabled"
        private const val KEY_AUTH_METHOD = "auth_method" // "PIN" or "BIOMETRIC"
        private const val KEY_PIN_HASH = "pin_hash"
    }

    fun isSecurityEnabled(): Boolean {
        return prefs.getBoolean(KEY_SECURITY_ENABLED, false)
    }

    fun setSecurityEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SECURITY_ENABLED, enabled).apply()
    }

    fun getAuthMethod(): String {
        return prefs.getString(KEY_AUTH_METHOD, "PIN") ?: "PIN"
    }

    fun setAuthMethod(method: String) {
        prefs.edit().putString(KEY_AUTH_METHOD, method).apply()
    }

    fun setPin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return storedHash == hashPin(pin)
    }

    fun hasPin(): Boolean {
        return !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()
    }

    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun hashPin(pin: String): String {
        val salt = "MeuFinanceiro_LocalSalt_2026"
        val bytes = MessageDigest.getInstance("SHA-256").digest((pin + salt).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
