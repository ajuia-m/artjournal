package com.ajuia.artjournal.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSessionStore(context: Context) : SessionStore {
    private val securePreferences = context.applicationContext.getSharedPreferences(
        SECURE_PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val workspacePreferences = context.applicationContext.getSharedPreferences(
        WORKSPACE_PREFERENCES,
        Context.MODE_PRIVATE
    )

    override fun readRefreshToken(): String? {
        val encrypted = securePreferences.getString(KEY_REFRESH_TOKEN, null) ?: return null
        val initializationVector = securePreferences.getString(KEY_REFRESH_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(initializationVector, Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
                StandardCharsets.UTF_8
            )
        }.getOrElse {
            clearRefreshToken()
            null
        }
    }

    override fun writeRefreshToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        securePreferences.edit()
            .putString(KEY_REFRESH_TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_REFRESH_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    override fun clearRefreshToken() {
        securePreferences.edit()
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_REFRESH_IV)
            .apply()
    }

    override fun readSelectedSchoolId(): String? =
        workspacePreferences.getString(KEY_SELECTED_SCHOOL, null)

    override fun writeSelectedSchoolId(schoolId: String) {
        workspacePreferences.edit().putString(KEY_SELECTED_SCHOOL, schoolId).apply()
    }

    override fun clearSelectedSchoolId() {
        workspacePreferences.edit().remove(KEY_SELECTED_SCHOOL).apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "artjournal.server.refresh-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val SECURE_PREFERENCES = "server_session_secure"
        const val WORKSPACE_PREFERENCES = "server_workspace"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_REFRESH_IV = "refresh_token_iv"
        const val KEY_SELECTED_SCHOOL = "selected_school_id"
    }
}

class SharedPreferencesWorkspacePreferences(context: Context) : WorkspacePreferences {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE
    )

    override fun readMode(): WorkspaceMode? = preferences.getString(KEY_MODE, null)
        ?.let { stored -> WorkspaceMode.entries.firstOrNull { it.name == stored } }

    override fun writeMode(mode: WorkspaceMode) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
    }

    override fun clearMode() {
        preferences.edit().remove(KEY_MODE).apply()
    }

    private companion object {
        const val PREFERENCES = "workspace_mode"
        const val KEY_MODE = "mode"
    }
}
