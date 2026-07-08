package com.masari.arsonkupik.audio

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

enum class AudioLibrarySource {
    DeviceScan,
    UserImport,
}

data class AudioLibraryTrack(
    val id: String,
    val title: String,
    val artist: String,
    val format: String,
    val durationSec: Int,
    val uri: Uri,
    val source: AudioLibrarySource,
)

object AudioLibraryScanner {
    suspend fun scanDevice(context: Context): List<AudioLibraryTrack> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf("1000")
        val sort = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        val tracks = mutableListOf<AudioLibraryTrack>()
        resolver.query(collection, projection, selection, args, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, mediaId)
                val displayName = cursor.getStringOrNull(displayCol).orEmpty()
                val title = cursor.getStringOrNull(titleCol)
                    ?.takeIf { it.isNotBlank() }
                    ?: cleanTitle(displayName)
                val artist = cursor.getStringOrNull(artistCol)
                    ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                    ?: "Local Audio"
                val durationSec = (cursor.getLongOrZero(durationCol) / 1000L).toInt().coerceAtLeast(1)
                val mime = cursor.getStringOrNull(mimeCol).orEmpty()
                tracks += AudioLibraryTrack(
                    id = "device:$mediaId",
                    title = title,
                    artist = artist,
                    format = "${formatName(displayName, mime)} - Device music",
                    durationSec = durationSec,
                    uri = uri,
                    source = AudioLibrarySource.DeviceScan,
                )
            }
        }

        tracks.distinctBy { it.id }
    }

    suspend fun describeUris(context: Context, uris: List<Uri>): List<AudioLibraryTrack> = withContext(Dispatchers.IO) {
        uris.distinct().mapNotNull { uri ->
            runCatching {
                val displayName = queryDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty()
                val metadata = readMetadata(context, uri)
                val title = metadata.title?.takeIf { it.isNotBlank() } ?: cleanTitle(displayName)
                val artist = metadata.artist?.takeIf { it.isNotBlank() } ?: "Imported Audio"
                val durationSec = metadata.durationMs?.let { (it / 1000L).toInt().coerceAtLeast(1) } ?: 240
                AudioLibraryTrack(
                    id = "import:${uri}",
                    title = title,
                    artist = artist,
                    format = "${formatName(displayName, metadata.mime.orEmpty())} - Imported file",
                    durationSec = durationSec,
                    uri = uri,
                    source = AudioLibrarySource.UserImport,
                )
            }.getOrNull()
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getStringOrNull(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
        return null
    }

    private fun readMetadata(context: Context, uri: Uri): Metadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Metadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private data class Metadata(
        val title: String?,
        val artist: String?,
        val durationMs: Long?,
        val mime: String?,
    )

    private fun cleanTitle(name: String): String {
        val fallback = name.substringAfterLast('/').ifBlank { "Untitled Audio" }
        return fallback.substringBeforeLast('.', fallback).replace('_', ' ').trim().ifBlank { "Untitled Audio" }
    }

    private fun formatName(name: String, mime: String): String {
        val fromMime = when {
            mime.contains("mpeg", ignoreCase = true) -> "MP3"
            mime.contains("mp3", ignoreCase = true) -> "MP3"
            mime.contains("flac", ignoreCase = true) -> "FLAC"
            mime.contains("wav", ignoreCase = true) -> "WAV"
            mime.contains("aac", ignoreCase = true) -> "AAC"
            mime.contains("ogg", ignoreCase = true) -> "OGG"
            else -> null
        }
        val fromName = name.substringAfterLast('.', "")
            .takeIf { it.isNotBlank() && it.length <= 5 }
            ?.uppercase(Locale.US)
        return fromMime ?: fromName ?: "AUDIO"
    }

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun android.database.Cursor.getLongOrZero(index: Int): Long =
        if (isNull(index)) 0L else getLong(index)
}
