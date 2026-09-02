package crucible.lens.data.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.secureCredentialDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_credentials")

class AndroidSecureCredentialStore(
    context: Context
) : SecureCredentialStore {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    override suspend fun read(): String? = mutex.withLock {
        val preferences = appContext.secureCredentialDataStore.data.first()
        val encodedCiphertext = preferences[CIPHERTEXT]
        val encodedIv = preferences[INITIALIZATION_VECTOR]
        if (encodedCiphertext == null && encodedIv == null) return@withLock null
        checkNotNull(encodedCiphertext) { "Missing encrypted credential" }
        checkNotNull(encodedIv) { "Missing credential initialization vector" }

        val key = existingKey() ?: error("Credential encryption key is unavailable")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(AUTHENTICATION_TAG_BITS, Base64.decode(encodedIv, Base64.NO_WRAP))
        )
        cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP)).decodeToString()
    }

    override suspend fun write(value: String) = mutex.withLock {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.encodeToByteArray())
        appContext.secureCredentialDataStore.edit { preferences ->
            preferences[CIPHERTEXT] = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            preferences[INITIALIZATION_VECTOR] = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        }
        Unit
    }

    override suspend fun delete() = mutex.withLock {
        appContext.secureCredentialDataStore.edit { preferences ->
            preferences.remove(CIPHERTEXT)
            preferences.remove(INITIALIZATION_VECTOR)
        }
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun existingKey(): SecretKey? = loadKeyStore().getKey(KEY_ALIAS, null) as? SecretKey

    private fun getOrCreateKey(): SecretKey = existingKey() ?: KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        ANDROID_KEY_STORE
    ).run {
        init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        generateKey()
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "crucible_lens_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AUTHENTICATION_TAG_BITS = 128
        val CIPHERTEXT = stringPreferencesKey("api_key_ciphertext")
        val INITIALIZATION_VECTOR = stringPreferencesKey("api_key_iv")
    }
}
