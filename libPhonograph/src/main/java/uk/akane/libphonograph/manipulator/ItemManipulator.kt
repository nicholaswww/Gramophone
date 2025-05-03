package uk.akane.libphonograph.manipulator

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import uk.akane.libphonograph.TAG
import uk.akane.libphonograph.hasScopedStorageV2
import java.io.File
import java.io.IOException

object ItemManipulator {
    // requires requestLegacyExternalStorage for simplicity
    fun deleteSong(context: Context, id: Long): DeleteRequest {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.getContentUri("external"), id)
        val selector = "${MediaStore.Images.Media._ID} = ?"
        if (hasScopedStorageV2() && context.checkCallingOrSelfUriPermission(
                uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver, listOf(uri)
            )
            return DeleteRequest(pendingIntent.intentSender)
        } else {
            return DeleteRequest {
                return@DeleteRequest try {
                    context.contentResolver.delete(uri, selector, arrayOf(id.toString())) == 1
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

    fun setPlaylistContent(context: Context, out: File, songs: List<File>) {
        // TODO try write request for non-owned files (can we find out what we own?)
        if (!out.exists())
            throw IllegalArgumentException("tried to change playlist $out that doesn't exist")
        val backup = out.readBytes()
        try {
            PlaylistSerializer.write(context.applicationContext, out, songs)
        } catch (t: Throwable) {
            try {
                PlaylistSerializer.write(context.applicationContext, out.resolveSibling(
                    "${out.nameWithoutExtension}_NEW_${System.currentTimeMillis()}.${out.extension}"), songs)
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
        // TODO try delete request for non-owned files
        val new = out.resolveSibling("$newName.${out.extension}")
        out.renameTo(new)
        MediaScannerConnection.scanFile(context, arrayOf(out.toString(), new.toString()), null) { path, uri ->
            if (uri == null && path == new.toString()) {
                Log.e(TAG, "failed to scan renamed playlist $path")
            }
        }
    }

    fun deletePlaylist(context: Context, out: File) {
        // TODO try delete request for non-owned files
        Log.i(TAG, "deleting $out")
        if (!out.delete())
            throw IOException("delete returned false")
        MediaScannerConnection.scanFile(context, arrayOf(out.toString()), null) { _, _ -> }
    }

    class DeleteRequest {
        val startSystemDialog: IntentSender?
        val continueDelete: (() -> Boolean)?

        constructor(startSystemDialog: IntentSender) {
            this.startSystemDialog = startSystemDialog
            this.continueDelete = null
        }

        constructor(continueDelete: (() -> Boolean)) {
            this.startSystemDialog = null
            this.continueDelete = continueDelete
        }
    }
}