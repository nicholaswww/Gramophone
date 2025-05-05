package uk.akane.libphonograph.manipulator

import android.content.ContentUris
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
import org.akanework.gramophone.logic.hasScopedStorageV2
import uk.akane.libphonograph.getStringOrNullIfThrow
import java.io.File
import java.io.IOException

object ItemManipulator {
    private const val TAG = "ItemManipulator"

    fun deleteSong(context: Context, id: Long): DeleteRequest {
        // TODO delete .ttml / .lrc as well if present (using MediaStore.Files because they are subtitles)
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.getContentUri("external"), id
        )
        return delete(context, uri)
    }

    fun deletePlaylist(context: Context, id: Long): DeleteRequest {
        val uri = ContentUris.withAppendedId(
            @Suppress("deprecation") MediaStore.Audio.Playlists.getContentUri("external"), id
        )
        return delete(context, uri)
    }

    // requires requestLegacyExternalStorage for simplicity
    fun delete(context: Context, uri: Uri): DeleteRequest {
        if (needRequestWrite(context, uri)) {
            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver, listOf(uri)
            )
            return DeleteRequest(pendingIntent.intentSender)
        } else {
            return DeleteRequest {
                return@DeleteRequest try {
                    context.contentResolver.delete(uri, null, null) == 1
                } catch (_: SecurityException) {
                    false
                }
            }
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

    @ChecksSdkIntAtLeast(Build.VERSION_CODES.R)
    fun needRequestWrite(context: Context, uri: Uri): Boolean {
        return hasScopedStorageV2() && !checkIfFileAttributedToSelf(context, uri) &&
                context.checkUriPermission(uri, Process.myPid(), Process.myUid(),
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED
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
                out.resolveSibling("${out.name}.bak").writeBytes(backup)
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

    class DeleteRequest {
        val startSystemDialog: IntentSender?
        val continueDelete: (suspend () -> Boolean)?

        constructor(startSystemDialog: IntentSender) {
            this.startSystemDialog = startSystemDialog
            this.continueDelete = null
        }

        constructor(continueDelete: (suspend () -> Boolean)) {
            this.startSystemDialog = null
            this.continueDelete = continueDelete
        }
    }
}