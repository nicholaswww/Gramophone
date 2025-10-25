package org.akanework.gramophone.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.replaceAllSupport
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.ui.placeholderScaleToFit
import org.akanework.gramophone.logic.utils.convertDurationToTimeStamp
import org.akanework.gramophone.ui.MainActivity
import java.util.LinkedList

// TODO: support listening to externally caused changes to playlist (ie MCT).
class PlaylistQueueSheet(
    context: Context, private val activity: MainActivity
) : BottomSheetDialog(context), Player.Listener {
    private val instance: MediaBrowser?
        get() = activity.getPlayer()
    private val playlistAdapter: PlaylistCardAdapter
    private val touchHelper: ItemTouchHelper
    private val durationView: TextView

    init {
        setContentView(R.layout.playlist_bottom_sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        durationView = findViewById(R.id.duration)!!
        val recyclerView = findViewById<MyRecyclerView>(R.id.recyclerview)!!
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, ic ->
            val i = ic.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            val i2 = ic.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(i.left, 0, i.right, i.bottom)
            return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(ic)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout(),
                    Insets.of(0, i.top, 0, 0)
                )
                .setInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout(),
                    Insets.of(0, i2.top, 0, 0)
                )
                .build()
        }
        playlistAdapter = PlaylistCardAdapter(activity, instance!!)
        val callback = PlaylistCardMoveCallback(playlistAdapter::onRowMoved) {
            pos, _ -> playlistAdapter.removeItem(pos) }
        touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = playlistAdapter
        recyclerView.scrollToPositionWithOffsetCompat(playlistAdapter.playlist.first.indexOfFirst { i ->
            i == (instance?.currentMediaItemIndex ?: 0)
        }, // quick UX hack to show there's more songs above (well, if there is).
            (context.resources.getDimensionPixelOffset(R.dimen.list_height) * 0.5f).toInt())
        recyclerView.fastScroll(null, null)
        findViewById<Button>(R.id.scrollToPlaying)!!.setOnClickListener {
            recyclerView.smoothScrollToPosition(playlistAdapter.playlist.first.indexOfFirst { i ->
                i == (instance?.currentMediaItemIndex ?: 0)
            })
        }
        activity.controllerViewModel.addRecreationalPlayerListener(lifecycle, this) {
            onMediaItemTransition(
                instance?.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            )
            onIsPlayingChanged(instance?.isPlaying ?: false)
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: @Player.MediaItemTransitionReason Int
    ) {
        playlistAdapter.currentMediaItem = mediaItem?.mediaId
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        playlistAdapter.currentIsPlaying = isPlaying
    }

    private inner class PlaylistCardAdapter(
        private val activity: MainActivity,
        private val instance: MediaBrowser
    ) : MyRecyclerView.Adapter<ViewHolder>() {
        var playlist: Pair<MutableList<Int>, MutableList<MediaItem>> = dumpPlaylist()
        private var idToPosMap: HashMap<String, Int>? = null

        init {
            updateList()
        }
        var currentMediaItem: String? = null
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
        var currentIsPlaying: Boolean? = null
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

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ViewHolder =
            ViewHolder(
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.adapter_list_card, parent, false) as ConstraintLayout,
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = playlist.second[playlist.first[holder.bindingAdapterPosition]]
            holder.songName.text = item.mediaMetadata.title
            holder.songArtist.text = activity.getString(R.string.artist_time,
                item.mediaMetadata.durationMs?.convertDurationToTimeStamp(),
                item.mediaMetadata.artist ?: activity.getString(R.string.unknown_artist))
            holder.songCover.load(item.mediaMetadata.artworkUri) {
                placeholderScaleToFit(R.drawable.ic_default_cover)
                crossfade(true)
                error(R.drawable.ic_default_cover)
            }
            holder.closeButton.setOnClickListener { v ->
                ViewCompat.performHapticFeedback(v, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) { // if -1, user clicked remove twice on same item
                    removeItem(pos)
                }
            }
            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) { // if -1, user clicked remove on this item
                    ViewCompat.performHapticFeedback(
                        it,
                        HapticFeedbackConstantsCompat.CONTEXT_CLICK
                    )
                    val instance = activity.getPlayer()
                    instance?.seekToDefaultPosition(playlist.first[pos])
                }
            }
            holder.dragHandle.setOnTouchListener { _, ev ->
                if (ev.action == MotionEvent.ACTION_DOWN)
                    touchHelper.startDrag(holder)
                false
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any?>) {
            if (payloads.isNotEmpty()) {
                if (payloads.none { it is Boolean && it }) {
                    holder.nowPlaying.drawable?.level = if (currentIsPlaying == true) 1 else 0
                    return
                }
                if (currentMediaItem == null || playlist.second[playlist.first[position]].mediaId != currentMediaItem) {
                    (holder.nowPlaying.drawable as? NowPlayingDrawable?)?.level2Done = Runnable {
                        holder.nowPlaying.visibility = View.GONE
                        holder.nowPlaying.setImageDrawable(null)
                    }
                    holder.nowPlaying.drawable?.level = 2
                    return
                }
            } else {
                super.onBindViewHolder(holder, position, payloads)
                if (currentMediaItem == null || playlist.second[playlist.first[position]].mediaId != currentMediaItem)
                    return
            }
            holder.nowPlaying.setImageDrawable(
                NowPlayingDrawable(holder.itemView.context)
                    .also { it.level = if (currentIsPlaying == true) 1 else 0 })
            holder.nowPlaying.visibility = View.VISIBLE
        }

        override fun onViewRecycled(holder: ViewHolder) {
            holder.songCover.dispose()
            holder.closeButton.setOnClickListener(null)
            holder.itemView.setOnClickListener(null)
            holder.dragHandle.setOnClickListener(null)
            (holder.nowPlaying.drawable as? NowPlayingDrawable?)?.level2Done = null
            holder.nowPlaying.setImageDrawable(null)
            holder.nowPlaying.visibility = View.GONE
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int = if (playlist.first.size != playlist.second.size)
            throw IllegalStateException()
        else playlist.first.size

        fun onRowMoved(from: Int, to: Int) {
            val mediaController = activity.getPlayer()
            val from1 = playlist.first.removeAt(from)
            playlist.first.replaceAllSupport { if (it > from1) it - 1 else it }
            val movedItem = playlist.second.removeAt(from1)
            val to1 = if (to > 0) playlist.first[to - 1] + 1 else 0
            playlist.first.replaceAllSupport { if (it >= to1) it + 1 else it }
            playlist.first.add(to, to1)
            playlist.second.add(to1, movedItem)
            mediaController?.moveMediaItem(from1, to1)
            notifyItemMoved(from, to)
        }

        fun removeItem(pos: Int) {
            val instance = activity.getPlayer()
            val idx = playlist.first.removeAt(pos)
            playlist.first.replaceAllSupport { if (it > idx) it - 1 else it }
            instance?.removeMediaItem(idx)
            playlist.second.removeAt(idx)
            notifyItemRemoved(pos)
            updateList()
        }

        private fun dumpPlaylist(): Pair<MutableList<Int>, MutableList<MediaItem>> {
            val items = LinkedList<MediaItem>()
            for (i in 0 until instance.mediaItemCount) {
                items.add(instance.getMediaItemAt(i))
            }
            val indexes = LinkedList<Int>()
            val s = instance.shuffleModeEnabled
            var i = instance.currentTimeline.getFirstWindowIndex(s)
            while (i != C.INDEX_UNSET) {
                indexes.add(i)
                i = instance.currentTimeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, s)
            }
            return Pair(indexes, items)
        }

        private fun updateList() {
            idToPosMap = hashMapOf()
            playlist.second.forEachIndexed { i, item -> idToPosMap!![item.mediaId] = playlist.first.indexOf(i) }
            durationView.text = activity.getString(R.string.duration_queue,
                (playlist.second.sumOf { it.mediaMetadata.durationMs ?: 0L } / 60)
                .convertDurationToTimeStamp())
        }
    }

    private class PlaylistCardMoveCallback(private val move: (Int, Int) -> Unit,
                                           private val swipe: (Int, Int) -> Unit) :
        ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.START or ItemTouchHelper.END) {
        override fun isLongPressDragEnabled(): Boolean {
            return true
        }

        override fun isItemViewSwipeEnabled(): Boolean {
            return true
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val vhBap = viewHolder.bindingAdapterPosition
            val tBap = target.bindingAdapterPosition
            if (vhBap != RecyclerView.NO_POSITION && tBap != RecyclerView.NO_POSITION) {
                move(vhBap, tBap)
                return true
            }
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val vhBap = viewHolder.bindingAdapterPosition
            if (vhBap != RecyclerView.NO_POSITION) {
                swipe(vhBap, direction)
            }
        }
    }

    class ViewHolder(
        view: ConstraintLayout,
    ) : RecyclerView.ViewHolder(view) {
        val songName: TextView = view.findViewById(R.id.title)
        val songArtist: TextView = view.findViewById(R.id.artist)
        val songCover: ImageView = view.findViewById(R.id.cover)
        val nowPlaying: ImageView = view.findViewById(R.id.now_playing)
        val closeButton: MaterialButton = view.findViewById(R.id.more)
        val dragHandle = ImageView(view.context)
        init {
            // don't want the hundreds of other ViewHolders using this layout to inflate view it
            // will never use, and also don't want to diverge layout for 1 view (it was diverged
            // before when I wrote this commit and was completely out of sync after a while). so, do
            // it programmatically.
            dragHandle.id = R.id.dragHandle
            val dp8 = 8.dpToPx(view.context)
            dragHandle.setPaddingRelative(10.dpToPx(view.context), dp8, 0, dp8)
            dragHandle.setImageResource(R.drawable.baseline_drag_handle_24)
            view.addView(dragHandle, ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            })
            view.findViewById<View>(R.id.coverCardView).updateLayoutParams<ConstraintLayout.LayoutParams> {
                startToStart = ConstraintLayout.LayoutParams.UNSET
                startToEnd = R.id.dragHandle
            }
            closeButton.setIconResource(R.drawable.outline_close_24)
            TooltipCompat.setTooltipText(closeButton,
                closeButton.context.getString(R.string.remove))
            closeButton.contentDescription = closeButton.context.getString(R.string.remove)
        }
    }
}