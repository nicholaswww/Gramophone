package org.akanework.gramophone.logic.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.StrictMode
import androidx.annotation.RequiresApi
import androidx.media3.common.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioManagerCompat
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.nift4.gramophone.hificore.ReflectionAudioEffect
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/*
 * For "smart album/track gain selection": the used gain is always determined at audio track init.
 * Album gain will be used if track from same album is either prev or next in playlist. If user adds
 * same album track after we started playing we shall hold onto chosen gain until seek or next
 * track happens. Also that implies that if there are two songs that could be played gaplessly, they
 * should NOT be played gaplessly unless RG gain is also same.
 * TODO: for offload only, or always? if only for offload, then RGAP needs to apply gain outside of
 *  flush somehow (do AudioProcessors even get told the config is now to be applied?)
 *  https://github.com/androidx/media/issues/2855
 * That effectively avoids audible volume jumps, and allows to change effect settings for offloaded
 * RG in synchronized way as well, avoiding wrong gain applied to audio frame.
 */
// TODO: what is com.lge.media.EXTRA_VOLUME_STREAM_HIFI_VALUE
class PostAmpAudioSink(
	val sink: DefaultAudioSink, val rgAp: ReplayGainAudioProcessor, val context: Context
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
	private val isDpeOffloadable by lazy {
		false // TODO implement proper check
	}
	// TODO apply negative boost gain for non-offload, make it configurable
	private val boostGainDb = 0
	private val cleanCh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
		DynamicsProcessing.Channel(0f, false,
			0, false, 0,
			false, 0, true
		).apply {
			mbc = DynamicsProcessing.Mbc(
				false, false, 0
			)
			limiter = DynamicsProcessing.Limiter(
				true, false, 0,
				ReplayGainUtil.TAU_ATTACK * 1000f,
				ReplayGainUtil.TAU_RELEASE * 1000f,
				ReplayGainUtil.RATIO, 0f, 0f
			)
			preEq = DynamicsProcessing.Eq(
				false, false, 0
			)
			postEq = DynamicsProcessing.Eq(
				false, false, 0
			)
		}
	} else null
	private var volumeEffect: Volume? = null
	private var dpeEffect: DynamicsProcessing? = null
	private var dpeCanary: ReflectionAudioEffect? = null
	private var hasVolume = false
	private var hasDpe = false
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
				updateVolumeEffect()
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
		updateVolumeEffect()
	}

	private fun myOnReceiveBroadcast(intent: Intent) {
		updateVolumeEffect()
		// TODO do we need to do anything special?
	}

	private fun myApplyPendingConfig() {
		format = pendingFormat
		calculateGain()
		updateVolumeEffect()
	}

	private fun calculateGain() {
		// Nonchalantly borrow settings from ReplayGainAudioProcessor
		val mode: ReplayGainUtil.Mode
		val rgGain: Int
		val nonRgGain: Int
		val reduceGain: Boolean
		synchronized(rgAp) {
			mode = rgAp.mode
			rgGain = rgAp.rgGain
			nonRgGain = rgAp.nonRgGain
			reduceGain = rgAp.reduceGain
		}
		val useDpe = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasDpe
		val isOffload = true//format?.let { it.sampleMimeType != MimeTypes.AUDIO_RAW } == true TODO
		if (useDpe) {
			try {
				dpeEffect!!.enabled = isOffload || boostGainDb > 0
			} catch (e: IllegalStateException) {
				Log.e(TAG, "dpe enable=$isOffload failed", e)
			}
			try {
				dpeEffect!!.setAllChannelsTo(cleanCh)
			} catch (e: IllegalStateException) {
				Log.e(TAG, "dpe reset all channels failed", e)
			}
		}
		if (isOffload) {
			val tags = ReplayGainUtil.parse(format)
			val calcGain = ReplayGainUtil.calculateGain(tags, mode, rgGain, reduceGain || !useDpe,
				if (useDpe) ReplayGainUtil.RATIO else null)
			val gain = calcGain?.first ?: ReplayGainUtil.dbToAmpl(nonRgGain.toFloat() - boostGainDb)
			val kneeThresholdDb = calcGain?.second
			rgVolume = if (useDpe) 1f else min(gain, 1f)
			try {
				if (useDpe) {
					dpeEffect!!.setInputGainAllChannelsTo(ReplayGainUtil.amplToDb(gain))
					if (kneeThresholdDb != null) {
						dpeEffect!!.setLimiterAllChannelsTo(
							DynamicsProcessing.Limiter(
								true, true, 0,
								ReplayGainUtil.TAU_ATTACK * 1000f,
								ReplayGainUtil.TAU_RELEASE * 1000f,
								ReplayGainUtil.RATIO, kneeThresholdDb, boostGainDb.toFloat()
							)
						)
					} else {
						dpeEffect!!.setLimiterAllChannelsTo(
							DynamicsProcessing.Limiter(
								true, true, 0,
								ReplayGainUtil.TAU_ATTACK * 1000f,
								ReplayGainUtil.TAU_RELEASE * 1000f,
								ReplayGainUtil.RATIO, 9999999f, boostGainDb.toFloat()
							)
						)
					}
				}
			} catch (e: UnsupportedOperationException) {
				Log.e(TAG, "we raced with someone else about DPE and we lost", e)
			}
		} else {
			if (useDpe && boostGainDb > 0) {
				// This limiter has such a high threshold it doesn't limit anything. But it sure
				// does apply the postGain and make everything LOUD.
				dpeEffect!!.setLimiterAllChannelsTo(
					DynamicsProcessing.Limiter(
						true, true, 0,
						ReplayGainUtil.TAU_ATTACK * 1000f,
						ReplayGainUtil.TAU_RELEASE * 1000f,
						ReplayGainUtil.RATIO, 9999999f, boostGainDb.toFloat()
					)
				)
			}
			rgVolume = 1f
		}
		setVolumeInternal()
	}

	override fun setAudioSessionId(audioSessionId: Int) {
		mySetAudioSessionId(audioSessionId)
		super.setAudioSessionId(audioSessionId)
	}

	private fun mySetAudioSessionId(id: Int) {
		if (id != audioSessionId) {
			Log.i(TAG, "set session id to $id")
			if (audioSessionId != 0) {
				if (volumeEffect != null) {
					volumeEffect!!.let {
						CoroutineScope(Dispatchers.Default).launch {
							try {
								it.enabled = false
								it.release()
							} catch (e: Throwable) {
								Log.e(TAG, "failed to release Volume effect", e)
							}
						}
					}
					volumeEffect = null
				}
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dpeEffect != null) {
					// DPE must be released synchronously to avoid getting the old effect instance
					// again, with its old inUse values.
					dpeEffect!!.let {
						try {
							it.enabled = false
							it.release()
						} catch (e: Throwable) {
							Log.e(TAG, "failed to release DPE effect", e)
						}
					}
					dpeEffect = null
				}
			}
			hasVolume = false
			hasDpe = false
			audioSessionId = id
			if (id != 0) {
				// Set a lower priority when creating effects - we are willing to share.
				// (User story "EQ is not working and I have to change a obscure setting to fix it"
				// is worse than user story "it's too quiet when I enable my EQ, but gets louder
				// when I disable it").
				try {
					volumeEffect = Volume(-100000, id)
					volumeEffect!!.setControlStatusListener { _, hasControl ->
						Log.i(TAG, "volume control state is now: $hasControl")
						hasVolume = hasControl
						updateVolumeEffect()
					}
					hasVolume = volumeEffect!!.hasControl()
					Log.i(TAG, "init volume, control state is: $hasVolume")
				} catch (e: Throwable) {
					Log.e(TAG, "failed to init Volume effect", e)
				}
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isDpeOffloadable) {
					createDpeEffect()
				}
			}
		}
	}

	private fun myOnRoutingChanged(router: AudioTrack, routedDevice: AudioDeviceInfo?) {
		updateVolumeEffect()
		// TODO
	}

	@RequiresApi(Build.VERSION_CODES.P)
	private fun createDpeEffect() {
		hasDpe = false
		// DPE has this behaviour which I can really only call a bug, where inUse values are carried
		// over from other apps and the only way to reset it is to entirely release all instances
		// of the effect in ALL apps in this session ID at the same time. Also, sometimes if the
		// effect is busy the constructor randomly throws because it doesn't support priority well
		// - it always tries to set values even if we don't have control. Amazing work, Google. For
		// the DynamicsProcessing DSP to be the best thing ever, the parameter implementation is so
		// stupid I can't even put it into words.
		try {
			try {
				dpeEffect = DynamicsProcessing(
					-100000, audioSessionId,
					DynamicsProcessing.Config.Builder(
						DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
						1, false, 0,
						false, 0, false,
						0, true
					)
						.setAllChannelsTo(cleanCh)
						.build()
				)
			} catch (t: Throwable) {
				// DynamicsProcessing does not release() the instance if illegal arguments are
				// passed to the constructor. We have to rely on finalize to avoid conflicts with
				// ourselves, hence do a GC. This API is so broken...
				val policy = StrictMode.getThreadPolicy()
				try {
					StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
					System.gc()
				} finally {
					StrictMode.setThreadPolicy(policy)
				}
				throw t
			}
			dpeEffect!!.setControlStatusListener { effect, hasControl ->
				if (effect != dpeEffect) {
					try {
						effect.release()
					} catch (_: Throwable) {}
					return@setControlStatusListener // stale event
				}
				Log.i(TAG, "dpe control state is now: $hasControl")
				try {
					effect.release()
				} catch (_: Throwable) {}
				dpeEffect = null
				hasDpe = false
				if (hasControl) createDpeEffect()
				else {
					createDpeCanary()
					calculateGain() // switch from DPE to setVolume()
				}
			}
			dpeEffect!!.setAllChannelsTo(cleanCh)
			hasDpe = dpeEffect!!.hasControl()
			// wow, we got here. very good.
			if (dpeCanary != null) {
				Log.i(TAG, "release dpe canary because we got real dpe")
				try {
					dpeCanary!!.release()
				} catch (e: Throwable) {
					Log.e(TAG, "failed to release DPE canary", e)
				}
				dpeCanary = null
			}
			Log.i(TAG, "init dpe, control state is: $hasDpe")
		} catch (e: Throwable) {
			if (e is UnsupportedOperationException)
				Log.w(TAG, "failed to init DPE effect: $e")
			else
				Log.e(TAG, "failed to init DPE effect", e)
			try {
				dpeEffect?.release()
			} catch (_: Throwable) {}
			dpeEffect = null
			hasDpe = false
			createDpeCanary()
		}
		calculateGain() // switch from setVolume() to DPE (or don't if we did not succeed)
	}

	@RequiresApi(Build.VERSION_CODES.P)
	private fun createDpeCanary() {
		// The bug where constructor sets parameters (which crashes, and hence we can't register a
		// control listener) can be worked around by using AudioEffect class raw via reflection to
		// check when we regain control. (Set lower prio to avoid conflicting with ourselves.)
		if (dpeCanary == null) {
			try {
				dpeCanary = ReflectionAudioEffect(
					AudioEffect.EFFECT_TYPE_DYNAMICS_PROCESSING,
					ReflectionAudioEffect.EFFECT_TYPE_NULL, -100001, audioSessionId
				)
				dpeCanary!!.setControlStatusListener { _, controlGranted ->
					Log.i(TAG, "dpe canary control state is now: $controlGranted")
					if (controlGranted) {
						try {
							dpeCanary!!.release()
						} catch (e: Throwable) {
							Log.e(TAG, "failed to release DPE canary", e)
						}
						dpeCanary = null
						createDpeEffect()
					} else {
						// odd.
						calculateGain()
					}
				}
				Log.i(TAG, "init dpe canary")
				if (dpeCanary!!.hasControl()) {
					// what???
					Log.i(TAG, "release dpe canary because we suddenly have control")
					try {
						dpeCanary!!.release()
					} catch (e: Throwable) {
						Log.e(TAG, "failed to release DPE canary", e)
					}
					dpeCanary = null
					createDpeEffect()
				}
			} catch (e: Throwable) {
				Log.e(TAG, "failed to init DPE canary", e)
				try {
					dpeCanary?.release()
				} catch (_: Throwable) {}
				dpeCanary = null
				// whatever, I give up.
			}
		}
	}

	private fun updateVolumeEffect() {
		try {
			val useDpe = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasDpe
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				// TODO: this causes offload detection false negatives
				// Enabling the effect is not actually needed, it also works in disabled state. And
				// the big advantage of not enabling it is that offload checks calling the method
				// EffectChain::isNonOffloadableEnabled_l() will still allow to go to offload.
				try {
					if (hasVolume) volumeEffect!!.enabled = true
				} catch (e: IllegalStateException) {
					Log.e(TAG, "volume enable failed", e)
				}
				if (!hasVolume || boostGainDb == 0) return
				val boostGainForOldEffect = if (useDpe && boostGainDb > 0) 0 else boostGainDb
				val minIndex = AudioManagerCompat.getStreamMinVolume(audioManager, AudioManager.STREAM_MUSIC)
				val maxIndex = AudioManagerCompat.getStreamMaxVolume(audioManager, AudioManager.STREAM_MUSIC)
				val curIndex = AudioManagerCompat.getStreamVolume(audioManager, AudioManager.STREAM_MUSIC)
				val minVolume =
					max(
						audioManager.getStreamVolumeDb(
							AudioManager.STREAM_MUSIC, minIndex,
							AudioDeviceInfo.TYPE_BUILTIN_SPEAKER // TODO device type
						), -96f
					)
				val maxVolume =
					audioManager.getStreamVolumeDb(
						AudioManager.STREAM_MUSIC, maxIndex,
						AudioDeviceInfo.TYPE_BUILTIN_SPEAKER // TODO device type
					)
				val curVolume =
					audioManager.getStreamVolumeDb(
						AudioManager.STREAM_MUSIC, curIndex,
						AudioDeviceInfo.TYPE_BUILTIN_SPEAKER // TODO device type
					)
				val curVolumeS = if (maxVolume - minVolume == 1f) {
					if (curVolume <= 0f) -9600 else (100 * ReplayGainUtil.amplToDb(curVolume)).toInt()
						.toShort()
				} else if (curVolume < -96) -9600 else (curVolume.toInt() * 100).toShort()
				val theVolume = min(
					volumeEffect!!.maxLevel.toInt(),
					curVolumeS + boostGainForOldEffect * 100
				).toShort()
				Log.d(TAG, "min=$minVolume max=$maxVolume cur=$curVolume --> $curVolumeS --> $theVolume")
				repeat(20) {
					volumeEffect!!.level = theVolume
				}
			} else {
				// TODO support <28 for boost here
				// - https://developer.android.com/reference/android/media/AudioManager#getStreamVolumeDb(int,%20int,%20int)
				//   - problems: sdk 28 required; I read on SO that samsung broke it and it returns values 0~1
				//   - on O you can use it via AudioSystem C++; on O MR1 you can use it via AudioSystem java reflection
				// - AudioSystem::getStreamVolume() in C++
				//   - it was removed recently but exists everywhere I need it https://cs.android.com/android/_/android/platform/frameworks/av/+/38c45a4438915c73434558be9ffc2d4f73516cf2
				//   - interpretation of data changed in M https://android.googlesource.com/platform/frameworks/av/+/ffbc80f5908eaf67a033c6e93a343c39dd6894eb%5E!/
				try {
					if (hasVolume) volumeEffect!!.enabled = false
				} catch (e: IllegalStateException) {
					Log.e(TAG, "volume enable failed", e)
				}
			}
		} catch (e: Throwable) {
			Log.e(TAG, "failed to update volume effect state", e)
		}
	}

	override fun play() {
		updateVolumeEffect()
		super.play()
	}

	override fun pause() {
		updateVolumeEffect()
		super.pause()
	}

	override fun flush() {
		updateVolumeEffect()
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
			updateVolumeEffect()
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