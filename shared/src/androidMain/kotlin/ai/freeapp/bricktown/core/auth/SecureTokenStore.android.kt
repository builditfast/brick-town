package ai.freeapp.bricktown.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual class SecureTokenStoreFactory(private val context: Context) {
    actual fun create(): TokenStore = KeystoreTokenStore(context)
}

/**
 * Tokens in SharedPreferences, each encrypted with an AES-GCM key that never
 * leaves the Android Keystore, so the prefs file holds only `iv:ciphertext`.
 *
 * A value the key can no longer open (the key was wiped, the file came from
 * another device) reads as signed out rather than as a crash. The file is also
 * excluded from backups (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`);
 * rename [PREFS] there too if it ever changes.
 */
internal class KeystoreTokenStore(context: Context) : TokenStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Only through [key], which is synchronized: two first saves racing on
    // separate threads would otherwise each generate a key under the same
    // alias, and whatever the loser encrypted could never be read back.
    private var cachedKey: SecretKey? = null

    override fun read(): AuthTokens? {
        val access = decrypt(prefs.getString(KEY_ACCESS, null)) ?: return null
        val refresh = decrypt(prefs.getString(KEY_REFRESH, null)) ?: return null
        return AuthTokens(access, refresh)
    }

    // commit(), not apply(): the server revokes the old refresh token as soon
    // as it hands out a new one, so a write lost to a process kill would sign
    // the user out on the next launch.
    override fun save(tokens: AuthTokens) {
        val (access, refresh) = try {
            encrypt(tokens.accessToken) to encrypt(tokens.refreshToken)
        } catch (broken: Exception) {
            // A key that exists but no longer works (some vendors' Keystores
            // lose keys over an OS update) would fail every sign-in until the
            // user clears the app's data. Nothing it encrypted can be read
            // anyway, and both values are about to be replaced.
            replaceKey()
            encrypt(tokens.accessToken) to encrypt(tokens.refreshToken)
        }
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).commit()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(plain.encodeToByteArray())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(body, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String?): String? {
        val (iv, body) = stored?.split(":", limit = 2)?.takeIf { it.size == 2 } ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
            cipher.doFinal(Base64.decode(body, Base64.NO_WRAP)).decodeToString()
        }.getOrNull()
    }

    @Synchronized
    private fun key(): SecretKey = cachedKey ?: loadOrCreateKey().also { cachedKey = it }

    @Synchronized
    private fun replaceKey() {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
        cachedKey = null
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        /** `shared_prefs/secure_tokens.xml`. Package-free, so the scaffold's rename leaves it alone. */
        const val PREFS = "secure_tokens"
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "secure_tokens_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
    }
}
