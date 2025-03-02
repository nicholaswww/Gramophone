package org.akanework.gramophone.logic.utils

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink.AudioTrackConfig
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.parcelize.Parcelize
import org.akanework.gramophone.logic.platformmedia.AudioFormatDescription

@Parcelize
data class AfFormatInfo(val routedDeviceName: String?, val routedDeviceType: Int?,
                        val ioHandle: Int?, val sampleRateHz: Int?, val audioFormat: String?,
                        val channelCount: Int?) : Parcelable

// TODO the offload may not actually be respected by AF, but how do we find out if it is or isn't?
@Parcelize
data class AudioTrackInfo(val encoding: Int, val sampleRateHz: Int, val channelConfig: Int,
	val offload: Boolean) : Parcelable {
	companion object {
		@OptIn(UnstableApi::class)
		fun fromMedia3AudioTrackConfig(config: AudioTrackConfig) =
			AudioTrackInfo(config.encoding, config.sampleRate, config.channelConfig,
				config.offload)
	}
}

@UnstableApi
class AfFormatTracker(private val context: Context, private val playbackHandler: Handler)
	: AnalyticsListener {
	companion object {
		private const val TAG = "AfFormatTracker"
		init {
			try {
				System.loadLibrary("gramophone")
			} catch (e: Throwable) {
				Log.e(TAG, Log.getStackTraceString(e))
			}
		}

		@SuppressLint("DiscouragedPrivateApi")
		private fun getAudioTrackPtr(audioTrack: AudioTrack): Long {
			val cls = audioTrack.javaClass
			val field = cls.getDeclaredField("mNativeTrackInJavaObj")
			field.isAccessible = true
			return field.get(audioTrack) as Long
		}

		private fun getHalSampleRate(audioTrack: AudioTrack): Int? {
			if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
				throw IllegalArgumentException("cannot get hal sample rate for released AudioTrack")
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
				val ret = try {
					getHalSampleRateInternal(getAudioTrackPtr(audioTrack))
				} catch (e: Throwable) {
					Log.e(TAG, Log.getStackTraceString(e))
					null
				}
				if (ret != null && ret != 0)
					return ret
				return null
			}
			val output = getOutput(audioTrack)
			if (output == null)
				return null
			val af = getAfService()
			if (af == null)
				return null
			val inParcel = obtainParcel(af)
			val outParcel = obtainParcel(af)
			try {
				inParcel.writeInterfaceToken(af.interfaceDescriptor!!)
				inParcel.writeInt(output)
				af.transact(3, inParcel, outParcel, 0)
				if (!readStatus(outParcel))
					return null
				return outParcel.readInt()
			} finally {
				inParcel.recycle()
				outParcel.recycle()
			}
		}
		private external fun getHalSampleRateInternal(@Suppress("unused") audioTrackPtr: Long): Int

		private fun obtainParcel(binder: IBinder) =
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
				Parcel.obtain(binder) else Parcel.obtain()

		private fun getHalChannelCount(audioTrack: AudioTrack): Int? {
			if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
				throw IllegalArgumentException("cannot get hal channel count for released AudioTrack")
			return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) null else
				try {
					getHalChannelCountInternal(getAudioTrackPtr(audioTrack))
				} catch (e: Throwable) {
					Log.e(TAG, Log.getStackTraceString(e))
					null
				}
		}
		private external fun getHalChannelCountInternal(@Suppress("unused") audioTrackPtr: Long): Int

		private fun getHalFormat(audioTrack: AudioTrack): String? {
			if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
				throw IllegalArgumentException("cannot get hal format for released AudioTrack")
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
				val ret = try {
					getHalFormatInternal(getAudioTrackPtr(audioTrack))
				} catch (e: Throwable) {
					Log.e(TAG, Log.getStackTraceString(e))
					null
				}
				if (ret != null && ret != 0)
					return audioFormatToString(ret.toUInt())
				return null
			}
			val output = getOutput(audioTrack)
			if (output == null)
				return null
			val af = getAfService()
			if (af == null)
				return null
			val inParcel = obtainParcel(af)
			val outParcel = obtainParcel(af)
			try {
				inParcel.writeInterfaceToken(af.interfaceDescriptor!!)
				inParcel.writeInt(output)
				af.transact(
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 4 else 5,
					inParcel, outParcel, 0
				)
				if (!readStatus(outParcel))
					return null
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
					val format = try {
						AudioFormatDescription.CREATOR.createFromParcel(outParcel)
					} catch (e: Throwable) {
						Log.e(TAG, Log.getStackTraceString(e))
						return null
					}
					return simplifyAudioFormatDescription(format)?.let { audioFormatToString(it.toUInt()) }
				} else
					return audioFormatToString(outParcel.readInt().toUInt())
			} finally {
				inParcel.recycle()
				outParcel.recycle()
			}
		}
		private external fun getHalFormatInternal(@Suppress("unused") audioTrackPtr: Long): Int

		private fun audioFormatToString(audioFormat: UInt): String {
			return when (audioFormat) {
				0xFFFFFFFFU -> /* AUDIO_FORMAT_INVALID         */
				"AUDIO_FORMAT_INVALID"
				0U -> /* AUDIO_FORMAT_DEFAULT         */
				"AUDIO_FORMAT_DEFAULT"
				0x01000000U -> /* AUDIO_FORMAT_MP3             */
				"AUDIO_FORMAT_MP3"
				0x02000000U -> /* AUDIO_FORMAT_AMR_NB          */
				"AUDIO_FORMAT_AMR_NB"
				0x03000000U -> /* AUDIO_FORMAT_AMR_WB          */
				"AUDIO_FORMAT_AMR_WB"
				0x04000000U -> /* AUDIO_FORMAT_AAC             */
				"AUDIO_FORMAT_AAC"
				0x05000000U -> /* AUDIO_FORMAT_HE_AAC_V1       */
				"AUDIO_FORMAT_HE_AAC_V1"
				0x06000000U -> /* AUDIO_FORMAT_HE_AAC_V2       */
				"AUDIO_FORMAT_HE_AAC_V2"
				0x07000000U -> /* AUDIO_FORMAT_VORBIS          */
				"AUDIO_FORMAT_VORBIS"
				0x08000000U -> /* AUDIO_FORMAT_OPUS            */
				"AUDIO_FORMAT_OPUS"
				0x09000000U -> /* AUDIO_FORMAT_AC3             */
				"AUDIO_FORMAT_AC3"
				0x0A000000U -> /* AUDIO_FORMAT_E_AC3           */
				"AUDIO_FORMAT_E_AC3"
				0x0B000000U -> /* AUDIO_FORMAT_DTS             */
				"AUDIO_FORMAT_DTS"
				0x0C000000U -> /* AUDIO_FORMAT_DTS_HD          */
				"AUDIO_FORMAT_DTS_HD"
				0x0D000000U -> /* AUDIO_FORMAT_IEC61937        */
				"AUDIO_FORMAT_IEC61937"
				0x0E000000U -> /* AUDIO_FORMAT_DOLBY_TRUEHD    */
				"AUDIO_FORMAT_DOLBY_TRUEHD"
				0x10000000U -> /* AUDIO_FORMAT_EVRC            */
				"AUDIO_FORMAT_EVRC"
				0x11000000U -> /* AUDIO_FORMAT_EVRCB           */
				"AUDIO_FORMAT_EVRCB"
				0x12000000U -> /* AUDIO_FORMAT_EVRCWB          */
				"AUDIO_FORMAT_EVRCWB"
				0x13000000U -> /* AUDIO_FORMAT_EVRCNW          */
				"AUDIO_FORMAT_EVRCNW"
				0x14000000U -> /* AUDIO_FORMAT_AAC_ADIF        */
				"AUDIO_FORMAT_AAC_ADIF"
				0x15000000U -> /* AUDIO_FORMAT_WMA             */
				"AUDIO_FORMAT_WMA"
				0x16000000U -> /* AUDIO_FORMAT_WMA_PRO         */
				"AUDIO_FORMAT_WMA_PRO"
				0x17000000U -> /* AUDIO_FORMAT_AMR_WB_PLUS     */
				"AUDIO_FORMAT_AMR_WB_PLUS"
				0x18000000U -> /* AUDIO_FORMAT_MP2             */
				"AUDIO_FORMAT_MP2"
				0x19000000U -> /* AUDIO_FORMAT_QCELP           */
				"AUDIO_FORMAT_QCELP"
				0x1A000000U -> /* AUDIO_FORMAT_DSD             */
				"AUDIO_FORMAT_DSD"
				0x1B000000U -> /* AUDIO_FORMAT_FLAC            */
				"AUDIO_FORMAT_FLAC"
				0x1C000000U -> /* AUDIO_FORMAT_ALAC            */
				"AUDIO_FORMAT_ALAC"
				0x1D000000U -> /* AUDIO_FORMAT_APE             */
				"AUDIO_FORMAT_APE"
				0x1E000000U -> /* AUDIO_FORMAT_AAC_ADTS        */
				"AUDIO_FORMAT_AAC_ADTS"
				0x1F000000U -> /* AUDIO_FORMAT_SBC             */
				"AUDIO_FORMAT_SBC"
				0x20000000U -> /* AUDIO_FORMAT_APTX            */
				"AUDIO_FORMAT_APTX"
				0x21000000U -> /* AUDIO_FORMAT_APTX_HD         */
				"AUDIO_FORMAT_APTX_HD"
				0x22000000U -> /* AUDIO_FORMAT_AC4             */
				"AUDIO_FORMAT_AC4"
				0x23000000U -> /* AUDIO_FORMAT_LDAC            */
				"AUDIO_FORMAT_LDAC"
				0x24000000U -> /* AUDIO_FORMAT_MAT             */
				"AUDIO_FORMAT_MAT"
				0x25000000U -> /* AUDIO_FORMAT_AAC_LATM        */
				"AUDIO_FORMAT_AAC_LATM"
				0x26000000U -> /* AUDIO_FORMAT_CELT            */
				"AUDIO_FORMAT_CELT"
				0x27000000U -> /* AUDIO_FORMAT_APTX_ADAPTIVE   */
				"AUDIO_FORMAT_APTX_ADAPTIVE"
				0x28000000U -> /* AUDIO_FORMAT_LHDC            */
				"AUDIO_FORMAT_LHDC"
				0x29000000U -> /* AUDIO_FORMAT_LHDC_LL         */
				"AUDIO_FORMAT_LHDC_LL"
				0x2A000000U -> /* AUDIO_FORMAT_APTX_TWSP       */
				"AUDIO_FORMAT_APTX_TWSP"
				0x2B000000U -> /* AUDIO_FORMAT_LC3             */
				"AUDIO_FORMAT_LC3"
				0x2C000000U -> /* AUDIO_FORMAT_MPEGH           */
				"AUDIO_FORMAT_MPEGH"
				0x2D000000U -> /* AUDIO_FORMAT_IEC60958        */
				"AUDIO_FORMAT_IEC60958"
				0x2E000000U -> /* AUDIO_FORMAT_DTS_UHD         */
				"AUDIO_FORMAT_DTS_UHD"
				0x2F000000U -> /* AUDIO_FORMAT_DRA             */
				"AUDIO_FORMAT_DRA"
				0x30000000U -> /* AUDIO_FORMAT_APTX_ADAPTIVE_QLEA */
				"AUDIO_FORMAT_APTX_ADAPTIVE_QLEA"
				0x31000000U -> /* AUDIO_FORMAT_APTX_ADAPTIVE_R4   */
				"AUDIO_FORMAT_APTX_ADAPTIVE_R4"
				0x32000000U -> /* AUDIO_FORMAT_DTS_HD_MA       */
				"AUDIO_FORMAT_DTS_HD_MA"
				0x33000000U -> /* AUDIO_FORMAT_DTS_UHD_P2      */
				"AUDIO_FORMAT_DTS_UHD_P2"

				/* Aliases */
				0x1U -> /* AUDIO_FORMAT_PCM_16_BIT        */
				"AUDIO_FORMAT_PCM_16_BIT"        // (PCM | PCM_SUB_16_BIT)
				0x2U -> /* AUDIO_FORMAT_PCM_8_BIT         */
				"AUDIO_FORMAT_PCM_8_BIT"        // (PCM | PCM_SUB_8_BIT)
				0x3U -> /* AUDIO_FORMAT_PCM_32_BIT        */
				"AUDIO_FORMAT_PCM_32_BIT"        // (PCM | PCM_SUB_32_BIT)
				0x4U -> /* AUDIO_FORMAT_PCM_8_24_BIT      */
				"AUDIO_FORMAT_PCM_8_24_BIT"        // (PCM | PCM_SUB_8_24_BIT)
				0x5U -> /* AUDIO_FORMAT_PCM_FLOAT         */
				"AUDIO_FORMAT_PCM_FLOAT"        // (PCM | PCM_SUB_FLOAT)
				0x6U -> /* AUDIO_FORMAT_PCM_24_BIT_PACKED */
				"AUDIO_FORMAT_PCM_24_BIT_PACKED"        // (PCM | PCM_SUB_24_BIT_PACKED)
				0x4000001U -> /* AUDIO_FORMAT_AAC_MAIN          */
				"AUDIO_FORMAT_AAC_MAIN"  // (AAC | AAC_SUB_MAIN)
				0x4000002U -> /* AUDIO_FORMAT_AAC_LC            */
				"AUDIO_FORMAT_AAC_LC"  // (AAC | AAC_SUB_LC)
				0x4000004U -> /* AUDIO_FORMAT_AAC_SSR           */
				"AUDIO_FORMAT_AAC_SSR"  // (AAC | AAC_SUB_SSR)
				0x4000008U -> /* AUDIO_FORMAT_AAC_LTP           */
				"AUDIO_FORMAT_AAC_LTP"  // (AAC | AAC_SUB_LTP)
				0x4000010U -> /* AUDIO_FORMAT_AAC_HE_V1         */
				"AUDIO_FORMAT_AAC_HE_V1"  // (AAC | AAC_SUB_HE_V1)
				0x4000020U -> /* AUDIO_FORMAT_AAC_SCALABLE      */
				"AUDIO_FORMAT_AAC_SCALABLE"  // (AAC | AAC_SUB_SCALABLE)
				0x4000040U -> /* AUDIO_FORMAT_AAC_ERLC          */
				"AUDIO_FORMAT_AAC_ERLC"  // (AAC | AAC_SUB_ERLC)
				0x4000080U -> /* AUDIO_FORMAT_AAC_LD            */
				"AUDIO_FORMAT_AAC_LD"  // (AAC | AAC_SUB_LD)
				0x4000100U -> /* AUDIO_FORMAT_AAC_HE_V2         */
				"AUDIO_FORMAT_AAC_HE_V2"  // (AAC | AAC_SUB_HE_V2)
				0x4000200U -> /* AUDIO_FORMAT_AAC_ELD           */
				"AUDIO_FORMAT_AAC_ELD"  // (AAC | AAC_SUB_ELD)
				0x4000300U -> /* AUDIO_FORMAT_AAC_XHE           */
				"AUDIO_FORMAT_AAC_XHE"  // (AAC | AAC_SUB_XHE)
				0x1e000001U -> /* AUDIO_FORMAT_AAC_ADTS_MAIN     */
				"AUDIO_FORMAT_AAC_ADTS_MAIN" // (AAC_ADTS | AAC_SUB_MAIN)
				0x1e000002U -> /* AUDIO_FORMAT_AAC_ADTS_LC       */
				"AUDIO_FORMAT_AAC_ADTS_LC" // (AAC_ADTS | AAC_SUB_LC)
				0x1e000004U -> /* AUDIO_FORMAT_AAC_ADTS_SSR      */
				"AUDIO_FORMAT_AAC_ADTS_SSR" // (AAC_ADTS | AAC_SUB_SSR)
				0x1e000008U -> /* AUDIO_FORMAT_AAC_ADTS_LTP      */
				"AUDIO_FORMAT_AAC_ADTS_LTP" // (AAC_ADTS | AAC_SUB_LTP)
				0x1e000010U -> /* AUDIO_FORMAT_AAC_ADTS_HE_V1    */
				"AUDIO_FORMAT_AAC_ADTS_HE_V1" // (AAC_ADTS | AAC_SUB_HE_V1)
				0x1e000020U -> /* AUDIO_FORMAT_AAC_ADTS_SCALABLE */
				"AUDIO_FORMAT_AAC_ADTS_SCALABLE" // (AAC_ADTS | AAC_SUB_SCALABLE)
				0x1e000040U -> /* AUDIO_FORMAT_AAC_ADTS_ERLC     */
				"AUDIO_FORMAT_AAC_ADTS_ERLC" // (AAC_ADTS | AAC_SUB_ERLC)
				0x1e000080U -> /* AUDIO_FORMAT_AAC_ADTS_LD       */
				"AUDIO_FORMAT_AAC_ADTS_LD" // (AAC_ADTS | AAC_SUB_LD)
				0x1e000100U -> /* AUDIO_FORMAT_AAC_ADTS_HE_V2    */
				"AUDIO_FORMAT_AAC_ADTS_HE_V2" // (AAC_ADTS | AAC_SUB_HE_V2)
				0x1e000200U -> /* AUDIO_FORMAT_AAC_ADTS_ELD      */
				"AUDIO_FORMAT_AAC_ADTS_ELD" // (AAC_ADTS | AAC_SUB_ELD)
				0x1e000300U -> /* AUDIO_FORMAT_AAC_ADTS_XHE      */
				"AUDIO_FORMAT_AAC_ADTS_XHE" // (AAC_ADTS | AAC_SUB_XHE)
				0x25000002U -> /* AUDIO_FORMAT_AAC_LATM_LC       */
				"AUDIO_FORMAT_AAC_LATM_LC" // (AAC_LATM | AAC_SUB_LC)
				0x25000010U -> /* AUDIO_FORMAT_AAC_LATM_HE_V1    */
				"AUDIO_FORMAT_AAC_LATM_HE_V1" // (AAC_LATM | AAC_SUB_HE_V1)
				0x25000100U -> /* AUDIO_FORMAT_AAC_LATM_HE_V2    */
				"AUDIO_FORMAT_AAC_LATM_HE_V2" // (AAC_LATM | AAC_SUB_HE_V2)
				0xA000001U -> /* AUDIO_FORMAT_E_AC3_JOC         */
				"AUDIO_FORMAT_E_AC3_JOC"  // (E_AC3 | E_AC3_SUB_JOC)
				0x24000001U -> /* AUDIO_FORMAT_MAT_1_0           */
				"AUDIO_FORMAT_MAT_1_0" // (MAT | MAT_SUB_1_0)
				0x24000002U -> /* AUDIO_FORMAT_MAT_2_0           */
				"AUDIO_FORMAT_MAT_2_0" // (MAT | MAT_SUB_2_0)
				0x24000003U -> /* AUDIO_FORMAT_MAT_2_1           */
				"AUDIO_FORMAT_MAT_2_1" // (MAT | MAT_SUB_2_1)
				0x2C000013U -> /* AUDIO_FORMAT_MPEGH_SUB_BL_L3   */
				"AUDIO_FORMAT_MPEGH_SUB_BL_L3"
				0x2C000014U -> /* AUDIO_FORMAT_MPEGH_SUB_BL_L4   */
				"AUDIO_FORMAT_MPEGH_SUB_BL_L4"
				0x2C000023U -> /* AUDIO_FORMAT_MPEGH_SUB_LC_L3   */
				"AUDIO_FORMAT_MPEGH_SUB_LC_L3"
				0x2C000024U -> /* AUDIO_FORMAT_MPEGH_SUB_LC_L4   */
				"AUDIO_FORMAT_MPEGH_SUB_LC_L4"
				else ->
				"AUDIO_FORMAT_($audioFormat)"
			}
		}

		@SuppressLint("PrivateApi") // only Android T, private API stability TODO verify
		private fun simplifyAudioFormatDescription(aidl: AudioFormatDescription): Int? {
			return try {
				Class.forName("android.media.audio.common.AidlConversion").getMethod(
					"aidl2legacy_AudioFormatDescription_audio_format_t", Int::class.java
				).invoke(null, aidl) as Int
			} catch (e: Throwable) {
				Log.e(TAG, Log.getStackTraceString(e))
				null
			}
		}

		private fun getOutput(audioTrack: AudioTrack): Int? {
			if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
				throw IllegalArgumentException("cannot get hal output for released AudioTrack")
			return try {
				getOutputInternal(getAudioTrackPtr(audioTrack))
			} catch (e: Throwable) {
				Log.e(TAG, Log.getStackTraceString(e))
				null
			}
		}
		private external fun getOutputInternal(@Suppress("unused") audioTrackPtr: Long): Int

		@SuppressLint("PrivateApi")
		private fun getAfService(): IBinder? {
			return try {
				Class.forName("android.os.ServiceManager").getMethod(
					"getService", String::class.java
				).invoke(null, "media.audio_flinger") as IBinder?
			} catch (e: Throwable) {
				Log.e(TAG, Log.getStackTraceString(e))
				null
			}
		}

		private fun readStatus(parcel: Parcel): Boolean {
			if (Build.VERSION.SDK_INT < 31) return true
			val status = parcel.readInt()
			if (status == 0) return true
			Log.e(TAG, "binder transaction failed with status $status")
			return false
		}
	}
	// only access sink or track on PlaybackThread
	private var lastAudioTrack: AudioTrack? = null
	private var audioSink: DefaultAudioSink? = null
	var format: AfFormatInfo? = null
		private set
	var formatChangedCallback: ((AfFormatInfo?) -> Unit)? = null

	private val routingChangedListener: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
		object : AudioRouting.OnRoutingChangedListener {
			override fun onRoutingChanged(router: AudioRouting) {
				this@AfFormatTracker.onRoutingChanged(router as AudioTrack)
			}
		}
	} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
		@Suppress("deprecation")
		object : AudioTrack.OnRoutingChangedListener {
			override fun onRoutingChanged(router: AudioTrack) {
				this@AfFormatTracker.onRoutingChanged(router)
			}
		}
	} else null

	private fun onRoutingChanged(router: AudioTrack) {
		val audioTrack = (audioSink ?: throw NullPointerException(
			"audioSink is null in onAudioTrackInitialized"
		)).getAudioTrack()
		if (router !== audioTrack) return // stale callback
		if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED) return
		buildFormat(audioTrack)
	}

	// TODO why do we have to reflect on app code, there must be a better solution
	private fun DefaultAudioSink.getAudioTrack(): AudioTrack? {
		val cls = javaClass
		val field = cls.getDeclaredField("audioTrack")
		field.isAccessible = true
		return field.get(this) as AudioTrack?
	}

	fun setAudioSink(sink: DefaultAudioSink) {
		this.audioSink = sink
	}

	override fun onAudioTrackInitialized(
		eventTime: AnalyticsListener.EventTime,
		audioTrackConfig: AudioTrackConfig
	) {
		format = null
		playbackHandler.post {
			val audioTrack = (audioSink ?: throw NullPointerException(
				"audioSink is null in onAudioTrackInitialized"
			)).getAudioTrack()
			if (audioTrack != lastAudioTrack) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
					lastAudioTrack?.removeOnRoutingChangedListener(
						routingChangedListener as AudioRouting.OnRoutingChangedListener
					)
				} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					@Suppress("deprecation")
					lastAudioTrack?.removeOnRoutingChangedListener(
						routingChangedListener as AudioTrack.OnRoutingChangedListener
					)
				}
				this.lastAudioTrack = audioTrack
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
					audioTrack?.addOnRoutingChangedListener(
						routingChangedListener as AudioRouting.OnRoutingChangedListener,
						playbackHandler
					)
				} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					@Suppress("deprecation")
					audioTrack?.addOnRoutingChangedListener(
						routingChangedListener as AudioTrack.OnRoutingChangedListener,
						playbackHandler
					)
				}
			}
			buildFormat(audioTrack)
		}
	}

	private fun buildFormat(audioTrack: AudioTrack?) {
		audioTrack?.let {
			val rd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
				audioTrack.routedDevice else null
			val pn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
				rd!!.productName.toString() else null
			val t = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
				rd!!.type else null
			if (rd != MediaRoutes.getSelectedAudioDevice(context))
				Log.w(TAG, "routedDevice is not the same as MediaRoute selected device")
			AfFormatInfo(
				pn, t,
				getOutput(audioTrack), getHalSampleRate(audioTrack),
				getHalFormat(audioTrack), getHalChannelCount(audioTrack)
			)
		}.let {
			format = it
			formatChangedCallback?.invoke(it)
		}
	}
}