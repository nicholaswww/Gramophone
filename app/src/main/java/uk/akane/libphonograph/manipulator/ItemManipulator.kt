package uk.akane.libphonograph.manipulator

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import org.akanework.gramophone.logic.hasImprovedMediaStore
import org.akanework.gramophone.logic.hasMarkIsFavouriteStatus
import org.akanework.gramophone.logic.hasScopedStorageV2
import uk.akane.libphonograph.getIntOrNullIfThrow
import uk.akane.libphonograph.getLongOrNullIfThrow
import uk.akane.libphonograph.getStringOrNullIfThrow
import java.io.File
import java.io.IOException

object ItemManipulator {
    private const val TAG = "ItemManipulator"

    fun deleteSong(context: Context, file: File, id: Long): MediaStoreRequest {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.getContentUri("external"), id
        )
        val uris = mutableSetOf(uri)
        // TODO maybe don't hardcode these extensions twice, here and in LrcUtils?
        uris.addAll(setOf("ttml", "lrc", "srt").map {
            file.resolveSibling("${file.nameWithoutExtension}.$it")
        }.filter { it.exists() }.map {
            // It doesn't really make sense to have >1 subtitle file so we don't need to batch the queries.
            getIdForPath(context, it)
        }.filter { it != null }
            .map { ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), it!!) })
        return delete(context, uris)
    }

    fun deletePlaylist(context: Context, id: Long): MediaStoreRequest {
        val uri = ContentUris.withAppendedId(
            @Suppress("deprecation") MediaStore.Audio.Playlists.getContentUri("external"), id
        )
        return delete(context, setOf(uri))
    }

    // requires requestLegacyExternalStorage for simplicity
    fun delete(context: Context, uris: Set<Uri>): MediaStoreRequest {
        if (needRequestWrite(context, uris)) {
            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver, uris.toList()
            )
            return MediaStoreRequest(pendingIntent.intentSender)
        } else {
            return MediaStoreRequest {
                return@MediaStoreRequest try {
                    !uris.map {
                        context.contentResolver.delete(it, null, null) == 1
                    }.contains(false)
                } catch (_: SecurityException) {
                    false
                }
            }
        }
    }

    fun setFavorite(context: Context, uris: Set<Uri>, favorite: Boolean): IntentSender? {
        if (!hasImprovedMediaStore()) {
            // TODO Q- support
            return null
        }
        if (hasMarkIsFavouriteStatus()) {
            MediaStore.markIsFavoriteStatus(
                context.contentResolver, uris.toList(), favorite
            )
            return null
        } else if (needRequestWrite(context, uris)) {
            // This never actually asks the user for permission...
            val pendingIntent = MediaStore.createFavoriteRequest(
                context.contentResolver, uris.toList(), favorite
            )
            return pendingIntent.intentSender
        } else {
            val cv = ContentValues()
            cv.put(MediaStore.MediaColumns.IS_FAVORITE, if (favorite) 1 else 0)
            uris.forEach { uri ->
                if (context.contentResolver.update(uri, cv, null, null) != 1)
                    Log.w(TAG, "failed to favorite $uri")
            }
            return null
        }
    }

    fun createPlaylist(context: Context, name: String): File {
        val parent = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val out = File(parent, "$name.m3u")
        if (out.exists())
            throw IllegalArgumentException("tried to create playlist $out that already exists")
        PlaylistSerializer.write(context.applicationContext, out, listOf())
        return out
    }

    private fun getIdForPath(context: Context, file: File): Long? {
        val cursor = context.contentResolver.query(MediaStore.Files.getContentUri("external"),
            if (hasImprovedMediaStore())
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
            else arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.Files.FileColumns.DATA} = ?", arrayOf(file.absolutePath), null)
        if (cursor == null) return null
        cursor.use {
            if (!cursor.moveToFirst()) return null
            if (hasImprovedMediaStore()) {
                val typeColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val type = cursor.getIntOrNullIfThrow(typeColumn)
                if (type != MediaStore.Files.FileColumns.MEDIA_TYPE_SUBTITLE) {
                    Log.e(TAG, "expected $file to be a subtitle")
                    return null
                }
            }
            val idColumn = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            return cursor.getLongOrNullIfThrow(idColumn)
        }
    }

    @ChecksSdkIntAtLeast(Build.VERSION_CODES.R)
    fun needRequestWrite(context: Context, uris: Set<Uri>): Boolean {
        for (uri in uris)
            if (needRequestWrite(context, uri))
                return true
        return false
    }

    @ChecksSdkIntAtLeast(Build.VERSION_CODES.R)
    fun needRequestWrite(context: Context, uri: Uri): Boolean {
        return hasScopedStorageV2() && (!checkIfFileAttributedToSelf(context, uri)
                || !checkIfFileOnPrimaryVolume(context, uri)) &&
                context.checkUriPermission(uri, Process.myPid(), Process.myUid(),
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED
    }

    private fun checkIfFileOnPrimaryVolume(context: Context, uri: Uri): Boolean {
        // TODO: is this even needed? it was only added to test if it fixes a bug for a user.
        val cursor = context.contentResolver.query(uri,
            arrayOf(MediaStore.MediaColumns.VOLUME_NAME), null, null, null)
        if (cursor == null) return false
        cursor.use {
            if (!cursor.moveToFirst()) return false
            val column = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
            val volume = cursor.getStringOrNullIfThrow(column)
            return volume == "external_primary"
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkIfFileAttributedToSelf(context: Context, uri: Uri): Boolean {
        val cursor = context.contentResolver.query(uri,
            arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME), null, null, null)
        if (cursor == null) return false
        cursor.use {
            if (!cursor.moveToFirst()) return false
            val column = cursor.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
            val pkg = cursor.getStringOrNullIfThrow(column)
            return pkg == context.packageName
        }
    }

    fun addToPlaylist(context: Context, out: File, songs: List<File>) {
        if (!out.exists())
            throw IllegalArgumentException("tried to change playlist $out that doesn't exist")
        setPlaylistContent(context, out, PlaylistSerializer.read(out) + songs)
    }

    fun setPlaylistContent(context: Context, out: File, songs: List<File>) {
        if (!out.exists())
            throw IllegalArgumentException("tried to change playlist $out that doesn't exist")
        val backup = out.readBytes()
        try {
            PlaylistSerializer.write(context.applicationContext, out, songs)
        } catch (t: Throwable) {
            try {
                PlaylistSerializer.write(context.applicationContext, out.resolveSibling(
                    "${out.nameWithoutExtension}_NEW_${System.currentTimeMillis()}.m3u"), songs)
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            try {
                out.resolveSibling("${out.nameWithoutExtension}_BAK_${System.currentTimeMillis()}.${out.extension}").writeBytes(backup)
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            throw t
        }
    }

    fun renamePlaylist(context: Context, out: File, newName: String) {
        val new = out.resolveSibling("$newName.${out.extension}")
        if (new.exists())
            throw IOException("can't rename to existing")
        // don't use normal rename methods as media store caches the old title in that case
        new.writeBytes(out.readBytes())
        if (!out.delete())
            throw IOException("delete after rename failed")
        MediaScannerConnection.scanFile(context, arrayOf(out.toString(), new.toString()), null) { path, uri ->
            if (uri == null && path == new.toString()) {
                Log.e(TAG, "failed to scan renamed playlist $path")
            }
        }
    }

    class MediaStoreRequest {
        val startSystemDialog: IntentSender?
        val continueAction: (suspend () -> Boolean)?

        constructor(startSystemDialog: IntentSender) {
            this.startSystemDialog = startSystemDialog
            this.continueAction = null
        }

        constructor(continueAction: (suspend () -> Boolean)) {
            this.startSystemDialog = null
            this.continueAction = continueAction
        }
    }
}