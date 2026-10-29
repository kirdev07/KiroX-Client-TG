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
    /** A key being switched to (legacy database encryption) until TDLib confirmed the switch. */
    private val pendingFile = File(context.noBackupFilesDir, "tdlib-$sessionName.key.pending")
    private val alias = "kirox_tdlib_$sessionName"

    /** The stored key can never be unwrapped again (Keystore entry gone or file damaged). */
    class KeyLostException : Exception("Ключ базы TDLib утерян")

    val exists: Boolean get() = file.exists()

    /**
     * Null when no key is stored. Throws [KeyLostException] when the key is really gone; any other
     * exception is a temporary Keystore failure, and the caller must not delete anything for it.
     */
    fun load(): ByteArray? = if (!file.exists()) null else unwrap(file)

    /** The key of an unfinished re-encryption, if any; never throws. */
    fun loadPending(): ByteArray? = if (!pendingFile.exists()) null else runCatching { unwrap(pendingFile) }.getOrNull()

    fun generate(): ByteArray = ByteArray(KEY_SIZE).also(SecureRandom()::nextBytes)

    fun save(key: ByteArray) = write(file, key)

    fun savePending(key: ByteArray) = write(pendingFile, key)

    /** The pending key is now the database key. */
    fun promotePending() {
        if (pendingFile.exists() && !pendingFile.renameTo(file)) error("Не удалось сохранить ключ базы TDLib")
    }

    fun dropPending() { pendingFile.delete() }

    fun delete() {
        file.delete()
        pendingFile.delete()
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun unwrap(source: File): ByteArray {
        val data = source.readBytes()
        if (data.size <= IV_SIZE) throw KeyLostException()
        return try {
            decrypt(data)
        } catch (_: javax.crypto.AEADBadTagException) {
            throw KeyLostException()
        } catch (_: java.security.UnrecoverableKeyException) {
            throw KeyLostException()
        } catch (_: java.security.InvalidKeyException) {
            // Includes KeyPermanentlyInvalidatedException.
            throw KeyLostException()
        }
    }

    private fun write(target: File, key: ByteArray) {
        val tmp = File(target.path + ".tmp")
        java.io.FileOutputStream(tmp).use { out ->
            out.write(encrypt(key))
            // On disk before the rename: a power cut must not leave an empty key file.
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) error("Не удалось сохранить ключ базы TDLib")
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
