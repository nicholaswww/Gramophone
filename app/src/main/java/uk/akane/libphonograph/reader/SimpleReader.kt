package uk.akane.libphonograph.reader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uk.akane.libphonograph.dynamicitem.RecentlyAdded

object SimpleReader {
    fun readFromMediaStore(
        context: Context,
        minSongLengthSeconds: Long = 0,
        blackListSet: Set<String> = setOf(),
        shouldUseEnhancedCoverReading: Boolean? = false, // null means load if permission is granted
        recentlyAddedFilterSecond: Long? = 1_209_600, // null means don't generate recently added
        shouldIncludeExtraFormat: Boolean = true,
        coverStubUri: String? = null
    ): SimpleReaderResult {
        val (playlists, foundPlaylistContent) = Reader.fetchPlaylists(context)
        val result = runBlocking {
            withContext(Dispatchers.Default) {
                Reader.readFromMediaStore(
                    context, minSongLengthSeconds, blackListSet,
                    shouldUseEnhancedCoverReading, shouldIncludeExtraFormat,
                    shouldLoadIdMap = foundPlaylistContent, coverStubUri = coverStubUri
                )
            }
        }
        // We can null assert because we never pass shouldLoad*=false into Reader
        return SimpleReaderResult(
            result.songList,
            result.albumList!!,
            result.albumArtistList!!,
            result.artistList!!,
            result.genreList!!,
            result.dateList!!,
            playlists.map { it.toPlaylist(result.idMap, result.pathMap) }.let {
                if (recentlyAddedFilterSecond != null)
                    it + RecentlyAdded(
                        (System.currentTimeMillis() / 1000L) - recentlyAddedFilterSecond,
                        result.songList
                    )
                else it
            },
            result.folderStructure!!,
            result.shallowFolder!!,
            result.folders!!
        )
    }
}