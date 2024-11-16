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

package org.akanework.gramophone.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Artist
import uk.akane.libphonograph.items.Date
import uk.akane.libphonograph.items.FileNode
import uk.akane.libphonograph.items.Genre
import uk.akane.libphonograph.items.Playlist

/**
 * LibraryViewModel:
 *   A ViewModel that contains library information.
 * Used across the application.
 *
 * @author AkaneTan, nift4
 */
class LibraryViewModel : ViewModel() {
    val mediaItemList: MutableLiveData<List<MediaItem>> = MutableLiveData()
    val albumItemList: MutableLiveData<List<Album>> = MutableLiveData()
    val albumArtistItemList: MutableLiveData<List<Artist>> = MutableLiveData()
    val artistItemList: MutableLiveData<List<Artist>> = MutableLiveData()
    val genreItemList: MutableLiveData<List<Genre>> = MutableLiveData()
    val dateItemList: MutableLiveData<List<Date>> = MutableLiveData()
    val playlistList: MutableLiveData<List<Playlist>> = MutableLiveData()
    val folderStructure: MutableLiveData<FileNode> = MutableLiveData()
    val shallowFolderStructure: MutableLiveData<FileNode> = MutableLiveData()
    val allFolderSet: MutableLiveData<Set<String>> = MutableLiveData()
}
