package org.akanework.gramophone.logic.utils

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.Log
import org.nift4.gramophone.hificore.AdaptiveDynamicRangeCompression
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.min

class ReplayGainAudioProcessor : BaseAudioProcessor() {
	companion object {
		private const val TAG = "ReplayGainAP"
	}
	enum class Mode {
		None, Track, Album
	}
	private var compressor: AdaptiveDynamicRangeCompression? = null
	private var waitingForFlush = false
	private var mode = Mode.None
	private var rgGain = 0 // dB
	private var nonRgGain = 0 // dB
	private var reduceGain = false
	private var gain = 1f
	private var tagTrackPeak: Float? = null
	private var tagTrackGain: Float? = null
	private var tagAlbumPeak: Float? = null
	private var tagAlbumGain: Float? = null
	private var postGainPeak = 1f
	private var kneeThresholdDb = 0f
	private val ratio = 2f
	private val tauAttack = 0.0014f
	private val tauRelease = 0.093f
	override fun queueInput(inputBuffer: ByteBuffer) {
		val frameCount = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
		val outputBuffer = replaceOutputBuffer(frameCount * outputAudioFormat.bytesPerFrame)
		if (compressor != null) {
			compressor!!.compress(inputAudioFormat.channelCount,
				gain,
				kneeThresholdDb, 1f, inputBuffer,
				outputBuffer, frameCount)
			inputBuffer.position(inputBuffer.limit())
			outputBuffer.position(frameCount * outputAudioFormat.bytesPerFrame)
		} else {
			if (gain == 1f) {
				outputBuffer.put(inputBuffer)
			} else {
				while (inputBuffer.hasRemaining()) {
					outputBuffer.putFloat(inputBuffer.getFloat() * gain)
				}
			}
		}
		outputBuffer.flip()
	}

	override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
		if (inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
			throw UnhandledAudioFormatException(
				"Invalid PCM encoding. Expected float PCM.", inputAudioFormat
			)
		}
		// TODO: if setMode and setNonRgGain required reconfiguration, we could be a lot lazier.
		if ((tagTrackGain == null && tagAlbumGain == null)
			|| (tagTrackGain != null && tagTrackGain != 1f)
			|| (tagAlbumGain != null && tagAlbumGain != 1f)) {
			return inputAudioFormat
		}
		// if there's RG metadata but it says we don't need to do anything, we can skip all work.
		return AudioProcessor.AudioFormat.NOT_SET
	}

	@Synchronized
	fun setMode(mode: Mode) {
		this.mode = mode
	}

	@Synchronized
	fun setRgGain(rgGain: Int) {
		this.rgGain = rgGain
	}

	@Synchronized
	fun setNonRgGain(nonRgGain: Int) {
		this.nonRgGain = nonRgGain
	}

	@Synchronized
	fun setReduceGain(reduceGain: Boolean) {
		this.reduceGain = reduceGain
	}

	fun setRootFormat(inputFormat: Format) {
		val rg = ReplayGainUtil.parse(inputFormat)
		tagTrackGain = rg.trackGain
		tagTrackPeak = rg.trackPeak
		tagAlbumGain = rg.albumGain
		tagAlbumPeak = rg.albumPeak
	}

	private fun computeGain() {
		val mode: Mode
		val rgGain: Int
		val nonRgGain: Int
		val reduceGain: Boolean
		synchronized(this) {
			mode = this.mode
			rgGain = this.rgGain
			nonRgGain = this.nonRgGain
			reduceGain = this.reduceGain
		}
		val rgGainAmpl = dbToAmpl(rgGain)
		val tagGain = when (mode) {
			Mode.Track -> (tagTrackGain ?: tagAlbumGain)?.times(rgGainAmpl) ?: dbToAmpl(nonRgGain)
			Mode.Album -> (tagAlbumGain?: tagTrackGain)?.times(rgGainAmpl) ?: dbToAmpl(nonRgGain)
			Mode.None -> 1f
		}
		val tagPeak = when (mode) {
			Mode.Track -> tagTrackPeak ?: tagAlbumPeak ?: 1f
			Mode.Album -> tagAlbumPeak ?: tagTrackPeak ?: 1f
			Mode.None -> 1f
		}
		gain = if (reduceGain) {
			min(tagGain, if (tagPeak == 0f) 1f else 1f / tagPeak)
		} else {
			tagGain
		}
		postGainPeak = (if (tagPeak == 0f) 1f else tagPeak) * (if (gain == 0f) 0.001f else gain)
		val postGainPeakDb = 20 * log10(postGainPeak)
		val targetDb = 0
		kneeThresholdDb = postGainPeakDb - (postGainPeakDb - targetDb) * ratio / (ratio - 1f)
		if (postGainPeakDb + targetDb > 0f) {
			if (reduceGain)
				throw IllegalStateException("reduceGain true but ($postGainPeak)" +
						"$postGainPeakDb > 0f")
			if (compressor == null)
				compressor = AdaptiveDynamicRangeCompression()
			Log.w(TAG, "using dynamic range compression")
			compressor!!.init(
				inputAudioFormat.sampleRate,
				tauAttack, tauRelease, ratio
			)
		} else {
			onReset() // delete compressor
		}
	}

	private fun dbToAmpl(db: Int): Float {
		if (db <= -758) {
			return 0f
		}
		return exp(db * ln(10f) / 20f)
	}

	override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
		waitingForFlush = false
		computeGain()
	}

	override fun onReset() {
		compressor?.release()
		compressor = null
	}
}