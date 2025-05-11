package org.akanework.gramophone.logic.utils.flows

import android.content.ContentUris
import android.net.Uri
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import uk.akane.libphonograph.Constants
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId
import uk.akane.libphonograph.toUriCompat
import uk.akane.libphonograph.utils.MiscUtils
import uk.akane.libphonograph.utils.MiscUtils.findBestCover
import java.io.File
import kotlin.math.abs
import kotlin.math.min

sealed class IncrementalList<T>(val after: List<T>) {
    class Begin<T>(after: List<T>) : IncrementalList<T>(after)
    class Insert<T>(val pos: Int, val count: Int, after: List<T>) : IncrementalList<T>(after)
    class Remove<T>(val pos: Int, val count: Int, after: List<T>) : IncrementalList<T>(after)
    class Move<T>(val pos: Int, val count: Int, val outPos: Int, after: List<T>) : IncrementalList<T>(after)
    class Update<T>(val pos: Int, val count: Int, after: List<T>) : IncrementalList<T>(after)
}

sealed class IncrementalMap<T, R>(val after: Map<T, R>) {
    class Begin<T, R>(after: Map<T, R>) : IncrementalMap<T, R>(after)
    class Insert<T, R>(val key: T, after: Map<T, R>) : IncrementalMap<T, R>(after)
    class Remove<T, R>(val key: T, after: Map<T, R>) : IncrementalMap<T, R>(after)
    class Move<T, R>(val key: T, val outKey: T, after: Map<T, R>) : IncrementalMap<T, R>(after)
    class Update<T, R>(val key: T, after: Map<T, R>) : IncrementalMap<T, R>(after)
}

inline fun <T, R> Flow<IncrementalList<T>>.flatMapIncremental(
    crossinline predicate: (T) -> List<R>
): Flow<IncrementalList<R>> = flow {
    var last: List<List<R>>? = null
    var lastFlat: List<R>? = null
    collect { command ->
        var new: List<List<R>>
        var newFlat: List<R>? = null
        when {
            command is IncrementalList.Begin || last == null -> {
                new = command.after.map(predicate)
                newFlat = new.flatMap { it }
                emit(IncrementalList.Begin(newFlat))
            }
            command is IncrementalList.Insert -> {
                new = ArrayList(last!!)
                var totalSize = 0
                for (i in command.pos..<command.pos+command.count) {
                    val item = predicate(command.after[i])
                    totalSize += item.size
                    new.add(i, item)
                }
                if (totalSize > 0) {
                    var totalStart = 0
                    for (i in 0..<command.pos) {
                        totalStart += new[i].size
                    }
                    newFlat = new.flatMap { it }
                    emit(IncrementalList.Insert(totalSize, totalStart, newFlat))
                }
            }
            command is IncrementalList.Move -> {
                new = ArrayList(last!!)
                var totalSize = 0
                for (i in command.pos..<command.pos+command.count) {
                    totalSize += new.removeAt(i).size
                }
                for (i in command.outPos..<command.outPos+command.count) {
                    new.add(i, last!![i - command.outPos + command.pos])
                }
                if (totalSize > 0) {
                    var totalStart = 0
                    for (i in 0..<command.pos) {
                        totalStart += last!![i].size
                    }
                    var totalOutStart = 0
                    for (i in 0..<command.outPos) {
                        totalOutStart += new[i].size
                    }
                    newFlat = new.flatMap { it }
                    emit(IncrementalList.Move(totalStart, totalSize, totalOutStart, newFlat))
                }
            }
            command is IncrementalList.Remove -> {
                new = ArrayList(last!!)
                var totalSize = 0
                for (i in command.pos..<command.pos+command.count) {
                    totalSize += new.removeAt(i).size
                }
                if (totalSize > 0) {
                    var totalStart = 0
                    for (i in 0..<command.pos) {
                        totalStart += new[i].size
                    }
                    newFlat = new.flatMap { it }
                    emit(IncrementalList.Remove(totalSize, totalStart, newFlat))
                }
            }
            command is IncrementalList.Update -> {
                new = ArrayList(last!!)
                var removed = 0
                var added = 0
                for (i in command.pos..<command.pos+command.count) {
                    removed += new[i].size
                    val item = predicate(command.after[i])
                    added += item.size
                    new[i] = item
                }
                if (removed != 0 || added != 0) {
                    var baseStart = 0
                    for (i in 0..<command.pos) {
                        baseStart += new[i].size
                    }
                    val baseSize = min(added, removed)
                    val offsetStart = baseStart + baseSize
                    var offsetCount = abs(added - removed)
                    newFlat = new.flatMap { it }
                    // in insert/remove cases we technically spoiler updates to the list but it doesn't matter
                    if (removed > added) {
                        emit(IncrementalList.Remove(offsetStart, offsetCount, newFlat))
                    } else if (removed < added) {
                        emit(IncrementalList.Insert(offsetStart, offsetCount, newFlat))
                    }
                    if (removed != 0 && added != 0) {
                        emit(IncrementalList.Update(baseStart, baseSize, newFlat))
                    }
                }
            }
            else -> throw IllegalArgumentException("code bug, IncrementalCommand case exhausted")
        }
        last = new
        lastFlat = newFlat ?: lastFlat
    }
}

inline fun <T> Flow<IncrementalList<T>>.filterIncremental(
    crossinline predicate: (T) -> Boolean
): Flow<IncrementalList<T>> = flatMapIncremental {
    if (predicate(it)) listOf(it) else emptyList()
}

/*
   Hand-"optimized" version of:
     inline fun <T, R> Flow<IncrementalCommand<T>>.mapIncremental(
         crossinline predicate: (T) -> R
     ): Flow<IncrementalCommand<R>> = flatMapIncremental {
         listOf(predicate(it))
     }
 */
inline fun <T, R> Flow<IncrementalList<T>>.mapIncremental(
    crossinline predicate: (T) -> R
): Flow<IncrementalList<R>> = flow {
    var last: List<R>? = null
    collect { command ->
        var new: List<R>
        when {
            command is IncrementalList.Begin || last == null -> {
                new = command.after.map(predicate)
                emit(IncrementalList.Begin(new))
            }
            command is IncrementalList.Insert -> {
                new = ArrayList(last!!)
                for (i in command.pos..<command.pos+command.count) {
                    new.add(i, predicate(command.after[i]))
                }
                emit(IncrementalList.Insert(command.pos, command.count, new))
            }
            command is IncrementalList.Move -> {
                new = ArrayList(last!!)
                for (i in command.pos..<command.pos+command.count) {
                    new.removeAt(i)
                }
                for (i in command.outPos..<command.outPos+command.count) {
                    new.add(i, last!![i - command.outPos + command.pos])
                }
                emit(IncrementalList.Move(command.pos, command.count, command.outPos, new))
            }
            command is IncrementalList.Remove -> {
                new = ArrayList(last!!)
                for (i in command.pos..<command.pos+command.count) {
                    new.removeAt(i)
                }
                emit(IncrementalList.Remove(command.pos, command.count, new))
            }
            command is IncrementalList.Update -> {
                new = ArrayList(last!!)
                for (i in command.pos..<command.pos+command.count) {
                    new[i] = predicate(command.after[i])
                }
                emit(IncrementalList.Update(command.pos, command.count, new))
            }
            else -> throw IllegalArgumentException("code bug, IncrementalCommand case exhausted")
        }
        last = new
    }
}

inline fun <T, R> Flow<IncrementalList<T>>.groupByIncremental(
    crossinline key: (T) -> R
): Flow<IncrementalMap<R, IncrementalList<T>>> = flow {
    /*
    var last: List<List<R>>? = null
    var lastFlat: List<R>? = null
    collect { command ->
        var new: List<R>
        var newFlat: List<R>? = null
        when {
            command is IncrementalList.Begin || last == null -> {
                new = command.after.map(predicate)
                newFlat = new.flatMap { it }
                emit(IncrementalList.Begin(newFlat))
            }
            command is IncrementalList.Insert -> {
                new = ArrayList(last!!)
                var totalSize = 0
                for (i in command.pos..<command.pos+command.count) {
                    val item = predicate(command.after[i])
                    totalSize += item.size
                    new.add(i, item)
                }
                if (totalSize > 0) {
                    var totalStart = 0
                    for (i in 0..<command.pos) {
                        totalStart += new[i].size
                    }
                    newFlat = new.flatMap { it }
                    emit(IncrementalList.Insert(totalSize, totalStart, newFlat))
                }
            }
            command is IncrementalList.Move -> {
                new = ArrayList(last!!)
                var totalSize = 0
                for (i in command.pos..<command.pos+command.count) {
                    totalSize += new.removeAt(i).size
                }
                for (i in command.outPos..<command.outPos+command.count) {
                    new.add(i, last!![i - command.outPos + command.pos])
                }
                if (totalSize > 0) {
                    var totalStart = 0
                    for (i in 0..<command.pos) {
                        totalStart += last!![i].size
                    }
                    var totalOutStart = 0
                    for (i in 0..<command.outPos) {
                        totalOutStart += new[i].size
                    }
                    newFlat = new.flatMap { it }
                    emit(IncrementalList.Move(totalStart, totalSize, totalOutStart, newFlat))
                }
            }
            command is IncrementalList.Remove -> {
                new = ArrayList(last!!)
                var totalSize = 0
                for (i in command.pos..<command.pos+command.count) {
                    totalSize += new.removeAt(i).size
                }
                if (totalSize > 0) {
                    var totalStart = 0
                    for (i in 0..<command.pos) {
                        totalStart += new[i].size
                    }
                    newFlat = new.flatMap { it }
                    emit(IncrementalList.Remove(totalSize, totalStart, newFlat))
                }
            }
            command is IncrementalList.Update -> {
                new = ArrayList(last!!)
                var removed = 0
                var added = 0
                for (i in command.pos..<command.pos+command.count) {
                    removed += new[i].size
                    val item = predicate(command.after[i])
                    added += item.size
                    new[i] = item
                }
                if (removed != 0 || added != 0) {
                    var baseStart = 0
                    for (i in 0..<command.pos) {
                        baseStart += new[i].size
                    }
                    val baseSize = min(added, removed)
                    val offsetStart = baseStart + baseSize
                    var offsetCount = abs(added - removed)
                    newFlat = new.flatMap { it }
                    if (removed > added) {
                        val dummy = ArrayList(lastFlat!!)
                        for (i in offsetStart..<offsetStart+offsetCount) {
                            dummy.removeAt(i)
                        }
                        emit(IncrementalList.Remove(offsetStart, offsetCount, dummy))
                    } else if (removed < added) {
                        val dummy = ArrayList(lastFlat!!)
                        for (i in offsetStart..<offsetStart+offsetCount) {
                            dummy.add(i, newFlat[i])
                        }
                        emit(IncrementalList.Insert(offsetStart, offsetCount, dummy))
                    }
                    if (removed != 0 && added != 0) {
                        emit(IncrementalList.Update(baseStart, baseSize, newFlat))
                    }
                }
            }
            else -> throw IllegalArgumentException("code bug, IncrementalCommand case exhausted")
        }
        last = new
        lastFlat = newFlat ?: lastFlat
    } TODO*/
}

inline fun <T, R> Flow<IncrementalMap<T, R>>.filterIncremental(
    crossinline predicate: (T, R) -> Boolean
): Flow<IncrementalMap<T, R>> = flow {

}

inline fun <T, R, S> Flow<IncrementalMap<T, R>>.mapProducerFlowLatestIncremental(
    crossinline predicate: (T, R) -> Flow<S>
): Flow<IncrementalMap<T, S>> = flow {

}

inline fun <T, R> Flow<IncrementalMap<T, R>>.toIncrementalList(
    crossinline comparator: (T, T) -> Int
): Flow<IncrementalList<R>> = flow {

}

inline fun <T, R> Flow<IncrementalMap<T, IncrementalList<R>>>.forKey(
    key: T
): Flow<IncrementalList<R>> = flow {

}

// TODO something that combines said groups back to one "Album" or "Artist"? or should we give up nice objects and
//  expose new flows from library surface to consumers for these subgroups? maybe we don't need to compose
//  increments if we do that?


// Basic pattern:
data class Album2(
    val id: Long?,
    val title: String?,
    val albumArtist: String?,
    val albumArtistId: Long?,
    val albumYear: Int?, // Last year
    val cover: Uri?,
    val songCount: Int,
)
data class Artist2(
    val id: Long?,
    val name: String?,
    val cover: Uri?,
    val songCount: Int,
    val albumCount: Int,
)

// TODO sharePauseableIn should propagate replay cache invalidation to downstream as well
private data class ReaderResult2(
    val songList: IncrementalList<MediaItem>,
    val canonicalArtistIdMap: Map<String, Long>,
)
private var useEnhancedCoverReading = true
private var coverStubUri: String? = "gramophoneCover"//TODO
private val scope = CoroutineScope(Dispatchers.Default)
private val readerFlow: SharedFlow<ReaderResult2> = TODO()
    .provideReplayCacheInvalidationManager<ReaderResult2>(copyDownstream = Invalidation.Optional)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)
val songFlow: Flow<IncrementalList<MediaItem>> = readerFlow.map { it.songList }

private val allowedFoldersForCoversFlow: SharedFlow<Set<String>> = songFlow
    .groupByIncremental { it.getFile()?.absolutePath }
    .filterIncremental { folder, songs ->
        if (folder != null) {
            val firstAlbum = songs.after.first().mediaMetadata.albumId
            songs.after.find { it.mediaMetadata.albumId != firstAlbum } == null
        } else false
    }
    .map { @Suppress("UNCHECKED_CAST") (it.after.keys as Set<String>) }
    .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

private val rawAlbumsFlow: Flow<IncrementalMap<Long?, IncrementalList<MediaItem>>> = songFlow
    .groupByIncremental { it.mediaMetadata.albumId }
    .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)
val albumsFlow: SharedFlow<IncrementalList<Album2>> = rawAlbumsFlow
    .mapProducerFlowLatestIncremental { albumId, songs ->
        val songList = songs.after
        val title = songList.first().mediaMetadata.albumTitle?.toString()
        val year = songList.mapNotNull { it.mediaMetadata.releaseYear }.maxOrNull()
        val artist = MiscUtils.findBestAlbumArtist(songList)
        val songCount = songList.size
        val fallbackCover = songList.first().mediaMetadata.artworkUri
        val albumArtFlow = if (useEnhancedCoverReading) {
            val firstFolder = songList.first().getFile()?.parent
            val eligibleForFolderAlbumArt = firstFolder != null && albumId != null &&
                    songList.find { it.getFile()?.parent != firstFolder } == null
            if (!eligibleForFolderAlbumArt) flowOf(fallbackCover)
            else allowedFoldersForCoversFlow.map { it.contains(firstFolder) }.distinctUntilChanged().map {
                if (it) {
                    if (coverStubUri != null)
                        Uri.Builder().scheme(coverStubUri)
                            .authority(albumId.toString()).path(firstFolder).build()
                    else
                        findBestCover(File(firstFolder))?.toUriCompat()
                } else fallbackCover
            }
        } else flowOf(if (albumId != null)
            ContentUris.withAppendedId(Constants.baseAlbumCoverUri, albumId) else fallbackCover)
        val artistIdFlow = if (artist?.second != null) flowOf(artist.second) else if (artist != null)
            readerFlow.map { it.canonicalArtistIdMap[artist.first] }.distinctUntilChanged() else flowOf(null)
        albumArtFlow.combine(artistIdFlow) { cover, artistId ->
            Album2(albumId, title, artist?.first, artistId, year, cover, songCount)
        }
    }
    .toIncrementalList(::compareValues)
    .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

fun getSongsInAlbum(album: Album2): Flow<IncrementalList<MediaItem>> = rawAlbumsFlow
    .forKey(album.id)

private val albumsForArtistFlow: Flow<IncrementalMap<Long?, IncrementalList<Album2>>> = albumsFlow
    .groupByIncremental { it.albumArtistId }
    .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)
fun getAlbumsForArtist(artist: Artist2): Flow<IncrementalList<Album2>> = albumsForArtistFlow.forKey(artist.id)

private val rawArtistFlow: Flow<IncrementalMap<Long?, IncrementalList<MediaItem>>> = songFlow
    .groupByIncremental { it.mediaMetadata.artistId }
    .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)
val artistFlow: SharedFlow<IncrementalList<Artist2>> = rawArtistFlow
    .mapProducerFlowLatestIncremental { artistId, songs -> // TODO missing artists that only have albums?
        val songList = songs.after
        val title = songList.first().mediaMetadata.artist?.toString()
        val cover = songList.first().mediaMetadata.artworkUri
        val songCount = songList.size
        albumsForArtistFlow
            .forKey(artistId)
            .map { it.after.size }
            .distinctUntilChanged()
            .map { albumCount ->
                Artist2(artistId, title, cover, songCount, albumCount)
            }
    }
    .toIncrementalList(::compareValues)
    .provideReplayCacheInvalidationManager()
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

fun getSongsForArtist(artist: Artist2): Flow<IncrementalList<MediaItem>> = rawArtistFlow.forKey(artist.id)

// TODO make proper album artists (songs sorted by album artist) tab again

// TODO dates

// TODO genres

// TODO folder flat tree

// TODO filesystem tree

// TODO id map

// TODO playlists