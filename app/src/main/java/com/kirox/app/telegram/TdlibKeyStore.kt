package com.kirox.app.telegram

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the TDLib database encryption key wrapped by an Android Keystore AES key.
 * The wrapped key lives in noBackupFilesDir, so it never leaves the device.
 */
class TdlibKeyStore(context: Context, sessionName: String) {
    private val file = File(context.noBackupFilesDir, "tdlib-$sessionName.key")
    private val alias = "kirox_tdlib_$sessionName"

    val exists: Boolean get() = file.exists()

    /** Null when no key is stored or the Keystore entry can no longer unwrap it. */
    fun load(): ByteArray? = if (!file.exists()) null else runCatching { decrypt(file.readBytes()) }.getOrNull()

    fun generate(): ByteArray = ByteArray(KEY_SIZE).also(SecureRandom()::nextBytes)

    fun save(key: ByteArray) {
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(encrypt(key))
        if (!tmp.renameTo(file)) error("Не удалось сохранить ключ базы TDLib")
    }

    fun delete() {
        file.delete()
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, data, 0, IV_SIZE))
        }
        return cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE)
    }

    private fun keyStore() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun secretKey(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE = 32
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
