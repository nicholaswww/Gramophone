package org.akanework.gramophone.logic.utils

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.Log
import org.nift4.gramophone.hificore.AdaptiveDynamicRangeCompression
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

class ReplayGainAudioProcessor : BaseAudioProcessor() {
	companion object {
		private const val TAG = "ReplayGainAP"
	}
	private var compressor: AdaptiveDynamicRangeCompression? = null
	private var nonRgGain = 1f
	private var gain = 1f
	private var postGainPeak = 1f
	private var kneeThresholdDb = 0f
	private val ratio = 2f
	private val tauAttack = 0.0014f
	private val tauRelease = 0.093f
	override fun queueInput(inputBuffer: ByteBuffer) {
		val frameCount = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
		val outputBuffer = replaceOutputBuffer(frameCount * outputAudioFormat.bytesPerFrame)
		if (compressor != null) {
			// TODO: make the math make sense? :D
			val freeHeadroom = 1f / min(1f, postGainPeak) - 1f
			val gainReduction = gain - max(0f, gain - freeHeadroom)
			compressor!!.compress(inputAudioFormat.channelCount,
				gain - gainReduction,
				kneeThresholdDb, 1f + gainReduction, inputBuffer,
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
			return inputAudioFormat
		}
		// if there's no RG metadata and no non-RG-gain set, we can skip all work.
		return AudioProcessor.AudioFormat.NOT_SET
	}

	fun setGain(gain: Float, peak: Float) {
		this.gain = gain
		this.postGainPeak = peak * gain
		if (postGainPeak == 0f) {
			Log.e(TAG, "Nonsense post gain peak, peak=$peak * gain=$gain")
			postGainPeak = 1f
		}
		val gainDb = 20 * log10(gain)
		kneeThresholdDb = gainDb - gainDb * ratio / (ratio - 1f)
	}

	override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
		val needsCompressor = postGainPeak > 1f
		if (needsCompressor) {
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

	override fun onReset() {
		compressor?.release()
		compressor = null
	}
}