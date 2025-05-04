package uk.akane.libphonograph.items

import androidx.media3.common.MediaItem
import java.io.File

open class Playlist protected constructor(
    override val id: Long?,
    override val title: String?,
    val path: File?,
    val dateAdded: Long?,
    val dateModified: Long?,
    val hasGaps: Boolean,
) : Item {
    private var _songList: List<MediaItem>? = null
    constructor(id: Long?, title: String?, path: File?, dateAdded: Long?, dateModified: Long?,
                hasGaps: Boolean, songList: List<MediaItem>) : this(id, title, path, dateAdded, dateModified, hasGaps) {
        _songList = songList
    }
    override val songList: List<MediaItem>
        get() = _songList ?: throw IllegalStateException("code bug: Playlist subclass used " +
                "protected constructor but did not override songList")

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + songList.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Playlist

        if (id != other.id) return false
        if (title != other.title) return false
        if (songList != other.songList) return false

        return true
    }
}

internal data class RawPlaylist(
    val id: Long?,
    val title: String?,
    val path: File?,
    val dateAdded: Long?,
    val dateModified: Long?,
    val songList: List<Long?>
) {
    // idMap may be null if and only if all playlists are empty
    fun toPlaylist(idMap: Map<Long, MediaItem>?): Playlist {
        val result = songList.mapNotNull { value ->
            value?.let { idMap!![value] }
            // if we have an id and it's not in the map, something's weird. but it's not a crash-worthy offense
        }
        return Playlist(id, title, path, dateAdded, dateModified, result.size != songList.size, result)
    }
}