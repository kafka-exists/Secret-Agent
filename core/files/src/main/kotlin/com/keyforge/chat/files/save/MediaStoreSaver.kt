package com.keyforge.chat.files.save

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.keyforge.chat.files.GeneratedFile
import java.io.File
import java.io.FileOutputStream

/**
 * Saves generated files to `Downloads/KeyForge/` via MediaStore on Android 10+
 * and the legacy filesystem below. Returns a content:// or file:// URI the
 * FileProvider / share sheet can use. Binary content (base64-decoded) is written
 * as-is.
 */
object MediaStoreSaver {

    private const val RELATIVE_DIR = "KeyForge"

    fun save(context: Context, file: GeneratedFile): Uri? {
        val displayName = file.name
        val mimeType = file.mimeType

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$RELATIVE_DIR")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(file.content) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), RELATIVE_DIR).apply { mkdirs() }
            val out = File(dir, displayName)
            FileOutputStream(out).use { it.write(file.content) }
            Uri.fromFile(out)
        }
    }
}
