package com.example.soccergamemanager.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("soccer_cloud_credentials", Context.MODE_PRIVATE)

    fun saveDeviceToken(localTeamId: String, token: String) = save("device_$localTeamId", token)

    fun deviceToken(localTeamId: String): String? = read("device_$localTeamId")

    fun clearDeviceToken(localTeamId: String) {
        preferences.edit().remove("device_$localTeamId").apply()
    }

    fun saveControllerToken(gameId: String, token: String) = save("controller_$gameId", token)

    fun controllerToken(gameId: String): String? = read("controller_$gameId")

    private fun save(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        preferences.edit().putString(key, encoded).apply()
    }

    private fun read(key: String): String? = runCatching {
        val encoded = preferences.getString(key, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_SIZE)
        val encrypted = bytes.copyOfRange(IV_SIZE, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "soccer-cloud-device-key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
