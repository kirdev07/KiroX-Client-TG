package com.kirox.app.ui.chat

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** File name for a downloaded message file: its own name, or one made from the type. */
internal fun displayFileName(path: String, name: String, mimeType: String): String {
    val clean = name.replace('/', '_').replace('\\', '_').trim()
    if (clean.isNotEmpty()) return clean
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: File(path).extension
    return "kirox_${System.currentTimeMillis()}" + if (extension.isNotEmpty()) ".$extension" else ""
}

internal fun mimeTypeOf(name: String, fallback: String): String =
    fallback.ifBlank { MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(name).extension.lowercase(Locale.ROOT)).orEmpty() }
        .ifBlank { "application/octet-stream" }

/** "512 Б", "1,2 МБ". */
internal fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> String.format(Locale("ru"), "%.1f КБ", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale("ru"), "%.1f МБ", bytes / (1024.0 * 1024))
    else -> String.format(Locale("ru"), "%.2f ГБ", bytes / (1024.0 * 1024 * 1024))
}

/**
 * Opens a downloaded file in another app. TDLib's own folder is never shared: the file is copied to
 * cache/shared (exposed by the FileProvider) and handed over read-only. Returns false when no app opens it.
 */
/** Share sheet with a copy of the file (send to another app or chat outside KiroX). */
internal suspend fun shareFile(context: Context, path: String, name: String, mimeType: String): Boolean = openFile(context, path, name, mimeType, Intent.ACTION_SEND)

internal suspend fun openFile(context: Context, path: String, name: String, mimeType: String, action: String = Intent.ACTION_VIEW): Boolean {
    val fileName = displayFileName(path, name, mimeType)
    val copy = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(context.cacheDir, "shared").apply { mkdirs() }
            // Older copies are not needed once opened; keep the folder small.
            root.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.deleteRecursively() }
            // One folder per source file: two documents with the same name never overwrite each other.
            val dir = File(root, Integer.toHexString(path.hashCode())).apply { mkdirs() }
            File(dir, fileName).also { File(path).copyTo(it, overwrite = true) }
        }.getOrNull()
    } ?: return false
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
    val intent = if (action == Intent.ACTION_SEND) Intent(action).setType(mimeTypeOf(fileName, mimeType)).putExtra(Intent.EXTRA_STREAM, uri)
        else Intent(action).setDataAndType(uri, mimeTypeOf(fileName, mimeType))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

/** Copies a downloaded file to the public Downloads/KiroX folder (MediaStore, no storage permission). */
internal suspend fun saveToDownloads(context: Context, path: String, name: String, mimeType: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val fileName = displayFileName(path, name, mimeType)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeTypeOf(fileName, mimeType))
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KiroX")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
        try {
            resolver.openOutputStream(uri)?.use { out -> File(path).inputStream().use { it.copyTo(out) } } ?: error("no stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            true
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }.getOrDefault(false)
}
