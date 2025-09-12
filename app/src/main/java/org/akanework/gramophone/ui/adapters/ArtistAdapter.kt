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

import android.content.SharedPreferences
import android.view.MenuItem
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.ArtistSubFragment
import uk.akane.libphonograph.items.Artist

/**
 * [ArtistAdapter] is an adapter for displaying artists.
 */
class ArtistAdapter(
    fragment: Fragment,
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(fragment.requireContext().applicationContext),
    var isAlbumArtist: Boolean = prefs.getBooleanStrict("isDisplayingAlbumArtist", false)
) : BaseAdapter<Artist>
    (
    fragment,
    liveData = (fragment.requireActivity() as MainActivity).let { if (isAlbumArtist)
        it.reader.albumArtistListFlow else it.reader.artistListFlow },
    sortHelper = StoreArtistHelper,
    naturalOrderHelper = null,
    initialSortType = Sorter.Type.ByTitleAscending,
    pluralStr = R.plurals.artists,
    defaultLayoutType = LayoutType.LIST
) {

    override fun virtualTitleOf(item: Artist): String {
        return context.getString(R.string.unknown_artist)
    }

    override val defaultCover = R.drawable.ic_default_cover_artist

    override fun onClick(item: Artist) {
        mainActivity.startFragment(ArtistSubFragment()) {
            putString("Id", item.id?.toString())
            putInt(
                "Item",
                if (isAlbumArtist)
                    R.id.album_artist
                else
                    R.id.artist
            )
        }
    }

    override fun onMenu(item: Artist, popupMenu: PopupMenu) {
        popupMenu.inflate(R.menu.more_menu_less)

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

                R.id.add_to_queue -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItems(
                        item.songList,
                    )
                    true
                }

                /*
				R.id.share -> {
					val builder = ShareCompat.IntentBuilder(mainActivity)
					val mimeTypes = mutableSetOf<String>()
					builder.addStream(viewModel.fileUriList.value?.get(songList[holder.bindingAdapterPosition].mediaId.toLong())!!)
					mimeTypes.add(viewModel.mimeTypeList.value?.get(songList[holder.bindingAdapterPosition].mediaId.toLong())!!)
					builder.setType(mimeTypes.singleOrNull() ?: "audio/*").startChooser()
				 } */
				 */
                else -> false
            }
        }
    }

    override fun createDecorAdapter(): BaseDecorAdapter<out BaseAdapter<Artist>> {
        return ArtistDecorAdapter(this)
    }

    private class ArtistDecorAdapter(
        artistAdapter: ArtistAdapter
    ) : BaseDecorAdapter<ArtistAdapter>(artistAdapter, R.plurals.artists) {

        override fun onSortButtonPressed(popupMenu: PopupMenu) {
            popupMenu.menu.findItem(R.id.album_artist_checkbox).isVisible = true
            popupMenu.menu.findItem(R.id.album_artist_checkbox).isChecked = adapter.isAlbumArtist
        }

        override fun onExtraMenuButtonPressed(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.album_artist_checkbox -> {
                    menuItem.isChecked = !menuItem.isChecked
                    adapter.isAlbumArtist = menuItem.isChecked

                    adapter.prefs.edit {
                        putBoolean(
                            "isDisplayingAlbumArtist",
                            adapter.isAlbumArtist
                        )
                    }
                    adapter.liveDataAgent.value =
                        if (adapter.isAlbumArtist) adapter.mainActivity.reader.albumArtistListFlow else
                            adapter.mainActivity.reader.artistListFlow
                    true
                }

                else -> false
            }
        }
    }

    object StoreArtistHelper : StoreItemHelper<Artist>(
        setOf(
            Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
            Sorter.Type.BySizeDescending, Sorter.Type.BySizeAscending,
            Sorter.Type.ByAlbumSizeAscending, Sorter.Type.ByAlbumSizeDescending
        )
    ) {
        override fun getAlbumSize(item: Artist): Int {
            return item.albumList.size
        }
    }
}