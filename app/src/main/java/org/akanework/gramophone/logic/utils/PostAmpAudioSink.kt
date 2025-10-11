package org.akanework.gramophone.logic.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import androidx.media3.common.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import kotlin.math.log10
import kotlin.math.max

/*
 * some notes:
 * - https://developer.android.com/reference/android/media/AudioManager#getStreamVolumeDb(int,%20int,%20int)
 *   - problems: sdk 28 required; I read on SO that samsung broke it and it returns values 0~1
 *   - on O you can use it via AudioSystem C++; on O MR1 you can use it via AudioSystem java reflection
 * - AudioSystem::getStreamVolume() in C++
 *   - it was removed recently but exists everywhere I need it https://cs.android.com/android/_/android/platform/frameworks/av/+/38c45a4438915c73434558be9ffc2d4f73516cf2
 *   - interpretation of data changed in M https://android.googlesource.com/platform/frameworks/av/+/ffbc80f5908eaf67a033c6e93a343c39dd6894eb%5E!/
 *
 *
 * if boost is <=3dB, could also use https://cs.android.com/android/platform/superproject/main/+/main:system/media/audio/include/system/audio.h;l=561;drc=8063e42c30fdde36835c1862cf413d8faeadcf45
 * but that has risk of clipping when system volume is high so maybe that's a bad idea?
 *
 * also need to investigate LoudnessController and MPEG-4/MPEG-D DRC and normalization
 * https://github.com/androidx/media/tree/media_codec_param
 * prototype upstream to edit these kind of things ^^^
 *
 * Boost has two primary purposes: make ReplayGain sound louder to make it more enjoyable, and free
 * up headroom for advanced EQ (DSP plugins or AudioEffect DPE/Equalizer/BassBoost/etc both). For
 * practical reasons boost is all or nothing - the boost value does not change between songs for any
 * reason!! Songs that shouldn't be boosted instead get negative boost gain before volume control
 * stage. For one, doing that prevents bugs blasting away user ears. And doing it differently is
 * useless for RG because we can't increase volume if gain is positive but clipping-safe through low
 * peaks, and also useless for EQ because we can either apply negative boost gain through
 * DPE/Equalizer or we don't have any EQ available anyway... (with BassBoost being the only possible
 * exception, but if there is BassBoost but not DPE/Equalizer then either they're busy because
 * external EQ app in which case our BassBoost should be disabled, or the SoC vendor is really weird
 * and decided we only need BassBoost but no Equalizer or DPE, but afaik none of them did).
 *
 * TODO: UX problem I see is that we can't nicely handle if there's boost set for non-offloaded
 *  playback and we switch to offloaded in the middle.
 *
 * for ReplayGain non-offload:
 * - if non-RG track, boost gain but inverted, hence negative, should be applied via GainProcessor
 * - negative RG gain could be applied either via GainProcessor
 *     setVolume() would mean I'd need to consider it in boost calculations, so let's skip that
 * - positive RG gain would be applied via GainProcessor
 *     if gain is positive and peaks are so high that it'd clip, reduce gain or use DIY DRC
 *
 * offload ReplayGain (volume+equalizer or DPE effect is offloadable):
 * - if non-RG track, set Equalizer/DPE effect with even bands to apply inverted boost gain
 * - negative RG gain should be applied either via setVolume()
 * - positive RG gain could be applied via Equalizer/DPE.Limiter.postGain/LoudnessEnhancer effect
 *     if gain is positive and peaks are so high that it'd clip, reduce gain or use the effect's DRC
 *     TODO: can we avoid DRC on Equalizer? we can avoid on DPE and can't on LoudnessEnhancer
 * - boost would be added via Volume effect
 *
 * TODO: another problem with using Equalizer/DPE/LoudnessEnhancer is that they could be busy. Well
 *  LoudnessEnhancer usually not, but the other ones are often used by EQ apps. Those could work in
 *  offload as long as there's no conflict between apps. Hence we should try both DPE and Equalizer
 *  and use whatever is currently free. But what to do if none of them is free?
 * LoudnessEnhancer shouldn't be used without user consent due to it's aggressive DRC starting at
 * -8dBFS in the best case, and DRC in Gramophone should be opt in always.
 *
 * offload ReplayGain (volume but no equalizer, DPE or LoudnessEnhancer effect available):
 * Problem: can't increase volume if there is unused headroom (low peak) but positive RG gain.
 * Problem: Volume effect for supporting RG gain is hard/impossible to synchronize with audio.
 * Hence we can't use Volume effect alone if offloaded, proceed below.
 *
 * offload ReplayGain (no effects): just use setVolume() for negative gain, and give up on
 * everything else (boost, positive gain). TODO: that's very bad UX though.
 */
// TODO: what is com.lge.media.EXTRA_VOLUME_STREAM_HIFI_VALUE
class PostAmpAudioSink(
	val sink: DefaultAudioSink, val context: Context
) : ForwardingAudioSink(sink) {
	companion object {
		private const val TAG = "PostAmpAudioSink"
	}
	private val receiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (intent?.action == "android.media.VOLUME_CHANGED_ACTION"
				|| intent?.action == "android.media.MASTER_VOLUME_CHANGED_ACTION"
				|| intent?.action == "android.media.MASTER_MUTE_CHANGED_ACTION"
				|| intent?.action == "android.media.STREAM_MUTE_CHANGED_ACTION") {
				myOnReceiveBroadcast(intent)
			}
		}
	}
	private val audioManager = context.getSystemService<AudioManager>()!!
	private var volumeEffect: Volume? = null
	private var format: Format? = null
	private var pendingFormat: Format? = null
	private var audioSessionId = 0
	private var volume = 1f
	private var rgVolume = 1f

	init {
		ContextCompat.registerReceiver(
			context,
			receiver,
			IntentFilter().apply {
				addAction("android.media.VOLUME_CHANGED_ACTION")
				addAction("android.media.MASTER_VOLUME_CHANGED_ACTION")
				addAction("android.media.MASTER_MUTE_CHANGED_ACTION")
				addAction("android.media.STREAM_MUTE_CHANGED_ACTION")
			},
			@SuppressLint("WrongConstant") // why is this needed?
			ContextCompat.RECEIVER_NOT_EXPORTED
		)
	}

	override fun setListener(listener: AudioSink.Listener) {
		super.setListener(object : AudioSink.Listener by listener {
			override fun onPositionAdvancing(playoutStartSystemTimeMs: Long) {
				onAudioTrackPlayStateChanging()
				listener.onPositionAdvancing(playoutStartSystemTimeMs)
			}

			override fun onOffloadBufferEmptying() {
				listener.onOffloadBufferEmptying()
			}

			override fun onOffloadBufferFull() {
				listener.onOffloadBufferFull()
			}

			override fun onAudioSinkError(audioSinkError: Exception) {
				listener.onAudioSinkError(audioSinkError)
			}

			override fun onAudioCapabilitiesChanged() {
				listener.onAudioCapabilitiesChanged()
			}

			override fun onAudioTrackInitialized(audioTrackConfig: AudioSink.AudioTrackConfig) {
				myApplyPendingConfig()
				listener.onAudioTrackInitialized(audioTrackConfig)
			}

			override fun onAudioTrackReleased(audioTrackConfig: AudioSink.AudioTrackConfig) {
				listener.onAudioTrackReleased(audioTrackConfig)
			}

			override fun onSilenceSkipped() {
				listener.onSilenceSkipped()
			}

			override fun onAudioSessionIdChanged(audioSessionId: Int) {
				mySetAudioSessionId(audioSessionId)
				listener.onAudioSessionIdChanged(audioSessionId)
			}

			override fun onRoutingChanged(router: AudioTrack, routedDevice: AudioDeviceInfo?) {
				myOnRoutingChanged(router, routedDevice)
				listener.onRoutingChanged(router, routedDevice)
			}
		})
	}

	override fun configure(
		inputFormat: Format,
		specifiedBufferSize: Int,
		outputChannels: IntArray?
	) {
		pendingFormat = inputFormat
		super.configure(inputFormat, specifiedBufferSize, outputChannels)
	}

	override fun setVolume(volume: Float) {
		this.volume = volume
		setVolumeInternal()
	}

	private fun setVolumeInternal() {
		super.setVolume(volume * rgVolume)
	}

	private fun myOnReceiveBroadcast(intent: Intent) {
		Log.i("hi", "got $intent")
		onAudioTrackPlayStateChanging()
		// TODO
	}

	private fun myApplyPendingConfig() {
		format = pendingFormat
		Log.i(TAG, "set format to $format")
		onAudioTrackPlayStateChanging()
		// TODO
	}

	override fun setAudioSessionId(audioSessionId: Int) {
		mySetAudioSessionId(audioSessionId)
		super.setAudioSessionId(audioSessionId)
	}

	private fun mySetAudioSessionId(id: Int) {
		if (id != audioSessionId) {
			Log.i(TAG, "set session id to $id")
			if (audioSessionId != 0) {
				volumeEffect!!.let {
					CoroutineScope(Dispatchers.Default).launch {
						it.enabled = false
						it.release()
					}
				}
				volumeEffect = null
			}
			if (id != 0) {
				// TODO: make sure Volume effect is disabled if it can't be offloaded, to prevent
				//  false negatives in offload detection.
				volumeEffect = Volume(99999, id)
				// TODO: is enabling actually needed to change volume? if not, can we keep effect in
				//  disabled state to avoid offload detection false negatives?
				volumeEffect!!.enabled = true
			}
		}
	}

	private fun myOnRoutingChanged(router: AudioTrack, routedDevice: AudioDeviceInfo?) {
		onAudioTrackPlayStateChanging()
		// TODO
	}

	private fun onAudioTrackPlayStateChanging() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			val minIndex = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
			val maxIndex = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
			val curIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
			val minVolume =
				max(audioManager.getStreamVolumeDb(
					AudioManager.STREAM_MUSIC, minIndex,
					AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
				), -96f)
			val maxVolume =
				audioManager.getStreamVolumeDb(
					AudioManager.STREAM_MUSIC, maxIndex,
					AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
				)
			val curVolume =
				audioManager.getStreamVolumeDb(
					AudioManager.STREAM_MUSIC, curIndex,
					AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
				)
			val curVolumeS = if (maxVolume - minVolume == 1f) {
				if (curVolume <= 0f) -9600 else (2000 * log10(curVolume)).toInt().toShort()
			} else if (curVolume < -96) -9600 else (curVolume.toInt() * 100).toShort()
			Log.i("hi", "min=$minVolume max=$maxVolume cur=$curVolume --> $curVolumeS")
			for (i in 0..20) {
				volumeEffect?.level = maxOf(curVolumeS, (-5300).toShort())
			}
		}
		// TODO
	}

	override fun play() {
		onAudioTrackPlayStateChanging()
		super.play()
	}

	override fun pause() {
		onAudioTrackPlayStateChanging()
		super.pause()
	}

	override fun flush() {
		onAudioTrackPlayStateChanging()
		super.flush()
	}

	override fun release() {
		context.unregisterReceiver(receiver)
		super.release()
	}

	override fun handleBuffer(
		buffer: ByteBuffer,
		presentationTimeUs: Long,
		encodedAccessUnitCount: Int
	): Boolean {
		val prev = sink.isAudioTrackStopped()
		val ret = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
		if (sink.isAudioTrackStopped() != prev) {
			onAudioTrackPlayStateChanging()
		}
		return ret
	}

	private val audioTrackField by lazy {
		DefaultAudioSink::class.java.getDeclaredField("audioTrack").apply {
			isAccessible = true
		}
	}

	private val audioTrackStoppedField by lazy {
		DefaultAudioSink::class.java.getDeclaredField("stoppedAudioTrack").apply {
			isAccessible = true
		}
	}

	private fun DefaultAudioSink.getAudioTrack(): AudioTrack? {
		return audioTrackField.get(this) as AudioTrack?
	}

	private fun DefaultAudioSink.isAudioTrackStopped(): Boolean {
		return audioTrackStoppedField.get(this) as Boolean
	}
}