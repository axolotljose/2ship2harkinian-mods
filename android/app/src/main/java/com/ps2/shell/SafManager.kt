package com.ps2.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

object SafManager {

    /**
     * Persists read and write permissions for a chosen Storage Access Framework tree URI.
     */
    fun persistUriPermission(context: Context, uri: Uri) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /**
     * Checks if persistable permission is currently held for a given URI string.
     */
    fun hasPersistedUriPermission(context: Context, uriString: String): Boolean {
        val persisted = context.contentResolver.persistedUriPermissions
        return persisted.any { it.uri.toString() == uriString && it.isReadPermission }
    }

    /**
     * Attempts to resolve a filesystem path from a SAF tree Uri.
     */
    fun getPathFromUri(context: Context, uri: Uri): String {
        var docId = DocumentsContract.getTreeDocumentId(uri)
        if (docId.contains(":")) {
            val split = docId.split(":")
            val type = split[0]
            val relativePath = split.getOrElse(1) { "" }

            if ("primary".equalsIgnoreCase(type)) {
                val primaryStorage = Environment.getExternalStorageDirectory().absolutePath
                return if (relativePath.isNotEmpty()) "$primaryStorage/$relativePath" else primaryStorage
            } else {
                // Secondary SD card or external storage drive
                val extDirs = context.getExternalFilesDirs(null)
                for (dir in extDirs) {
                    if (dir != null) {
                        val path = dir.absolutePath
                        val index = path.indexOf("/Android/data")
                        if (index != -1) {
                            val root = path.substring(0, index)
                            if (root.contains(type)) {
                                return if (relativePath.isNotEmpty()) "$root/$relativePath" else root
                            }
                        }
                    }
                }
                return "/storage/$type/$relativePath"
            }
        }
        return uri.path ?: uri.toString()
    }

    private fun String.equalsIgnoreCase(other: String): Boolean =
        this.equals(other, ignoreCase = true)
}
