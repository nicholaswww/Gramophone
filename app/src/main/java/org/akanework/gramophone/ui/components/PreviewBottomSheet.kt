package org.akanework.gramophone.ui.components

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.error
import coil3.size.Scale
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.startAnimation
import org.akanework.gramophone.ui.MainActivity

class PreviewBottomSheet(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
	ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), Player.Listener {
	private val activity
		get() = context as MainActivity
	private val instance: MediaController?
		get() = activity.getPlayer()
	private val bottomSheetPreviewCover: ImageView
	private val bottomSheetPreviewTitle: TextView
	private val bottomSheetPreviewSubtitle: TextView
	private val bottomSheetPreviewControllerButton: MaterialButton
	private val bottomSheetPreviewNextButton: MaterialButton
	private var lastDisposable: Disposable? = null

	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
			this(context, attrs, defStyleAttr, 0)

	constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

	init {
		inflate(context, R.layout.preview_player, this)
		bottomSheetPreviewTitle = findViewById(R.id.preview_song_name)
		bottomSheetPreviewSubtitle = findViewById(R.id.preview_artist_name)
		bottomSheetPreviewCover = findViewById(R.id.preview_album_cover)
		bottomSheetPreviewControllerButton = findViewById(R.id.preview_control)
		bottomSheetPreviewNextButton = findViewById(R.id.preview_next)

		bottomSheetPreviewControllerButton.setOnClickListener {
			ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
			instance?.playOrPause()
		}

		bottomSheetPreviewNextButton.setOnClickListener {
			ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
			instance?.seekToNext()
		}

		activity.controllerViewModel.addControllerCallback(activity.lifecycle) { _, _ ->
			instance?.addListener(this@PreviewBottomSheet)
			onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
			onMediaItemTransition(
				instance?.currentMediaItem,
				Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
			)
		}
	}

	override fun onIsPlayingChanged(isPlaying: Boolean) {
		onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
	}

	override fun onPlaybackStateChanged(playbackState: Int) {
		if (playbackState == Player.STATE_BUFFERING) return
		val myTag = bottomSheetPreviewControllerButton.getTag(R.id.play_next) as Int?
		if (instance?.isPlaying == true && myTag != 1) {
			bottomSheetPreviewControllerButton.icon =
				AppCompatResources.getDrawable(context, R.drawable.ic_play_anim)
			bottomSheetPreviewControllerButton.icon.startAnimation()
			bottomSheetPreviewControllerButton.setTag(R.id.play_next, 1)
		} else if (instance?.isPlaying == false && myTag != 2) {
			bottomSheetPreviewControllerButton.icon =
				AppCompatResources.getDrawable(context, R.drawable.ic_pause_anim)
			bottomSheetPreviewControllerButton.icon.startAnimation()
			bottomSheetPreviewControllerButton.setTag(R.id.play_next, 2)
		}
	}

	override fun onMediaItemTransition(
		mediaItem: MediaItem?,
		reason: @Player.MediaItemTransitionReason Int
	) {
		if ((instance?.mediaItemCount ?: 0) > 0) {
			lastDisposable?.dispose()
			lastDisposable = context.imageLoader.enqueue(ImageRequest.Builder(context).apply {
				target(onSuccess = {
					bottomSheetPreviewCover.setImageDrawable(it.asDrawable(context.resources))
				}, onError = {
					bottomSheetPreviewCover.setImageDrawable(it?.asDrawable(context.resources))
				}) // do not react to onStart() which sets placeholder
				data(mediaItem?.mediaMetadata?.artworkUri)
				scale(Scale.FILL)
				error(R.drawable.ic_default_cover)
			}.build())
			bottomSheetPreviewTitle.text = mediaItem?.mediaMetadata?.title
			bottomSheetPreviewSubtitle.text =
				mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist)
		} else {
			lastDisposable?.dispose()
			lastDisposable = null
		}
	}
}