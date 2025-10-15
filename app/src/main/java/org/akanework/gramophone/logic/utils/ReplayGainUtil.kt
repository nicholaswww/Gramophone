package org.akanework.gramophone.logic.utils

import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.extractor.mp3.Mp3InfoReplayGain
import java.math.BigInteger
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max

sealed class ReplayGainUtil {
	data class Rva2(val identification: String, val channels: List<Channel>) : ReplayGainUtil() {
		enum class ChannelEnum {
			Other,
			MasterVolume,
			FrontRight,
			FrontLeft,
			BackRight,
			BackLeft,
			FrontCenter,
			BackCenter,
			Subwoofer
		}
		data class Channel(val channel: ChannelEnum, val volumeAdjustment: Float,
		                   val peakVolume: BigInteger?) // peak volume can be up to 255-bit number
	}
	data class Rvad(val channels: List<Channel>) : ReplayGainUtil() {
		enum class ChannelEnum {
			FrontLeft,
			FrontRight,
			BackLeft,
			BackRight,
			Center,
			Bass
		}
		data class Channel(val channel: ChannelEnum, val volumeAdjustment: BigInteger,
		                   val peakVolume: BigInteger?) // peak volume can be up to 255-bit number
	}
	sealed class Txxx : ReplayGainUtil()
	data class TxxxTrackGain(val value: Float) : Txxx()
	data class R128TrackGain(val value: Float) : Txxx()
	data class TxxxTrackPeak(val value: Float) : Txxx()
	data class TxxxAlbumGain(val value: Float) : Txxx()
	data class R128AlbumGain(val value: Float) : Txxx()
	data class TxxxAlbumPeak(val value: Float) : Txxx()
	data class SoundCheck(val gainL: Float, val gainR: Float, val gainAltL: Float,
	                      val gainAltR: Float, val unk1: Int, val unk2: Int, val peakL: Float,
	                      val peakR: Float, val unk3: Int, val unk4: Int) : ReplayGainUtil() {
		val gain: Float
			get() = max(gainL, gainR)
		val peak: Float
			get() = max(peakL, peakR)
	}
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
	data class ReplayGainInfo(val trackGain: Float, val trackPeak: Float, val albumGain: Float,
	                          val albumPeak: Float)
	companion object {
		private fun parseRva2(frame: BinaryFrame): Rva2 {
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
				val len = ceil(frame.readUnsignedByte() / 8f).toInt()
				val peakBytes = ByteArray(len)
				frame.readBytes(peakBytes, 0, len)
				val peak = BigInteger(peakBytes)
				channels += Rva2.Channel(Rva2.ChannelEnum.entries[channel],
					volumeAdjustment, peak)
			}
			return Rva2(identification, channels)
		}

		private fun parseRvad(frame: BinaryFrame): Rvad {
			if (frame.id != "RVAD" && frame.id != "RVA")
				throw IllegalStateException("parseRvad() but frame isn't RVAD, it's $frame")
			val frame = ParsableByteArray(frame.data)
			val signs = frame.readUnsignedByte()
			val signFR = signs and 1 == 0
			val signFL = (signs shr 1) and 1 == 0
			val signBR = (signs shr 2) and 1 == 0
			val signBL = (signs shr 3) and 1 == 0
			val signC = (signs shr 4) and 1 == 0
			val signB = (signs shr 5) and 1 == 0
			val len = ceil(frame.readUnsignedByte() / 8f).toInt()
			val buf = ByteArray(len)
			val channels = arrayListOf<Rvad.Channel>()
			frame.readBytes(buf, 0, len)
			val volumeAdjFR = BigInteger(buf)
				.let { if (signFR) it.multiply(BigInteger.valueOf(-1)) else it }
			frame.readBytes(buf, 0, len)
			val volumeAdjFL = BigInteger(buf)
				.let { if (signFL) it.multiply(BigInteger.valueOf(-1)) else it }
			val peakVolFR: BigInteger?
			val peakVolFL: BigInteger?
			if (frame.bytesLeft() > 0) {
				frame.readBytes(buf, 0, len)
				peakVolFR = BigInteger(buf)
				frame.readBytes(buf, 0, len)
				peakVolFL = BigInteger(buf)
			} else {
				peakVolFR = null
				peakVolFL = null
			}
			channels += Rvad.Channel(Rvad.ChannelEnum.FrontRight,
				volumeAdjFR, peakVolFR)
			channels += Rvad.Channel(Rvad.ChannelEnum.FrontLeft,
				volumeAdjFL, peakVolFL)
			if (frame.bytesLeft() > 0) {
				frame.readBytes(buf, 0, len)
				val volumeAdjBR = BigInteger(buf)
					.let { if (signBR) it.multiply(BigInteger.valueOf(-1)) else it }
				frame.readBytes(buf, 0, len)
				val volumeAdjBL = BigInteger(buf)
					.let { if (signBL) it.multiply(BigInteger.valueOf(-1)) else it }
				val peakVolBR: BigInteger?
				val peakVolBL: BigInteger?
				if (frame.bytesLeft() > 0) {
					frame.readBytes(buf, 0, len)
					peakVolBR = BigInteger(buf)
					frame.readBytes(buf, 0, len)
					peakVolBL = BigInteger(buf)
				} else {
					peakVolBR = null
					peakVolBL = null
				}
				channels += Rvad.Channel(Rvad.ChannelEnum.BackRight,
					volumeAdjBR, peakVolBR)
				channels += Rvad.Channel(Rvad.ChannelEnum.BackLeft,
					volumeAdjBL, peakVolBL)
				if (frame.bytesLeft() > 0) {
					val volumeAdjC = BigInteger(buf)
						.let { if (signC) it.multiply(BigInteger.valueOf(-1)) else it }
					frame.readBytes(buf, 0, len)
					val peakVolC: BigInteger?
					if (frame.bytesLeft() > 0) {
						frame.readBytes(buf, 0, len)
						peakVolC = BigInteger(buf)
					} else {
						peakVolC = null
					}
					channels += Rvad.Channel(Rvad.ChannelEnum.Center,
						volumeAdjC, peakVolC)
					if (frame.bytesLeft() > 0) {
						val volumeAdjB = BigInteger(buf)
							.let { if (signB) it.multiply(BigInteger.valueOf(-1)) else it }
						frame.readBytes(buf, 0, len)
						val peakVolB: BigInteger?
						if (frame.bytesLeft() > 0) {
							frame.readBytes(buf, 0, len)
							peakVolB = BigInteger(buf)
						} else {
							peakVolB = null
						}
						channels += Rvad.Channel(Rvad.ChannelEnum.Bass,
							volumeAdjB, peakVolB)
					}
				}
			}
			return Rvad(channels)
		}

		private fun parseTxxx(description: String?, values: List<String>): Txxx? {
			val description = description?.uppercase()
			return when (description) {
				"REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_ALBUM_GAIN" -> {
					var value = values.firstOrNull()?.trim()
					if (value?.endsWith(" dB", ignoreCase = true) == true
						|| value?.endsWith(" LU", ignoreCase = true) == true) {
						value = value.substring(0, value.length - 3)
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
				"R128_TRACK_GAIN", "R128_ALBUM_GAIN" -> {
					val value = values.first().trim()
					value.toFloatOrNull()?.let {
						if (description == "R128_ALBUM_GAIN") R128AlbumGain(it)
						else R128TrackGain(it)
					}
				}
				else -> null
			}
		}

		private fun parseRgad(frame: BinaryFrame): Rgad {
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

		private fun parseITunNORM(text: String): SoundCheck? {
			val soundcheck = try {
				text.trim().split(' ').map {
					it.hexToInt(HexFormat {
						upperCase = true
					})
				}
			} catch (_: IllegalArgumentException) {
				return null
			}
			if (soundcheck.size < 9)
				return null
			val gainL = log10(soundcheck[0] / 1000.0f) * -10
			val gainR = log10(soundcheck[1] / 1000.0f) * -10
			val gainAltL = log10(soundcheck[2] / 2500.0f) * -10
			val gainAltR = log10(soundcheck[3] / 2500.0f) * -10
			val unk1 = soundcheck[4]
			val unk2 = soundcheck[5]
			val peakL = soundcheck[6] / 32768.0f
			val peakR = soundcheck[7] / 32768.0f
			val unk3 = soundcheck[8]
			val unk4 = soundcheck[9]
			return SoundCheck(gainL, gainR, gainAltL, gainAltR, unk1, unk2, peakL, peakR, unk3, unk4)
		}

		fun parse(inputFormat: Format): ReplayGainInfo {
			val metadata = arrayListOf<ReplayGainUtil>()
			inputFormat.metadata?.getMatchingEntries(InternalFrame::class.java)
			{ it.domain == "com.apple.iTunes" &&
					it.description.startsWith("REPLAYGAIN_", ignoreCase = true) }
				?.let {
					metadata.addAll(it.mapNotNull { frame ->
						parseTxxx(frame.description, listOf(frame.text))
					})
				}
			inputFormat.metadata?.getMatchingEntries(VorbisComment::class.java)
			{ it.key.startsWith("REPLAYGAIN_", ignoreCase = true)
					|| it.key.startsWith("R128_", ignoreCase = true) }
				?.let {
					metadata.addAll(it.mapNotNull { frame ->
						parseTxxx(frame.key, listOf(frame.value))
					})
				}
			inputFormat.metadata?.getMatchingEntries(TextInformationFrame::class.java)
			{ (it.id == "TXXX" || it.id == "TXX") &&
					it.description?.startsWith("REPLAYGAIN_", ignoreCase = true) == true }
				?.let {
					metadata.addAll(it.mapNotNull { frame ->
						parseTxxx(frame.description, frame.values)
					})
				}
			inputFormat.metadata?.getMatchingEntries(BinaryFrame::class.java)
			{ it.id == "RVA2" || it.id == "XRV" || it.id == "XRVA" }?.let {
				metadata.addAll(it.map { frame ->
					parseRva2(frame)
				})
			}
			inputFormat.metadata?.getMatchingEntries(BinaryFrame::class.java)
			{ it.id == "RVAD" || it.id == "RVA" }?.let {
				metadata.addAll(it.map { frame ->
					parseRvad(frame)
				})
			}
			inputFormat.metadata?.getMatchingEntries(BinaryFrame::class.java)
			{ it.id == "RGAD" }?.let { metadata.addAll(it.map { frame ->
				parseRgad(frame)
			}) }
			inputFormat.metadata?.getEntriesOfType(Mp3InfoReplayGain::class.java)
				?.let { metadata.addAll(it.map { info -> Mp3Info(info) }) }
			inputFormat.metadata?.getMatchingEntries(CommentFrame::class.java)
			{ it.description == "iTunNORM" }
				?.let {
					metadata.addAll(it.mapNotNull { frame ->
						parseITunNORM(frame.text)
					})
				}
			inputFormat.metadata?.getMatchingEntries(InternalFrame::class.java)
			{ it.domain == "com.apple.iTunes" && it.description == "iTunNORM" }
				?.let {
					metadata.addAll(it.mapNotNull { frame ->
						parseITunNORM(frame.text)
					})
				}
			TODO("combine above data into ReplayGainInfo")
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