package org.akanework.gramophone.logic.utils

import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.mp3.Mp3InfoReplayGain
import java.math.BigInteger

sealed class ReplayGainUtil {
	data class Rva2(val identification: String, val channels: List<Channel>) : ReplayGainUtil() {
		data class Channel(val channel: Int, val volumeAdjustment: Float, val peakVolume: BigInteger?)
	}
	sealed class Txxx : ReplayGainUtil()
	data class TxxxTrackGain(val value: Float) : Txxx()
	data class TxxxTrackPeak(val value: Float) : Txxx()
	data class TxxxAlbumGain(val value: Float) : Txxx()
	data class TxxxAlbumPeak(val value: Float) : Txxx()
	data class Mp3Info(val peak: Float, val field1Name: Byte, val field1Originator: Byte,
	                val field1Value: Float, val field2Name: Byte, val field2Originator: Byte,
	                val field2Value: Float) : ReplayGainUtil() {
		constructor(i: Mp3InfoReplayGain) : this(i.peak, i.field1Name,
			i.field1Originator, i.field1Value, i.field2Name,
			i.field2Originator, i.field2Value)
	}
	data class Rgad(val peak: Float, val field1Name: Byte, val field1Originator: Byte,
	                val field1Value: Float, val field2Name: Byte, val field2Originator: Byte,
	                val field2Value: Float) : ReplayGainUtil()
	companion object {
		fun parseRva2(frame: BinaryFrame): Rva2 {
			if (frame.id != "RVA2" && frame.id != "XRV" && frame.id != "XRVA")
				throw IllegalStateException("parseRva2() but frame isn't RVA2, it's $frame")
			val frame = ParsableByteArray(frame.data)
			val identificationLen = indexOfZeroByte(frame.data, 0)
			val identification = String(frame.data, 0, identificationLen,
				Charsets.ISO_8859_1)
			frame.skipBytes(identificationLen + 1)
			val channels = arrayListOf<Rva2.Channel>()
			while (frame.bytesLeft() > 0) {
				val channel = frame.readUnsignedByte()
				val volumeAdjustment = frame.readShort() / 512f
				val len = frame.readUnsignedByte()
				val peakAdjBytes = ByteArray(len)
				frame.readBytes(peakAdjBytes, 0, len)
				val peakAdj = BigInteger(peakAdjBytes)
				channels += Rva2.Channel(channel, volumeAdjustment, peakAdj)
			}
			return Rva2(identification, channels)
		}

		fun parseTxxx(description: String?, values: List<String>): Txxx? {
			val description = description?.uppercase()
			return when (description) {
				"REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_ALBUM_GAIN" -> {
					var value = values.firstOrNull()?.trim()
					if (value?.endsWith(" dB", ignoreCase = true) == true) {
						value = value.substring(0, value.length - 3)
					} else if (value?.endsWith(" LUFS", ignoreCase = true) == true) {
						value = value.substring(0, value.length - 5)
					}
					value?.toFloatOrNull()?.let {
						if (description == "REPLAYGAIN_ALBUM_GAIN") TxxxAlbumGain(it)
						else TxxxTrackGain(it)
					}
				}
				"REPLAYGAIN_TRACK_PEAK", "REPLAYGAIN_ALBUM_PEAK" -> {
					val value = values.firstOrNull()?.trim()
					value?.toFloatOrNull()?.let {
						if (description == "REPLAYGAIN_ALBUM_PEAK") TxxxAlbumPeak(it)
						else TxxxTrackPeak(it)
					}
				}
				else -> null
			}
		}

		fun parseRgad(frame: BinaryFrame): Rgad {
			if (frame.id != "RGAD" && frame.id != "RGA")
				throw IllegalStateException("parseRgad() but frame isn't RGAD, it's $frame")
			val frame = ParsableByteArray(frame.data)
			val peak = frame.readFloat()
			val field1 = frame.readShort()
			val field1Name = ((field1.toInt() shr 13) and 7).toByte()
			val field1Originator = ((field1.toInt() shr 10) and 7).toByte()
			val field1Value =
				((field1.toInt() and 0x1ff) * (if ((field1.toInt() and 0x200) != 0) -1 else 1)) / 10f
			val field2: Short = frame.readShort()
			val field2Name = ((field2.toInt() shr 13) and 7).toByte()
			val field2Originator = ((field2.toInt() shr 10) and 7).toByte()
			val field2Value =
				((field2.toInt() and 0x1ff) * (if ((field2.toInt() and 0x200) != 0) -1 else 1)) / 10f
			return Rgad(
				peak, field1Name, field1Originator, field1Value, field2Name, field2Originator,
				field2Value
			)
		}

		// this is copied from ExoPlayer's Id3Decoder
		private fun indexOfZeroByte(data: ByteArray, fromIndex: Int): Int {
			for (i in fromIndex until data.size) {
				if (data[i] == 0.toByte()) {
					return i
				}
			}
			return data.size
		}
	}
}