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

import android.net.Uri
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.Flow
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import uk.akane.libphonograph.items.Album

class AlbumAdapter(
    fragment: Fragment,
    liveData: Flow<List<Album>> = (fragment.requireActivity() as MainActivity).reader.albumListFlow,
    ownsView: Boolean = true,
    isSubFragment: Boolean = false,
    fallbackSpans: Int = 1
) : BaseAdapter<Album>
    (
    fragment,
    liveData = liveData,
    sortHelper = StoreAlbumHelper(),
    naturalOrderHelper = null,
    initialSortType = Sorter.Type.ByTitleAscending,
    pluralStr = R.plurals.albums,
    ownsView = ownsView,
    defaultLayoutType = LayoutType.GRID,
    isSubFragment = isSubFragment,
    fallbackSpans = fallbackSpans
) {

    override fun virtualTitleOf(item: Album): String {
        return context.getString(R.string.unknown_album)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)
        if (layoutType == LayoutType.GRID) {
            val item = list[position]
            holder.itemView.setOnLongClickListener {
                val popupMenu = PopupMenu(it.context, it)
                onMenu(item, popupMenu)
                popupMenu.show()
                true
            }
        }
    }

    override fun onClick(item: Album) {
        mainActivity.startFragment(GeneralSubFragment()) {
            putInt("Position", item.let {
                if (ownsView) toRawPos(it) else {
                    mainActivity.reader.albumListFlow.replayCache.last().indexOf(it)
                }
            })
            putInt("Item", R.id.album)
        }
    }

    override fun onMenu(item: Album, popupMenu: PopupMenu) {
        popupMenu.inflate(R.menu.more_menu_less)

        popupMenu.setOnMenuItemClickListener { it1 ->
            when (it1.itemId) {
                R.id.play_next -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItems(
                        mediaController.currentMediaItemIndex + 1,
                        item.songList,
                    )
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
            }
            true
        }
    }

    class StoreAlbumHelper : StoreItemHelper<Album>(
        setOf(
            Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
            Sorter.Type.ByArtistDescending, Sorter.Type.ByArtistAscending,
            Sorter.Type.BySizeDescending, Sorter.Type.BySizeAscending
        )
    ) {
        override fun getArtist(item: Album): String? {
            return item.albumArtist
        }

        override fun getCover(item: Album): Uri? {
            return item.cover ?: super.getCover(item)
        }
    }
}
