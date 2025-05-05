package uk.akane.libphonograph.reader

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.akanework.gramophone.logic.hasAudioPermission
import org.akanework.gramophone.logic.utils.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.akanework.gramophone.logic.utils.conflateAndBlockWhenPaused
import org.akanework.gramophone.logic.utils.repeatUntilDoneWhenUnpaused
import uk.akane.libphonograph.contentObserverVersioningFlow
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Artist
import uk.akane.libphonograph.items.Date
import uk.akane.libphonograph.items.FileNode
import uk.akane.libphonograph.items.Genre

/**
 * SimpleReader reimplementation using flows with focus on efficiency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowReader(
    context: Context,
    minSongLengthSecondsFlow: SharedFlow<Long>,
    blackListSetFlow: SharedFlow<Set<String>>,
    shouldUseEnhancedCoverReadingFlow: SharedFlow<Boolean?>, // null means load if permission is granted
    recentlyAddedFilterSecondFlow: SharedFlow<Long?>, // null means don't generate recently added
    shouldIncludeExtraFormatFlow: SharedFlow<Boolean>,
    coverStubUri: String? = null
) {
    // IMPORTANT: Do not use distinctUntilChanged() or StateFlow here because equals() on thousands
    // of MediaItems is very, very expensive!
    private var awaitingRefresh = false
    var hadFirstRefresh = true
        private set
    private val scope = CoroutineScope(Dispatchers.IO)
    private val finishRefreshTrigger = MutableSharedFlow<Unit>(replay = 0)
    private val manualRefreshTrigger = MutableSharedFlow<Unit>(replay = 1)
    init {
        manualRefreshTrigger.tryEmit(Unit)
    }
    // Start observing as soon as class gets instantiated. ContentObservers are cheap, and more
    // importantly, this allows us to skip the expensive Reader call if nothing changed while we
    // were inactive - that's the most common case!
    private val rawPlaylistVersionFlow = contentObserverVersioningFlow(
        context, scope,
        @Suppress("deprecation") MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, true
    ).shareIn(scope, Eagerly, replay = 1)
    private val mediaVersionFlow = contentObserverVersioningFlow(
        context, scope, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true
    ).shareIn(scope, Eagerly, replay = 1)
    // These expensive Reader calls are only done if we have someone (UI) observing the result AND
    // something changed. The PauseableFlows mechanism allows us to skip any unnecessary work.
    private val rawPlaylistFlow = rawPlaylistVersionFlow
            .conflateAndBlockWhenPaused(true)
            .flatMapLatest {
                manualRefreshTrigger.mapLatest { _ ->
                    if (context.hasAudioPermission())
                        Reader.fetchPlaylists(context).first
                    else emptyList()
                }
            }
    private val readerFlow: Flow<ReaderResult> =
        shouldIncludeExtraFormatFlow.distinctUntilChanged().flatMapLatest { shouldIncludeExtraFormat ->
            shouldUseEnhancedCoverReadingFlow.distinctUntilChanged()
                .flatMapLatest { shouldUseEnhancedCoverReading ->
                    minSongLengthSecondsFlow.distinctUntilChanged().flatMapLatest { minSongLengthSeconds ->
                        blackListSetFlow.distinctUntilChanged().flatMapLatest { blackListSet ->
                            mediaVersionFlow
                                .conflateAndBlockWhenPaused(true)
                                .flatMapLatest {
                                    // manual refresh may for whatever reason run in background
                                    // but all others shouldn't trigger background runs
                                    manualRefreshTrigger.mapLatest { _ ->
                                        repeatUntilDoneWhenUnpaused(true) {
                                            // TODO repeatUntilDoneWhenUnpaused makes no sense with non-cancelable
                                            //  function, make it cancelable
                                            var done = false
                                            try {
                                                val ret = if (context.hasAudioPermission())
                                                    Reader.readFromMediaStore(
                                                        context,
                                                        minSongLengthSeconds,
                                                        blackListSet,
                                                        shouldUseEnhancedCoverReading,
                                                        shouldIncludeExtraFormat,
                                                        coverStubUri = coverStubUri
                                                    )
                                                else ReaderResult.emptyReaderResult()
                                                yield()
                                                done = true
                                                ret
                                            } finally {
                                                if (!done) Log.e("hi", "cancel!!! reader")
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
        }.onEach {
            finishRefreshTrigger.emit(Unit)
            awaitingRefresh = true
            hadFirstRefresh = true
        }.sharePauseableIn(scope, WhileSubscribed(), replay = 1) // TODO 20000
    val idMapFlow: Flow<Map<Long, MediaItem>> = readerFlow.map { it.idMap!! }
    val songListFlow: Flow<List<MediaItem>> = readerFlow.map { it.songList }
    private val recentlyAddedFlow = recentlyAddedFilterSecondFlow.distinctUntilChanged()
        .combine(songListFlow) { recentlyAddedFilterSecond, songList ->
            if (recentlyAddedFilterSecond != null)
                RecentlyAdded(
                    (System.currentTimeMillis() / 1000L) - recentlyAddedFilterSecond,
                    songList
                )
            else
                null
        }
    private val mappedPlaylistsFlow = idMapFlow.combine(rawPlaylistFlow) { idMap, rawPlaylists ->
        rawPlaylists.map { it.toPlaylist(idMap) }
    }
    val albumListFlow: Flow<List<Album>> = readerFlow.map { it.albumList!! }
    val albumArtistListFlow: Flow<List<Artist>> = readerFlow.map { it.albumArtistList!! }
    val artistListFlow: Flow<List<Artist>> = readerFlow.map { it.artistList!! }
    val genreListFlow: Flow<List<Genre>> = readerFlow.map { it.genreList!! }
    val dateListFlow: Flow<List<Date>> = readerFlow.map { it.dateList!! }
    val playlistListFlow = mappedPlaylistsFlow.combine(recentlyAddedFlow) { mappedPlaylists, recentlyAdded ->
        if (recentlyAdded != null) mappedPlaylists + recentlyAdded else mappedPlaylists
    }.sharePauseableIn(scope, WhileSubscribed(), replay = 1) // TODO 20000
    val folderStructureFlow: Flow<FileNode> = readerFlow.map { it.folderStructure!! }
    val shallowFolderFlow: Flow<FileNode> = readerFlow.map { it.shallowFolder!! }
    val foldersFlow: Flow<Set<String>> = readerFlow.map { it.folders!! }

    /**
     * If the library hasn't been loaded yet, forces a load of the library. Otherwise forces a
     * manual refresh of the library. Suspends until new data is available.
     */
    suspend fun refresh() {
        coroutineScope {
            if (!awaitingRefresh) {
                // The playlist flow uses pull principle, and causes readerFlow to refresh, so
                // getting a value here means all data is up to date
                playlistListFlow.first()
                return@coroutineScope
            }
            val waiter = launch {
                finishRefreshTrigger.first()
            }
            manualRefreshTrigger.emit(Unit)
            waiter.join()
        }
    }
}