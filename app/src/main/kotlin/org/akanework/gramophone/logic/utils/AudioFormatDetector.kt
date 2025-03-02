package org.akanework.gramophone.logic.utils

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.os.Build
import android.os.Parcelable
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import kotlinx.parcelize.Parcelize
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
            fun get(enc: Int) = Encoding.entries.find { it.enc == enc }
            fun getString(context: Context, enc: Int) = get(enc)?.getString(context) ?: "UNKNOWN($enc)"
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
        val sampleRate: Int?,
        val bitDepth: Int?,
        val isLossless: Boolean?,
        val sourceChannels: Int?,
        val bitrate: Int?,
        val mimeType: String?,
        val spatialFormat: SpatialFormat,
        val encoderPadding: Int?,
        val encoderDelay: Int?
    ) : Parcelable {
        override fun toString(): String {
            return """
                    Audio Format Details:
                    Quality Tier: $quality
                    Sample Rate: $sampleRate Hz
                    Bit Depth: $bitDepth bit
                    Source Channels: $sourceChannels
                    Lossless: $isLossless
                    Spatial Format: $spatialFormat
                    Codec: $mimeType
                    ${bitrate?.let { "Bitrate: ${it / 1000} kbps" } ?: ""}
                    Encoder Padding: $encoderPadding frames, $encoderDelay ms
                """.trimIndent()
        }
    }
    @OptIn(UnstableApi::class)
    data class AudioFormats(val downstreamFormat: Format?, val audioSinkInputFormat: Format?,
                            val audioTrackInfo: AudioTrackInfo?, val halFormat: AfFormatInfo?) {
	    fun prettyToString(context: Context): String? {
            if (downstreamFormat == null || audioSinkInputFormat == null || audioTrackInfo == null)
                return null
            // TODO localization
            return StringBuilder().apply {
                append("== Downstream format ==\n")
                prettyPrintFormat(context, downstreamFormat)
                append("\n")
                append("== Audio sink input format ==\n")
                prettyPrintFormat(context, audioSinkInputFormat)
                append("\n")
                append("== Audio track format ==\n")
                prettyPrintAudioTrackInfo(context, audioTrackInfo)
                append("\n")
                append("== Audio HAL format ==\n")
                if (halFormat == null)
                    append("(no data available)")
                else
                    prettyPrintAfFormatInfo(context, halFormat)
            }.toString()
        }

        private fun StringBuilder.prettyPrintFormat(context: Context, format: Format) {
            append("Sample rate: ")
            if (format.sampleRate != Format.NO_VALUE) {
                append(format.sampleRate)
                append(" Hz\n")
            } else {
                append("Not applicable to this format\n")
            }

            append("Bit depth: ")
            val bitDepth = try {
                Util.getByteDepth(format.pcmEncoding) * 8
            } catch (_: IllegalArgumentException) { null }
            if (bitDepth != null) {
                append(bitDepth)
                append(" bits (")
                append(Encoding.getString(context, format.pcmEncoding))
                append(")\n")
            } else {
                append("Not applicable to this format\n")
            }

            append("Channel count: ")
            if (format.channelCount != Format.NO_VALUE) {
                append(format.channelCount)
                append(" channels\n")
            } else {
                append("Not applicable to this format\n")
            }
        }

        private fun StringBuilder.prettyPrintAudioTrackInfo(context: Context, format: AudioTrackInfo) {
            append("Channel config: ${channelConfigToString(context, format.channelConfig)}\n")
            append("Sample rate: ${format.sampleRateHz} Hz\n")
            append("Audio format: ${Encoding.getString(context, format.encoding)}\n")
            append("Offload: ${format.offload}\n") // TODO this does not indicate real state
        }

        private fun StringBuilder.prettyPrintAfFormatInfo(context: Context, format: AfFormatInfo) {
            append("Device name: ${format.routedDeviceName}\n")
            append("Device type: ${format.routedDeviceType?.let { audioDeviceTypeToString(context, it) }}\n")
            append("Sample rate: ${format.sampleRateHz} Hz\n")
            append("Audio format: ${format.audioFormat}\n")
            append("Channel count: ${format.channelCount}\n")
        }
    }

    @OptIn(UnstableApi::class)
    fun detectAudioFormat(
        format: Format?,
        player: Player?
    ): AudioFormatInfo? {
        if (format == null) return null
        val bitrate = format.bitrate.takeIf { it != Format.NO_VALUE }
        val sampleRate = format.sampleRate.takeIf { it != Format.NO_VALUE }
        val bitDepth = try {
            Util.getByteDepth(format.pcmEncoding) * 8
        } catch (_: IllegalArgumentException) { null }
        val isLossless = isLosslessFormat(format.sampleMimeType)
        val spatialFormat = detectSpatialFormat(format)
        val sourceChannels = format.channelCount.takeIf { it != Format.NO_VALUE }

        val quality = determineQualityTier(
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            isLossless = isLossless
        )

        return AudioFormatInfo(
            quality = quality,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            isLossless = isLossless,
            sourceChannels = sourceChannels,
            bitrate = bitrate,
            mimeType = format.sampleMimeType,
            spatialFormat = spatialFormat,
            encoderPadding = format.encoderPadding.takeIf { it != Format.NO_VALUE },
            encoderDelay = format.encoderDelay.takeIf { it != Format.NO_VALUE }
        )
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

        // Standard multichannel formats
        // TODO can we just go by channel count? isn't there any way to distinguish QUAD
        //  from QUAD_BACK?
        //  answer: until https://github.com/androidx/media/issues/1471 happens we cannot
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

    fun audioDeviceTypeToString(context: Context, type: Int?) =
        when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> context.getString(R.string.device_type_bluetooth_a2dp)
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> context.getString(R.string.device_type_builtin_speaker)
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> context.getString(R.string.device_type_wired_headset)
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> context.getString(R.string.device_type_wired_headphones)
            AudioDeviceInfo.TYPE_HDMI -> context.getString(R.string.device_type_hdmi)
            AudioDeviceInfo.TYPE_USB_DEVICE -> context.getString(R.string.device_type_usb_device)
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> context.getString(R.string.device_type_usb_accessory)
            AudioDeviceInfo.TYPE_DOCK -> context.getString(
                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ) R.string.device_type_dock_digital
                else R.string.device_type_dock
            )
            AudioDeviceInfo.TYPE_DOCK_ANALOG -> context.getString(R.string.device_type_dock_analog)
            AudioDeviceInfo.TYPE_USB_HEADSET -> context.getString(R.string.device_type_usb_headset)
            AudioDeviceInfo.TYPE_HEARING_AID -> context.getString(R.string.device_type_hearing_aid)
            AudioDeviceInfo.TYPE_BLE_HEADSET -> context.getString(R.string.device_type_ble_headset)
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> context.getString(R.string.device_type_ble_broadcast)
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> context.getString(R.string.device_type_ble_speaker)
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> context.getString(R.string.device_type_line_digital)
            AudioDeviceInfo.TYPE_LINE_ANALOG -> context.getString(R.string.device_type_line_analog)
            AudioDeviceInfo.TYPE_AUX_LINE -> context.getString(R.string.device_type_aux_line)
            AudioDeviceInfo.TYPE_HDMI_ARC -> context.getString(R.string.device_type_hdmi_arc)
            AudioDeviceInfo.TYPE_HDMI_EARC -> context.getString(R.string.device_type_hdmi_earc)
            else -> {
                Log.w("AudioFormatDetector", "unknown device type $type")
                context.getString(R.string.device_type_unknown)
            }
        }

    private fun determineQualityTier(
        sampleRate: Int?,
        bitDepth: Int?,
        isLossless: Boolean?
    ): AudioQuality = when {
        isLossless == false -> AudioQuality.LOSSY

        // Hi-Res: 24bit+ and 96kHz+
        bitDepth != null && sampleRate != null &&
                bitDepth >= 24 && sampleRate >= 88200 -> AudioQuality.HIRES

        // HD: 24bit at standard rates OR 16bit at high rates
        bitDepth != null && sampleRate != null && (
                (bitDepth >= 24 && sampleRate in setOf(44100, 48000)) ||
                (bitDepth == 16 && sampleRate >= 88200)) -> AudioQuality.HD

        // CD: 16bit at standard rates
        bitDepth == 16 && sampleRate in setOf(44100, 48000) -> AudioQuality.CD

        // Fallback for non-standard combinations
        else -> AudioQuality.UNKNOWN
    }
}