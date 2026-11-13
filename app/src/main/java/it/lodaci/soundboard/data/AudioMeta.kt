package it.lodaci.soundboard.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

object AudioMeta {
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    fun durationMs(context: Context, uri: Uri): Long = runCatching {
        val r = MediaMetadataRetriever()
        try {
            if (uri.scheme == "file" || uri.scheme == null) r.setDataSource(uri.path) else r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            r.release()
        }
    }.getOrDefault(0L)

    fun titleFromFileName(name: String): String =
        name.substringBeforeLast('.').replace('_', ' ').trim().ifEmpty { name }
}
