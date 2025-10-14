package org.akanework.gramophone.logic.utils

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.Log
import org.nift4.gramophone.hificore.AdaptiveDynamicRangeCompression
import java.nio.ByteBuffer
import kotlin.math.log10
import kotlin.math.min

class ReplayGainAudioProcessor : BaseAudioProcessor() {
	companion object {
		private const val TAG = "ReplayGainAP"
	}
	private var compressor: AdaptiveDynamicRangeCompression? = null
	private var waitingForFlush = false
	private var nonRgGain = 1f
	private var reduceGain = false
	private var gain = 1f
	private var tagPeak = 1f
	private var tagGain = 1f
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
					outputBuffer.putFloat((inputBuffer.getFloat() * gain).coerceIn(-1f, 1f))
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
		if (nonRgGain != 1f && false) { // TODO: check if rg metadata present.
			this.tagGain = 1f
			this.tagPeak = 1f
			waitingForFlush = true // don't call computeGain() as old data should apply until flush.
			return inputAudioFormat
		}
		// if there's no RG metadata and no non-RG-gain set, we can skip all work.
		return AudioProcessor.AudioFormat.NOT_SET
	}

	fun setGain(gain: Float, peak: Float) {
		this.tagGain = gain
		this.tagPeak = peak
		if (!waitingForFlush) computeGain()
	}

	fun computeGain() {
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
				throw IllegalStateException("reduceGain true but postGainPeak > 1f")
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

	override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
		waitingForFlush = false
		computeGain()
	}

	override fun onReset() {
		compressor?.release()
		compressor = null
	}
}