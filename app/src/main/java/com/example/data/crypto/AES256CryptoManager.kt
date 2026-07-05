package com.example.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

object AES256CryptoManager {

    private const val TAG = "AES256CryptoManager"
    private const val KEY_ALIAS = "LocalHubKey"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"
    private const val IV_SIZE = 16
    private const val BUFFER_SIZE = 8192

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    private fun getOrCreateKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER
            )
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setKeySize(256)
                    .build()
            )
            keyGen.generateKey()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun encryptFile(input: File, output: File) {
        try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv

            FileInputStream(input).use { fis ->
                FileOutputStream(output).use { fos ->
                    fos.write(iv)
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val encrypted = cipher.update(buffer, 0, bytesRead)
                        if (encrypted != null) fos.write(encrypted)
                    }
                    val finalBlock = cipher.doFinal()
                    if (finalBlock != null) fos.write(finalBlock)
                    fos.flush()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Şifreleme hatası: ${e.message}", e)
            throw e
        }
    }

    fun decryptFile(input: File, output: File) {
        try {
            val key = getOrCreateKey()
            FileInputStream(input).use { fis ->
                val iv = ByteArray(IV_SIZE)
                var totalRead = 0
                while (totalRead < IV_SIZE) {
                    val read = fis.read(iv, totalRead, IV_SIZE - totalRead)
                    if (read == -1) throw Exception("IV okunamadı")
                    totalRead += read
                }

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

                CipherInputStream(fis, cipher).use { cis ->
                    FileOutputStream(output).use { fos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (cis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                        }
                        fos.flush()
                    }
                }
            }

            if (!output.exists() || output.length() == 0L) {
                throw Exception("Çözülen dosya boş")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Çözme hatası: ${e.message}", e)
            output.delete()
            throw e
        }
    }

    // Helper functions for backward compatibility with the rest of the project
    
    fun decryptManualToFile(input: File, output: File) {
        decryptFile(input, output)
    }

    fun getCipherForDecryption(iv: ByteArray): Cipher {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return cipher
    }

    fun createDecryptCipher(encryptedFile: File): Pair<Cipher, FileInputStream> {
        val key = getOrCreateKey()
        val fis = FileInputStream(encryptedFile)

        val iv = ByteArray(IV_SIZE)
        var totalRead = 0
        while (totalRead < IV_SIZE) {
            val read = fis.read(iv, totalRead, IV_SIZE - totalRead)
            if (read == -1) {
                fis.close()
                throw Exception("IV okunamadı")
            }
            totalRead += read
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

        return Pair(cipher, fis)
    }

    fun encryptBytes(bytes: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(bytes)
        
        // Return IV + Encrypted Data
        val result = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(encrypted, 0, result, iv.size, encrypted.size)
        return result
    }

    fun decryptBytes(bytes: ByteArray): ByteArray {
        if (bytes.size < IV_SIZE) throw IllegalArgumentException("Data too short")
        val iv = ByteArray(IV_SIZE)
        System.arraycopy(bytes, 0, iv, 0, IV_SIZE)
        
        val encryptedData = ByteArray(bytes.size - IV_SIZE)
        System.arraycopy(bytes, IV_SIZE, encryptedData, 0, encryptedData.size)
        
        val cipher = getCipherForDecryption(iv)
        return cipher.doFinal(encryptedData)
    }
    
    fun encryptFile(context: Context, uri: android.net.Uri, output: File) {
        if (uri.scheme == "file" && uri.path != null) {
            encryptFile(File(uri.path!!), output)
            return
        }
        try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv

            context.contentResolver.openInputStream(uri)?.use { fis ->
                FileOutputStream(output).use { fos ->
                    fos.write(iv)
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val encrypted = cipher.update(buffer, 0, bytesRead)
                        if (encrypted != null) fos.write(encrypted)
                    }
                    val finalBlock = cipher.doFinal()
                    if (finalBlock != null) fos.write(finalBlock)
                    fos.flush()
                }
            } ?: throw Exception("Could not open InputStream from Uri: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Şifreleme hatası: ${e.message}", e)
            output.delete()
            throw e
        }
    }

    fun decryptToTempFile(encryptedFile: File, tempFile: File) {
        decryptFile(encryptedFile, tempFile)
    }
}
