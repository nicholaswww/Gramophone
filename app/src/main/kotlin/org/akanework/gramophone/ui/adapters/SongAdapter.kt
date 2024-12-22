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
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.io.File
import java.util.GregorianCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.NowPlayingDrawable
import org.akanework.gramophone.ui.fragments.ArtistSubFragment
import org.akanework.gramophone.ui.fragments.DetailDialogFragment
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import uk.akane.libphonograph.items.addDate
import uk.akane.libphonograph.items.modifiedDate
import uk.akane.libphonograph.manipulator.ItemManipulator


/**
 * [SongAdapter] is an adapter for displaying songs.
 */
class SongAdapter(
    fragment: Fragment,
    songList: Flow<List<MediaItem>> = (fragment.requireActivity() as MainActivity).reader.songListFlow,
    canSort: Boolean,
    helper: Sorter.NaturalOrderHelper<MediaItem>?,
    ownsView: Boolean,
    isSubFragment: Boolean = false,
    allowDiffUtils: Boolean = false,
    rawOrderExposed: Boolean = !isSubFragment,
    fallbackSpans: Int = 1
) : BaseAdapter<MediaItem>
    (
    fragment,
    liveData = songList,
    sortHelper = MediaItemHelper(),
    naturalOrderHelper = if (canSort) helper else null,
    initialSortType = if (canSort)
        (if (helper != null) Sorter.Type.NaturalOrder else
            (if (rawOrderExposed) Sorter.Type.NativeOrder else Sorter.Type.ByTitleAscending))
    else Sorter.Type.None,
    canSort = canSort,
    pluralStr = R.plurals.songs,
    ownsView = ownsView,
    defaultLayoutType = LayoutType.COMPACT_LIST,
    isSubFragment = isSubFragment,
    rawOrderExposed = rawOrderExposed,
    allowDiffUtils = allowDiffUtils,
    fallbackSpans = fallbackSpans
) {

    fun getSongList() = list.second

    fun getActivity() = mainActivity

    private val mediaControllerViewModel: MediaControllerViewModel by fragment.activityViewModels()
    private var idToPosMap: HashMap<String, Int>? = null
    private var currentMediaItem: String? = null
        set(value) {
            if (field != value) {
                val oldValue = field
                field = value
                if (idToPosMap != null) {
                    val oldPos = idToPosMap!![oldValue]
                    val newPos = idToPosMap!![value]
                    if (oldPos != null) {
                        notifyItemChanged(oldPos, true)
                    }
                    if (newPos != null) {
                        notifyItemChanged(newPos, true)
                    }
                }
            }
        }
    private var currentIsPlaying: Boolean? = null
        set(value) {
            if (field != value) {
                field = value
                if (value != null && currentMediaItem != null) {
                    idToPosMap?.get(currentMediaItem)?.let {
                        notifyItemChanged(it, false)
                    }
                }
            }
        }

    init {
        mediaControllerViewModel.addRecreationalPlayerListener(
            fragment.viewLifecycleOwner.lifecycle
        ) {
            currentMediaItem = it.currentMediaItem?.mediaId
            currentIsPlaying =
                it.playWhenReady && it.playbackState != Player.STATE_ENDED && it.playbackState != Player.STATE_IDLE
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentMediaItem = mediaItem?.mediaId
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    currentIsPlaying =
                        playWhenReady && it.playbackState != Player.STATE_ENDED && it.playbackState != Player.STATE_IDLE
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    currentIsPlaying =
                        it.playWhenReady && playbackState != Player.STATE_ENDED && it.playbackState != Player.STATE_IDLE
                }
            }
        }
    }

    override fun onListUpdated() {
        // TODO run this method on a different thread / in advance
        idToPosMap = hashMapOf()
        list.second.forEachIndexed { i, item -> idToPosMap!![item.mediaId] = i }
    }

    override fun virtualTitleOf(item: MediaItem): String {
        return "null"
    }

    override fun onClick(item: MediaItem) {
        val mediaController = mainActivity.getPlayer()
        mediaController?.apply {
            val songList = getSongList()
            setMediaItems(songList, songList.indexOf(item), C.TIME_UNSET)
            prepare()
            play()
        }
    }

    override fun onMenu(item: MediaItem, popupMenu: PopupMenu) {
        popupMenu.inflate(R.menu.more_menu)

        popupMenu.setOnMenuItemClickListener { it1 ->
            when (it1.itemId) {
                R.id.play_next -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItem(
                        mediaController.currentMediaItemIndex + 1,
                        item,
                    )
                    true
                }

                R.id.album -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        val positionAlbum =
                            mainActivity.reader.albumListFlow.replayCache.lastOrNull()?.indexOfFirst {
                                (it.title == item.mediaMetadata.albumTitle) &&
                                        (it.songList.contains(item))
                            }
                        if (positionAlbum != null) {
                            withContext(Dispatchers.Main) {
                                mainActivity.startFragment(GeneralSubFragment()) {
                                    putInt("Position", positionAlbum)
                                    putInt("Item", R.id.album)
                                }
                            }
                        }
                    }
                    true
                }

                R.id.artist -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        val positionArtist =
                            mainActivity.reader.artistListFlow.replayCache.lastOrNull()?.indexOfFirst {
                                val isMatching =
                                    (it.title == item.mediaMetadata.artist) &&
                                            (it.songList.contains(item))
                                isMatching
                            }
                        if (positionArtist != null) {
                            withContext(Dispatchers.Main) {
                                mainActivity.startFragment(ArtistSubFragment()) {
                                    putInt("Position", positionArtist)
                                    putInt("Item", R.id.artist)
                                }
                            }
                        }
                    }
                    true
                }

                R.id.details -> {
                    val position = mainActivity.reader.songListFlow.replayCache.lastOrNull()?.indexOfFirst {
                        it.mediaId == item.mediaId
                    }
                    mainActivity.startFragment(DetailDialogFragment()) {
                        putInt("Position", position!!)
                    }
                    true
                }

                R.id.delete -> {
                    val res = ItemManipulator.deleteSong(context, item.mediaId.toLong())
                    if (res.continueDelete != null) {
                        AlertDialog.Builder(context)
                            .setTitle(R.string.delete)
                            .setMessage(item.mediaMetadata.title)
                            .setPositiveButton(R.string.yes) { _, _ ->
                                res.continueDelete?.invoke()
                            }
                            .setNegativeButton(R.string.no) { _, _ -> }
                            .show()
                    } else {
                        mainActivity.intentSender.launch(
                            IntentSenderRequest.Builder(res.startSystemDialog!!).build()
                        )
                    }
                    true
                }

                R.id.share -> {
                    val mediaItem = mainActivity.reader.songListFlow.replayCache.lastOrNull()?.find {
                        it.mediaId == item.mediaId
                    } ?: return@setOnMenuItemClickListener true

                    val uri = mediaItem.requestMetadata.mediaUri
                        ?: mediaItem.localConfiguration?.uri
                        ?: return@setOnMenuItemClickListener true

                    val mimeType = mediaItem.localConfiguration?.mimeType ?: "audio/*"

                    try {
                        val contentUri = if (uri.scheme == "file") {
                            FileProvider.getUriForFile(
                                mainActivity,
                                "${mainActivity.packageName}.fileProvider",
                                File(uri.path!!)
                            )
                        } else uri

                        ShareCompat.IntentBuilder(mainActivity)
                            .setType(mimeType)
                            .setStream(contentUri)
                            .setChooserTitle("Share audio file")
                            .startChooser()
                    } catch (e: Exception) {
                        Toast.makeText(
                            mainActivity,
                            "Unable to share: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }

                else -> false
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            if (payloads.none { it is Boolean && it }) {
                holder.nowPlaying.drawable.level = if (currentIsPlaying == true) 1 else 0
                return
            }
            if (currentMediaItem == null || getSongList()[position].mediaId != currentMediaItem) {
                (holder.nowPlaying.drawable as? NowPlayingDrawable?)?.level2Done = Runnable {
                    holder.nowPlaying.visibility = View.GONE
                    holder.nowPlaying.setImageDrawable(null)
                }
                holder.nowPlaying.drawable?.level = 2
                return
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
            if (currentMediaItem == null || getSongList()[position].mediaId != currentMediaItem)
                return
        }
        holder.nowPlaying.setImageDrawable(NowPlayingDrawable()
            .also { it.level = if (currentIsPlaying == true) 1 else 0 })
        holder.nowPlaying.visibility = View.VISIBLE
    }

    class MediaItemHelper(
        types: Set<Sorter.Type> = setOf(
            Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
            Sorter.Type.ByArtistDescending, Sorter.Type.ByArtistAscending,
            Sorter.Type.ByAlbumTitleDescending, Sorter.Type.ByAlbumTitleAscending,
            Sorter.Type.ByAlbumArtistDescending, Sorter.Type.ByAlbumArtistAscending,
            Sorter.Type.ByAddDateDescending, Sorter.Type.ByAddDateAscending,
            Sorter.Type.ByReleaseDateDescending, Sorter.Type.ByReleaseDateAscending,
            Sorter.Type.ByModifiedDateDescending, Sorter.Type.ByModifiedDateAscending,
            Sorter.Type.ByDiscAndTrack
        )
    ) : Sorter.Helper<MediaItem>(types) {
        override fun getId(item: MediaItem): String {
            return item.mediaId
        }

        override fun getTitle(item: MediaItem): String {
            return item.mediaMetadata.title.toString()
        }

        override fun getArtist(item: MediaItem): String? {
            return item.mediaMetadata.artist?.toString()
        }

        override fun getAlbumTitle(item: MediaItem): String {
            return item.mediaMetadata.albumTitle?.toString() ?: ""
        }

        override fun getAlbumArtist(item: MediaItem): String {
            return item.mediaMetadata.albumArtist?.toString() ?: ""
        }

        override fun getCover(item: MediaItem): Uri? {
            return item.mediaMetadata.artworkUri
        }

        override fun getDiscAndTrack(item: MediaItem): Int {
            return (item.mediaMetadata.discNumber ?: 0) * 1000 + (item.mediaMetadata.trackNumber
                ?: 0)
        }

        override fun getAddDate(item: MediaItem): Long {
            return item.mediaMetadata.addDate ?: -1
        }

        override fun getReleaseDate(item: MediaItem): Long {
            if (item.mediaMetadata.releaseYear == null && item.mediaMetadata.releaseMonth == null
                && item.mediaMetadata.releaseDay == null
            ) {
                return GregorianCalendar(
                    (item.mediaMetadata.recordingYear ?: 0) + 1900,
                    (item.mediaMetadata.recordingMonth ?: 1) - 1,
                    item.mediaMetadata.recordingDay ?: 0, 0, 0, 0
                )
                    .timeInMillis
            }
            return GregorianCalendar(
                (item.mediaMetadata.releaseYear ?: 0) + 1900,
                (item.mediaMetadata.releaseMonth ?: 1) - 1,
                item.mediaMetadata.releaseDay ?: 0, 0, 0, 0
            )
                .timeInMillis
        }

        override fun getModifiedDate(item: MediaItem): Long {
            return item.mediaMetadata.modifiedDate ?: -1
        }
    }
}