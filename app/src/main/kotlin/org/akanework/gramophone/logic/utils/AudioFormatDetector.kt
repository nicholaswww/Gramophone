package org.akanework.gramophone.logic.utils

import android.content.Context
import android.media.AudioFormat
import android.os.Parcelable
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import kotlinx.parcelize.Parcelize
import java.io.File
import org.akanework.gramophone.R

object AudioFormatDetector {
    fun channelConfigToString(context: Context, format: Int): String {
        return when (format) {
            AudioFormat.CHANNEL_OUT_MONO -> context.getString(R.string.spk_channel_out_mono)
            AudioFormat.CHANNEL_OUT_STEREO -> context.getString(R.string.spk_channel_out_stereo)
            AudioFormat.CHANNEL_OUT_QUAD -> context.getString(R.string.spk_channel_out_quad)
            AudioFormat.CHANNEL_OUT_SURROUND -> context.getString(R.string.spk_channel_out_surround)
            AudioFormat.CHANNEL_OUT_5POINT1 -> context.getString(R.string.spk_channel_out_5point1)
            AudioFormat.CHANNEL_OUT_6POINT1 -> context.getString(R.string.spk_channel_out_6point1)
            AudioFormat.CHANNEL_OUT_7POINT1_SURROUND -> context.getString(R.string.spk_channel_out_7point1_surround)
            AudioFormat.CHANNEL_OUT_5POINT1POINT2 -> context.getString(R.string.spk_channel_out_5point1point2)
            AudioFormat.CHANNEL_OUT_5POINT1POINT4 -> context.getString(R.string.spk_channel_out_5point1point4)
            AudioFormat.CHANNEL_OUT_7POINT1POINT2 -> context.getString(R.string.spk_channel_out_7point1point2)
            AudioFormat.CHANNEL_OUT_7POINT1POINT4 -> context.getString(R.string.spk_channel_out_7point1point4)
            AudioFormat.CHANNEL_OUT_9POINT1POINT4 -> context.getString(R.string.spk_channel_out_9point1point4)
            AudioFormat.CHANNEL_OUT_9POINT1POINT6 -> context.getString(R.string.spk_channel_out_9point1point6)
            else -> {
                val str = mutableListOf<String>()
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_LOW_FREQUENCY) != 0) {
                    str += context.getString(R.string.spk_channel_out_low_frequency)
                }
                if ((format and AudioFormat.CHANNEL_OUT_BACK_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_back_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_BACK_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_back_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_LEFT_OF_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_left_of_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_RIGHT_OF_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_right_of_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_BACK_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_back_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_SIDE_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_side_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_SIDE_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_side_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_FRONT_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_front_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_FRONT_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_front_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_FRONT_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_front_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_BACK_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_back_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_BACK_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_back_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_BACK_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_back_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_SIDE_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_side_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_TOP_SIDE_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_top_side_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_BOTTOM_FRONT_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_bottom_front_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_BOTTOM_FRONT_CENTER) != 0) {
                    str += context.getString(R.string.spk_channel_out_bottom_front_center)
                }
                if ((format and AudioFormat.CHANNEL_OUT_BOTTOM_FRONT_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_bottom_front_right)
                }
                if ((format and AudioFormat.CHANNEL_OUT_LOW_FREQUENCY_2) != 0) {
                    str += context.getString(R.string.spk_channel_out_low_frequency_2)
                }
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_WIDE_LEFT) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_wide_left)
                }
                if ((format and AudioFormat.CHANNEL_OUT_FRONT_WIDE_RIGHT) != 0) {
                    str += context.getString(R.string.spk_channel_out_front_wide_right)
                }
                if (str.isEmpty())
                    context.getString(R.string.spk_channel_invalid)
                str.joinToString(" | ")
            }
        }
    }

    @UnstableApi
    enum class Encoding(val enc: Int, private val res: Int) {
        ENCODING_INVALID(C.ENCODING_INVALID, R.string.spk_encoding_invalid),
        ENCODING_PCM_8BIT(C.ENCODING_PCM_8BIT, R.string.spk_encoding_pcm_8bit),
        ENCODING_PCM_16BIT(C.ENCODING_PCM_16BIT, R.string.spk_encoding_pcm_16bit),
        ENCODING_PCM_16BIT_BIG_ENDIAN(C.ENCODING_PCM_16BIT_BIG_ENDIAN, R.string.spk_encoding_pcm_16bit_big_endian),
        ENCODING_PCM_24BIT(C.ENCODING_PCM_24BIT, R.string.spk_encoding_pcm_24bit),
        ENCODING_PCM_24BIT_BIG_ENDIAN(C.ENCODING_PCM_24BIT_BIG_ENDIAN, R.string.spk_encoding_pcm_24bit_big_endian),
        ENCODING_PCM_32BIT(C.ENCODING_PCM_32BIT, R.string.spk_encoding_pcm_32bit),
        ENCODING_PCM_32BIT_BIG_ENDIAN(C.ENCODING_PCM_32BIT_BIG_ENDIAN, R.string.spk_encoding_pcm_32bit_big_endian),
        ENCODING_PCM_FLOAT(C.ENCODING_PCM_FLOAT, R.string.spk_encoding_pcm_float),
        ENCODING_MP3(C.ENCODING_MP3, R.string.spk_encoding_mp3),
        ENCODING_AAC_LC(C.ENCODING_AAC_LC, R.string.spk_encoding_aac_lc),
        ENCODING_AAC_HE_V1(C.ENCODING_AAC_HE_V1, R.string.spk_encoding_aac_he_v1),
        ENCODING_AAC_HE_V2(C.ENCODING_AAC_HE_V2, R.string.spk_encoding_aac_he_v2),
        ENCODING_AAC_XHE(C.ENCODING_AAC_XHE, R.string.spk_encoding_aac_xhe),
        ENCODING_AAC_ELD(C.ENCODING_AAC_ELD, R.string.spk_encoding_aac_eld),
        ENCODING_AAC_ER_BSAC(C.ENCODING_AAC_ER_BSAC, R.string.spk_encoding_aac_er_bsac),
        ENCODING_AC3(C.ENCODING_AC3, R.string.spk_encoding_ac3),
        ENCODING_E_AC3(C.ENCODING_E_AC3, R.string.spk_encoding_e_ac3),
        ENCODING_E_AC3_JOC(C.ENCODING_E_AC3_JOC, R.string.spk_encoding_e_ac3_joc),
        ENCODING_AC4(C.ENCODING_AC4, R.string.spk_encoding_ac4),
        ENCODING_DTS(C.ENCODING_DTS, R.string.spk_encoding_dts),
        ENCODING_DTS_HD(C.ENCODING_DTS_HD, R.string.spk_encoding_dts_hd),
        ENCODING_DOLBY_TRUEHD(C.ENCODING_DOLBY_TRUEHD, R.string.spk_encoding_dolby_truehd),
        ENCODING_OPUS(C.ENCODING_OPUS, R.string.spk_encoding_opus),
        ENCODING_DTS_UHD_P2(C.ENCODING_DTS_UHD_P2, R.string.spk_encoding_dts_uhd_p2);

        fun getString(context: Context) = context.getString(res)

        companion object {
            fun get(enc: Int) = Encoding.entries.first { it.enc == enc }
        }
    }

    @Parcelize
    enum class AudioQuality : Parcelable {
        UNKNOWN,    // Unable to determine quality
        LOSSY,      // Compressed formats (MP3, AAC, OGG)
        CD,         // 16-bit/44.1kHz or 16-bit/48kHz (Red Book)
        HD,         // 24-bit/44.1kHz or 24-bit/48kHz
        HIRES       // 24-bit/88.2kHz+ (Hi-Res Audio standard)
    }

    @Parcelize
    enum class SpatialFormat : Parcelable {
        NONE,           // Mono
        STEREO,         // 2.0: Left, Right
        QUAD,           // 4.0: FL, FR, BL, BR (Quadraphonic)
        SURROUND_5_0,   // 5.0: FL, FR, FC, BL, BR
        SURROUND_5_1,   // 5.1: FL, FR, FC, LFE, BL, BR
        SURROUND_6_1,   // 6.1: FL, FR, FC, LFE, BC, SL, SR
        SURROUND_7_1,   // 7.1: FL, FR, FC, LFE, BL, BR, SL, SR
        DOLBY_AC4,      // Dolby AC-4
        DOLBY_AC3,      // Dolby Digital
        DOLBY_EAC3,     // Dolby Digital Plus
        DOLBY_EAC3_JOC, // Dolby Digital Plus with Joint Object Coding
        DOLBY_TRUEHD,   // Dolby TrueHD
        DTS,            // DTS
        DTS_EXPRESS,    // DTS Express
        DTS_HD,         // DTS-HD
        DTSX,           // DTS-X (DTS-UHD Profile 2)
        OTHER
    }

    @Parcelize
    data class AudioFormatInfo(
        val quality: AudioQuality,
        val sampleRate: Int,
        val bitDepth: Int?,
        val isLossless: Boolean?,
        val sourceChannels: Int,
        val deviceChannels: Int,
        val bitrate: Int?,
        val mimeType: String?,
        val spatialFormat: SpatialFormat,
        val encoderPadding: Int?,
        val encoderDelay: Int?
    ) : Parcelable {
        val isDownMixing: Boolean
            get() = sourceChannels > deviceChannels

        override fun toString(): String {
            val outputStr = if (isDownMixing) {
                "(Down mixed to $deviceChannels channels)"
            } else ""

            return """
                    Audio Format Details:
                    Quality Tier: $quality
                    Sample Rate: $sampleRate Hz
                    Bit Depth: $bitDepth bit
                    Source Channels: $sourceChannels $outputStr
                    Lossless: $isLossless
                    Spatial Format: $spatialFormat
                    Codec: $mimeType
                    ${bitrate?.let { "Bitrate: ${it / 1000} kbps" } ?: ""}
                """.trimIndent()
        }
    }

    @UnstableApi
    fun detectAudioFormat(
        tracks: Tracks,
        player: Player?
    ): AudioFormatInfo? {
        // TODO: use MediaRouter for getting device channel information
        val deviceChannels = 2

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    if (!group.isTrackSelected(i)) continue

                    val format = group.getTrackFormat(i)
                    val bitrate = if (format.bitrate != Format.NO_VALUE) {
                        format.bitrate
                    } else {
                        calculateOverallBitrate(player)
                    }

                    val sampleRate = normalizeToStandardRate(format.sampleRate)
                    val bitDepth = detectBitDepth(format)
                    val isLossless = isLosslessFormat(format.sampleMimeType)
                    val spatialFormat = detectSpatialFormat(format)
                    val sourceChannels = format.channelCount

                    val quality = determineQualityTier(
                        sampleRate = sampleRate,
                        bitDepth = bitDepth ?: 0,
                        isLossless = isLossless
                    )

                    return AudioFormatInfo(
                        quality = quality,
                        sampleRate = sampleRate,
                        bitDepth = bitDepth,
                        isLossless = isLossless,
                        sourceChannels = sourceChannels,
                        deviceChannels = deviceChannels,
                        bitrate = bitrate,
                        mimeType = format.sampleMimeType,
                        spatialFormat = spatialFormat,
                        encoderPadding = format.encoderPadding.takeIf { it != Format.NO_VALUE },
                        encoderDelay = format.encoderDelay.takeIf { it != Format.NO_VALUE }
                    )
                }
            }
        }
        return null
    }

    private fun calculateOverallBitrate(player: Player?): Int? {
        // TODO do not count cover or container data
        if (player == null) return null

        val duration = player.duration
        if (duration <= 0) return null

        val mediaItem = player.currentMediaItem ?: return null
        val uri = mediaItem.localConfiguration?.uri ?: return null

        try {
            val file = File(uri.path!!)
            val sizeInBits = file.length() * 8
            return (sizeInBits / (duration / 1000.0)).toInt()
        } catch (e: Exception) {
            return null
        }
    }

    private fun normalizeToStandardRate(rate: Int): Int {
        return when (rate) {
            in 44000..44200 -> 44100    // CD standard
            in 47900..48100 -> 48000    // DVD standard
            in 87900..88300 -> 88200    // 2x 44.1
            in 95900..96100 -> 96000    // DVD-Audio/Hi-Res
            in 176200..176600 -> 176400 // 4x 44.1
            in 191900..192100 -> 192000 // Hi-Res
            in 383900..394100 -> 394000 // Why?
            in 767900..768100 -> 768000 // I bet your 10000$ DAC can't play this.
            else -> rate
        }
    }

    @UnstableApi
    private fun detectBitDepth(format: Format): Int? {
        return when (format.pcmEncoding) {
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 16
            C.ENCODING_PCM_32BIT -> 32
            C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 16
            C.ENCODING_PCM_FLOAT -> 32
            else -> null
        }
    }

	@OptIn(UnstableApi::class)
	private fun isLosslessFormat(mimeType: String?): Boolean? = when (mimeType) {
        MimeTypes.AUDIO_FLAC,
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_WAV,
        MimeTypes.AUDIO_TRUEHD -> true

        // TODO distinguish lossless DTS-HD MA vs other lossy DTS-HD encoding schemes
        MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_X -> null

        else -> false
    }

    @UnstableApi
    private fun detectSpatialFormat(format: Format): SpatialFormat {
        val mimeFormat = when (format.sampleMimeType) {
            MimeTypes.AUDIO_AC3 -> SpatialFormat.DOLBY_AC3
            MimeTypes.AUDIO_E_AC3 -> SpatialFormat.DOLBY_EAC3
            MimeTypes.AUDIO_E_AC3_JOC -> SpatialFormat.DOLBY_EAC3_JOC
            MimeTypes.AUDIO_AC4 -> SpatialFormat.DOLBY_AC4
            MimeTypes.AUDIO_TRUEHD -> SpatialFormat.DOLBY_TRUEHD
            MimeTypes.AUDIO_DTS -> SpatialFormat.DTS
            MimeTypes.AUDIO_DTS_EXPRESS -> SpatialFormat.DTS_EXPRESS
            MimeTypes.AUDIO_DTS_HD -> SpatialFormat.DTS_HD
            MimeTypes.AUDIO_DTS_X -> SpatialFormat.DTSX
            else -> null
        }

        if (mimeFormat != null) return mimeFormat

        // Standard multichannel formats TODO can we just go by channel count? isn't there any way to distinguish QUAD from QUAD_BACK?
        return when (format.channelCount) {
            1 -> SpatialFormat.NONE          // Mono
            2 -> SpatialFormat.STEREO        // Standard stereo
            4 -> SpatialFormat.QUAD          // Quadraphonic
            5 -> SpatialFormat.SURROUND_5_0  // 5.0 surround
            6 -> SpatialFormat.SURROUND_5_1  // 5.1 surround
            7 -> SpatialFormat.SURROUND_6_1  // 6.1 surround
            8 -> SpatialFormat.SURROUND_7_1  // 7.1 surround
            else -> SpatialFormat.OTHER
        }
    }

    private fun determineQualityTier(
        sampleRate: Int,
        bitDepth: Int,
        isLossless: Boolean?
    ): AudioQuality = when {
        isLossless == false -> AudioQuality.LOSSY

        // Hi-Res: 24bit+ and 96kHz+
        bitDepth >= 24 && sampleRate >= 88200 -> AudioQuality.HIRES

        // HD: 24bit at standard rates OR 16bit at high rates
        (bitDepth >= 24 && sampleRate in setOf(44100, 48000)) ||
                (bitDepth == 16 && sampleRate >= 88200) -> AudioQuality.HD

        // CD: 16bit at standard rates
        bitDepth == 16 && sampleRate in setOf(44100, 48000) -> AudioQuality.CD

        // Fallback for non-standard combinations
        else -> AudioQuality.UNKNOWN
    }
}