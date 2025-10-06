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

	private fun myOnReceiveBroadcast(intent: Intent) {
		Log.i("hi", "got $intent")
		onAudioTrackPlayStateChanging()
		// TODO
	}

	private fun myApplyPendingConfig() {
		onAudioTrackPlayStateChanging()
		format = pendingFormat
		Log.i(TAG, "set format to $format")
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