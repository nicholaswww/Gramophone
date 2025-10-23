package org.akanework.gramophone.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.replaceAllSupport
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.ui.placeholderScaleToFit
import org.akanework.gramophone.logic.utils.convertDurationToTimeStamp
import org.akanework.gramophone.ui.MainActivity
import java.util.LinkedList

class PlaylistQueueSheet(
	context: Context, private val activity: MainActivity
) : BottomSheetDialog(context), Player.Listener {
	private val instance: MediaBrowser?
		get() = activity.getPlayer()
	private var playlistNowPlaying: TextView? = null
	private var playlistNowPlayingCover: ImageView? = null

	init {
		setContentView(R.layout.playlist_bottom_sheet)
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
		val playlistAdapter = PlaylistCardAdapter(activity, instance!!)
		val callback = PlaylistCardMoveCallback(playlistAdapter::onRowMoved)
		val touchHelper = ItemTouchHelper(callback)
		touchHelper.attachToRecyclerView(recyclerView)
		playlistNowPlaying = findViewById(R.id.now_playing)
		playlistNowPlaying!!.text = instance?.currentMediaItem?.mediaMetadata?.title
		playlistNowPlayingCover = findViewById(R.id.now_playing_cover)
		playlistNowPlayingCover!!.load(instance?.currentMediaItem?.mediaMetadata?.artworkUri) {
			placeholderScaleToFit(R.drawable.ic_default_cover)
			crossfade(true)
			error(R.drawable.ic_default_cover)
		}
		recyclerView.layoutManager = LinearLayoutManager(context)
		recyclerView.adapter = playlistAdapter
		recyclerView.scrollToPosition(playlistAdapter.playlist.first.indexOfFirst { i ->
			i == (instance?.currentMediaItemIndex ?: 0)
		})
		recyclerView.fastScroll(null, null)
		setOnDismissListener {
			if (playlistNowPlaying != null) {
				playlistNowPlayingCover!!.dispose()
				playlistNowPlayingCover = null
				playlistNowPlaying = null
			}
		}
	}

	override fun onMediaItemTransition(
		mediaItem: MediaItem?,
		reason: @Player.MediaItemTransitionReason Int
	) {
		if (instance?.mediaItemCount != 0) {
			if (playlistNowPlaying != null) {
				playlistNowPlaying!!.text = mediaItem?.mediaMetadata?.title
				playlistNowPlayingCover!!.load(mediaItem?.mediaMetadata?.artworkUri) {
					placeholderScaleToFit(R.drawable.ic_default_cover)
					crossfade(true)
					error(R.drawable.ic_default_cover)
				}
			}
		} else {
			playlistNowPlayingCover?.dispose()
		}
	}

	private class PlaylistCardAdapter(
		private val activity: MainActivity,
		private val instance: MediaBrowser
	) : MyRecyclerView.Adapter<ViewHolder>() {

		var playlist: Pair<MutableList<Int>, MutableList<MediaItem>> = dumpPlaylist()
		override fun onCreateViewHolder(
			parent: ViewGroup,
			viewType: Int
		): ViewHolder =
			ViewHolder(
				LayoutInflater
					.from(parent.context)
					.inflate(R.layout.adapter_list_card_playlist, parent, false),
			)

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val item = playlist.second[playlist.first[holder.bindingAdapterPosition]]
			holder.songName.text = item.mediaMetadata.title
			holder.songArtist.text = item.mediaMetadata.artist
			holder.indicator.text = item.mediaMetadata.durationMs?.convertDurationToTimeStamp()
			holder.songCover.load(item.mediaMetadata.artworkUri) {
				placeholderScaleToFit(R.drawable.ic_default_cover)
				crossfade(true)
				error(R.drawable.ic_default_cover)
			}
			holder.closeButton.setOnClickListener { v ->
				ViewCompat.performHapticFeedback(v, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
				val pos = holder.bindingAdapterPosition
				if (pos != RecyclerView.NO_POSITION) { // if -1, user clicked remove twice on same item
					val instance = activity.getPlayer()
					val idx = playlist.first.removeAt(pos)
					playlist.first.replaceAllSupport { if (it > idx) it - 1 else it }
					instance?.removeMediaItem(idx)
					playlist.second.removeAt(idx)
					notifyItemRemoved(pos)
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
		}

		override fun onViewRecycled(holder: ViewHolder) {
			holder.songCover.dispose()
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
	}

	private class PlaylistCardMoveCallback(private val touchHelperContract: (Int, Int) -> Unit) :
		ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
		override fun isLongPressDragEnabled(): Boolean {
			return true
		}

		override fun isItemViewSwipeEnabled(): Boolean {
			return false
		}

		override fun onMove(
			recyclerView: RecyclerView,
			viewHolder: RecyclerView.ViewHolder,
			target: RecyclerView.ViewHolder
		): Boolean {
			val vhBap = viewHolder.bindingAdapterPosition
			val tBap = target.bindingAdapterPosition
			if (vhBap != RecyclerView.NO_POSITION && tBap != RecyclerView.NO_POSITION) {
				touchHelperContract(vhBap, tBap)
				return true
			}
			return false
		}

		override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
			throw IllegalStateException()
		}
	}

	class ViewHolder(
		view: View,
	) : RecyclerView.ViewHolder(view) {
		val songName: TextView = view.findViewById(R.id.title)
		val songArtist: TextView = view.findViewById(R.id.artist)
		val songCover: ImageView = view.findViewById(R.id.cover)
		val indicator: TextView = view.findViewById(R.id.indicator)
		val closeButton: MaterialButton = view.findViewById(R.id.close)
	}
}