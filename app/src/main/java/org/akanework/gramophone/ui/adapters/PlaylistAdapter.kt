/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.adapters

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.DialogCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.iterator
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.AdapterFragment
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.manipulator.ItemManipulator
import java.io.File

/**
 * [PlaylistAdapter] is an adapter for displaying artists.
 */
class PlaylistAdapter(
    fragment: AdapterFragment,
) : BaseAdapter<Playlist>
    (
    fragment,
    liveData = (fragment.requireActivity() as MainActivity).reader.playlistListFlow,
    sortHelper = StoreItemHelper(),
    naturalOrderHelper = null,
    initialSortType = Sorter.Type.ByTitleAscending,
    pluralStr = R.plurals.items,
    ownsView = true,
    defaultLayoutType = LayoutType.LIST
), AdapterFragment.RequestAdapter {
    override val defaultCover = R.drawable.ic_default_cover_playlist
    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun virtualTitleOf(item: Playlist): String {
        return context.getString(
            if (item is RecentlyAdded)
                R.string.recently_added else R.string.unknown_playlist
        )
    }

    override fun onClick(item: Playlist) {
        mainActivity.startFragment(GeneralSubFragment()) {
            putString("Id", item.id?.toString())
            putInt("Item", R.id.playlist)
        }
    }

    override fun onMenu(item: Playlist, popupMenu: PopupMenu) {
        popupMenu.inflate(R.menu.more_menu)
        val canEdit = item.id != null
        popupMenu.menu.iterator().forEach {
            it.isVisible = it.itemId == R.id.play_next
                    || (canEdit && (it.itemId == R.id.rename || it.itemId == R.id.delete))
        }
        popupMenu.setOnMenuItemClickListener { it1 ->
            when (it1.itemId) {
                R.id.play_next -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItems(
                        mediaController.currentMediaItemIndex + 1,
                        item.songList,
                    )
                }

                R.id.delete -> {
                    if (item.id == null) {
                        Toast.makeText(context, R.string.delete_failed_playlist, Toast.LENGTH_LONG).show()
                        return@setOnMenuItemClickListener true
                    }
                    val res = ItemManipulator.deletePlaylist(context, item.id!!)
                    if (res.continueDelete != null) {
                        AlertDialog.Builder(context)
                            .setTitle(R.string.delete)
                            .setMessage(context.getString(R.string.delete_really, item.title))
                            .setPositiveButton(R.string.yes) { _, _ ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    res.continueDelete?.invoke()
                                }
                            }
                            .setNegativeButton(R.string.no) { _, _ -> }
                            .show()
                    } else {
                        mainActivity.intentSender.launch(
                            IntentSenderRequest.Builder(res.startSystemDialog!!).build()
                        )
                    }
                }

                R.id.rename -> {
                    if (item.id == null) {
                        Toast.makeText(context, R.string.rename_failed_playlist, Toast.LENGTH_LONG).show()
                        return@setOnMenuItemClickListener true
                    }
                    playlistNameDialog(context, R.string.rename_playlist, item.title ?: "") { name ->
                        val id = item.id!!
                        val uri = ContentUris.withAppendedId(
                            @Suppress("deprecation") MediaStore.Audio.Playlists.getContentUri("external"), id
                        )
                        val data = Bundle().apply {
                            putLong("Id", id)
                            putString("NewName", name)
                            putString("Path", item.path!!.absolutePath)
                        }
                        if (ItemManipulator.needRequestWrite(context, uri)) {
                            val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                            (fragment as AdapterFragment).startRequest(pendingIntent.intentSender, data)
                        } else {
                            onRequest(Activity.RESULT_OK, data)
                        }
                    }
                }

                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onRequest(resultCode: Int, data: Bundle) {
        if (resultCode == Activity.RESULT_OK) {
            val path = data.getString("Path")!!
            val newName = data.getString("NewName")!!
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    ItemManipulator.renamePlaylist(context, File(path), newName)
                } catch (e: Exception) {
                    Log.e("PlaylistAdapter", Log.getStackTraceString(e))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.rename_failed_playlist, Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, R.string.rename_failed_playlist, Toast.LENGTH_LONG).show()
        }
    }

    override fun createDecorAdapter(): BaseDecorAdapter<out BaseAdapter<Playlist>> {
        return PlaylistDecorAdapter(this)
    }

    private inner class PlaylistDecorAdapter(
        playlistAdapter: PlaylistAdapter
    ) : BaseDecorAdapter<PlaylistAdapter>(playlistAdapter, R.plurals.items, false) {

        override fun onBindViewHolder(
            holder: BaseDecorAdapter<PlaylistAdapter>.ViewHolder,
            position: Int,
            payloads: List<Any?>
        ) {
            super.onBindViewHolder(holder, position, payloads)
            holder.createPlaylist.visibility = View.VISIBLE
            holder.createPlaylist.setOnClickListener { _ ->
                playlistNameDialog(context, R.string.create_playlist, "") { name ->
                    ioScope.launch {
                        try {
                            ItemManipulator.createPlaylist(context, name)
                        } catch (e: Exception) {
                            Log.e("PlaylistAdapter", Log.getStackTraceString(e))
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, R.string.create_failed_playlist, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun playlistNameDialog(context: Context, title: Int, initialValue: String, then: (String) -> Unit) {
            val d = MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(R.layout.dialog_new_playlist)
                .setPositiveButton(android.R.string.ok) { d, _ ->
                    val et = DialogCompat.requireViewById(d as AlertDialog, R.id.editText) as TextInputEditText
                    val name = et.editableText.toString()
                    then(name)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
            val et = DialogCompat.requireViewById(d, R.id.editText) as TextInputEditText
            val b = d.getButton(DialogInterface.BUTTON_POSITIVE)
            et.editableText.append(initialValue)
            b.isEnabled = !initialValue.isBlank()
            et.addTextChangedListener(afterTextChanged = {
                val name = et.editableText.toString()
                b.isEnabled = !name.isBlank()
            })
            et.requestFocus()
            et.post {
                if (ViewCompat.getRootWindowInsets(d.window!!.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == false
                ) {
                    WindowInsetsControllerCompat(d.window!!, et).show(WindowInsetsCompat.Type.ime())
                }
            }
        }
    }
}
