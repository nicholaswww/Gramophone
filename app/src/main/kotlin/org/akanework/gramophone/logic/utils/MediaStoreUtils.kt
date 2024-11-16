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

package org.akanework.gramophone.logic.utils

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.LibraryViewModel
import uk.akane.libphonograph.reader.Reader
import uk.akane.libphonograph.reader.ReaderResult
import uk.akane.libphonograph.reader.SimpleReader
import uk.akane.libphonograph.reader.SimpleReaderResult

/**
 * [MediaStoreUtils] contains all the methods for reading
 * from mediaStore.
 */
object MediaStoreUtils {

    @Parcelize
    data class Lyric(
        val timeStamp: Long? = null,
        val content: String = "",
        var isTranslation: Boolean = false
    ) : Parcelable

    /**
     * [getAllSongs] gets all of your songs from your local disk.
     *
     * @param context
     * @return
     */
    @OptIn(UnstableApi::class)
    private fun getAllSongs(context: Context): SimpleReaderResult {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val limitValue = if (BuildConfig.DISABLE_MEDIA_STORE_FILTER) 0 else prefs.getInt(
            "mediastore_filter",
            context.resources.getInteger(R.integer.filter_default_sec)
        )
        val folderFilter = prefs.getStringSet("folderFilter", setOf()) ?: setOf()
        return SimpleReader.readFromMediaStore(
            context,
            minSongLengthSeconds = limitValue.toLong(),
            blackListSet = folderFilter,
            shouldUseEnhancedCoverReading = null
        )
    }

    fun updateLibraryWithInCoroutine(
        libraryViewModel: LibraryViewModel,
        context: Context,
        then: (() -> Unit)?
    ) {
        val pairObject = getAllSongs(context)
        CoroutineScope(Dispatchers.Main).launch {
            libraryViewModel.mediaItemList.value = pairObject.songList
            libraryViewModel.albumItemList.value = pairObject.albumList
            libraryViewModel.artistItemList.value = pairObject.artistList
            libraryViewModel.albumArtistItemList.value = pairObject.albumArtistList
            libraryViewModel.genreItemList.value = pairObject.genreList
            libraryViewModel.dateItemList.value = pairObject.dateList
            libraryViewModel.playlistList.value = pairObject.playlistList
            libraryViewModel.folderStructure.value = pairObject.folderStructure
            libraryViewModel.shallowFolderStructure.value = pairObject.shallowFolder
            libraryViewModel.allFolderSet.value = pairObject.folders
            then?.let { it() }
        }
    }

}
