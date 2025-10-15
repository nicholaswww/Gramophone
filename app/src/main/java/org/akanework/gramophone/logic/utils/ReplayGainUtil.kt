package org.akanework.gramophone.logic.utils

import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.mp3.Mp3InfoReplayGain
import java.math.BigInteger

sealed class ReplayGainUtil {
	data class Rva2(val identification: String, val channel: Int, val volumeAdjustment: Short,
		val peakVolume: BigInteger?) : ReplayGainUtil()
	data class Txxx(val trackGain: Float, val trackPeak: Float, val albumGain: Float,
	                val albumPeak: Float) : ReplayGainUtil()
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
			TODO("RVA2 parsing")
		}

		fun parseTxxx(frames: List<TextInformationFrame>): Txxx {
			TODO("TXXX parsing (case-insensitive)")
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
	}
}