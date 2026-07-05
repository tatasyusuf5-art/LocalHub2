package com.example.data.util

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build

object MediaStoreHelper {
    /**
     * Attempts to delete a media file from the MediaStore.
     * Returns an IntentSender if a permission confirmation dialog is required (Android 10+).
     * Returns null if the file was deleted successfully or does not exist.
     */
    fun deleteOriginalFile(context: Context, uri: Uri): IntentSender? {
        val resolver = context.contentResolver
        try {
            val rowsDeleted = resolver.delete(uri, null, null)
            if (rowsDeleted > 0) {
                return null
            }
        } catch (securityException: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val recoverableSecurityException = securityException as? RecoverableSecurityException
                    ?: (securityException.cause as? RecoverableSecurityException)
                if (recoverableSecurityException != null) {
                    return recoverableSecurityException.userAction.actionIntent.intentSender
                }
            }
            throw securityException
        }
        return null
    }
}
