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

import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.manipulator.ItemManipulator

/**
 * [PlaylistAdapter] is an adapter for displaying artists.
 */
class PlaylistAdapter(
    fragment: Fragment,
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
) {
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
        popupMenu.menu.iterator().forEach {
            it.isVisible = it.itemId == R.id.play_next || it.itemId == R.id.rename || it.itemId == R.id.delete
        }
        popupMenu.setOnMenuItemClickListener { it1 ->
            when (it1.itemId) {
                R.id.play_next -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItems(
                        mediaController.currentMediaItemIndex + 1,
                        item.songList,
                    )
                    true
                }

                R.id.delete -> {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.delete)
                        .setMessage(context.getString(R.string.delete_really, item.title))
                        .setPositiveButton(R.string.yes) { _, _ ->
                            ioScope.launch {
                                try {
                                    ItemManipulator.deletePlaylist(context, item.path!!)
                                } catch (e: Exception) {
                                    Log.e("PlaylistAdapter", Log.getStackTraceString(e))
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, R.string.delete_failed_playlist, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        .setNegativeButton(R.string.no) { _, _ -> }
                        .show()
                    true
                }

                R.id.rename -> TODO()

                else -> false
            }
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
                ioScope.launch {
                    ItemManipulator.createPlaylist(context, "test123")
                }
            }
        }
    }
}
