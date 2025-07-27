package org.akanework.gramophone.logic.utils.exoplayer

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.GramophoneApplication
import org.akanework.gramophone.logic.utils.CircularShuffleOrder


/**
 * If player in STATE_ENDED is resumed, state will be STATE_READY, on play button press it will
 * update to STATE_ENDED and only then media3 will wrap around playlist for us. This is a workaround
 * to restore STATE_ENDED as well and fake it for media3 until it indeed wraps around playlist.
 */
@OptIn(UnstableApi::class)
class EndedWorkaroundPlayer(private val context: Context, player: ExoPlayer) : ForwardingPlayer(player), Player.Listener {

    companion object {
        private const val TAG = "EndedWorkaroundPlayer"
    }

    private val remoteDeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
    private var wasEmpty = true
    val exoPlayer
        get() = wrappedPlayer as ExoPlayer
    init {
        exoPlayer.addListener(this)
    }
    // TODO: can't we do this in a cleaner way?
    var nextShuffleOrder:
            ((firstIndex: Int, mediaItemCount: Int, EndedWorkaroundPlayer) -> CircularShuffleOrder)?
            = null
    var isEnded = false
        set(value) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "isEnded set to $value (was $field)")
            }
            field = value
        }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason == DISCONTINUITY_REASON_SEEK) {
            isEnded = false
        }
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
    }

    override fun onTimelineChanged(timeline: Timeline, reason: @Player.TimelineChangeReason Int) {
        if (timeline.isEmpty != wasEmpty) {
            val wasEmpty = wasEmpty
            ContextCompat.getMainExecutor(context).execute {
                listeners.forEach {
                    it.value.onDeviceInfoChanged(if (!wasEmpty) remoteDeviceInfo else exoPlayer.deviceInfo)
                }
            }
            this.wasEmpty = timeline.isEmpty
        }
    }

    override fun getPlaybackState(): Int {
        if (isEnded) return STATE_ENDED
        return super.getPlaybackState()
    }

    override fun getDeviceInfo(): DeviceInfo {
        if (exoPlayer.currentTimeline.isEmpty) return remoteDeviceInfo
        return super.getDeviceInfo()
    }
}