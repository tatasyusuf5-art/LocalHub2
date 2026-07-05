package com.example.data.crypto

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.crypto.CipherInputStream

class EncryptedFileDataSource : BaseDataSource(false) {

    private var fileInputStream: FileInputStream? = null
    private var cipherInputStream: CipherInputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        
        uri = dataSpec.uri
        val filePath = dataSpec.uri.path
            ?: throw IOException("Geçersiz URI: ${dataSpec.uri}")

        val encryptedFile = File(filePath)
        if (!encryptedFile.exists()) {
            throw IOException("Şifreli dosya bulunamadı: $filePath")
        }

        try {
            val (cipher, fis) = AES256CryptoManager.createDecryptCipher(encryptedFile)
            fileInputStream = fis
            val cis = CipherInputStream(fis, cipher)
            cipherInputStream = cis

            // dataSpec.position kadar atla (seek desteği için)
            if (dataSpec.position > 0) {
                val skipBuffer = ByteArray(8192)
                var skipped = 0L
                while (skipped < dataSpec.position) {
                    val toSkip = minOf(skipBuffer.size.toLong(), dataSpec.position - skipped).toInt()
                    val read = cis.read(skipBuffer, 0, toSkip)
                    if (read == -1) break
                    skipped += read
                }
            }

            bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                C.LENGTH_UNSET.toLong()
            } else {
                dataSpec.length
            }

            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            close()
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }
        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        val cis = cipherInputStream ?: return C.RESULT_END_OF_INPUT
        try {
            val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                length
            } else {
                minOf(bytesRemaining, length.toLong()).toInt()
            }

            val read = cis.read(buffer, offset, bytesToRead)
            if (read == -1) {
                if (bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining > 0L) {
                    throw IOException("EOF reached before reading expected length")
                }
                return C.RESULT_END_OF_INPUT
            }

            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= read
            }
            bytesTransferred(read)
            return read
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    override fun getUri(): Uri? {
        return uri
    }

    @Throws(IOException::class)
    override fun close() {
        uri = null
        try {
            cipherInputStream?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            cipherInputStream = null
        }
        try {
            fileInputStream?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            fileInputStream = null
        }
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}

class EncryptedFileDataSourceFactory : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return EncryptedFileDataSource()
    }
}
