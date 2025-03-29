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

@Parcelize
data class AfFormatInfo(
    val routedDeviceName: String?, val routedDeviceId: Int?,
    val routedDeviceType: Int?, val mixPortId: Int?, val mixPortName: String?,
    val mixPortFlags: Int?, val ioHandle: Int?, val sampleRateHz: Int?,
    val audioFormat: String?, val channelCount: Int?, val channelMask: Int?,
    val grantedFlags: Int?, val policyPortId: Int?, val afTrackFlags: Int?
) : Parcelable

data class MyMixPort(val id: Int?, val name: String?, val flags: Int?, val channelMask: Int?, val format: Int?)

@Parcelize
data class AudioTrackInfo(
    val encoding: Int, val sampleRateHz: Int, val channelConfig: Int,
    val offload: Boolean
) : Parcelable {
    companion object {
        @OptIn(UnstableApi::class)
        fun fromMedia3AudioTrackConfig(config: AudioTrackConfig) =
            AudioTrackInfo(
                config.encoding, config.sampleRate, config.channelConfig,
                config.offload
            )
    }
}

@OptIn(UnstableApi::class)
class AfFormatTracker(
    private val context: Context, private val playbackHandler: Handler,
    private val handler: Handler
) : AnalyticsListener {
    companion object {
        private const val TAG = "AfFormatTracker"
        private const val TRACE_TAG = "GpNativeTrace"
        private const val LOG_EVENTS = true

        init {
            try {
                Log.d(TRACE_TAG, "Loading libgramophone.so")
                System.loadLibrary("gramophone")
                Log.d(TRACE_TAG, "Done loading libgramophone.so")
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }

        @SuppressLint("DiscouragedPrivateApi")
        private fun getAudioTrackPtr(audioTrack: AudioTrack): Long {
            if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
                throw IllegalArgumentException("cannot get pointer for released AudioTrack")
            val cls = audioTrack.javaClass
            val field = cls.getDeclaredField("mNativeTrackInJavaObj")
            field.isAccessible = true
            return field.get(audioTrack) as Long
        }

        private fun getHalSampleRate(audioTrack: AudioTrack): Int? {
            if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
                throw IllegalArgumentException("cannot get hal sample rate for released AudioTrack")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // getHalSampleRate() exists since below commit which first appeared in Android U
                // https://cs.android.com/android/_/android/platform/frameworks/av/+/310037a32d56e361d5b5156b74f8846f92bc245e
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
                // IAudioFlingerService.sampleRate(audio_io_handle_t)
                Log.d(TRACE_TAG, "trying to call sampleRate() via binder")
                try {
                    af.transact(3, inParcel, outParcel, 0)
                } catch (e: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    return null
                }
                Log.d(TRACE_TAG, "done calling format() via binder")
                if (!readStatus(outParcel))
                    return null
                return outParcel.readInt()
            } finally {
                inParcel.recycle()
                outParcel.recycle()
            }
        }

        private external fun getHalSampleRateInternal(@Suppress("unused") audioTrackPtr: Long): Int

        internal fun obtainParcel(binder: IBinder) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Parcel.obtain(binder) else Parcel.obtain()

        private fun getHalChannelCount(audioTrack: AudioTrack): Int? {
            if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
                throw IllegalArgumentException("cannot get hal channel count for released AudioTrack")
            // before U, caller should query channel mask from audio_port/audio_port_v7
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) null else
            // getHalChannelCount() exists since below commit which first appeared in Android U
            // https://cs.android.com/android/_/android/platform/frameworks/av/+/310037a32d56e361d5b5156b74f8846f92bc245e
                try {
                    Log.d(TRACE_TAG, "calling native getHalChannelCountInternal/getAudioTrackPtr")
                    getHalChannelCountInternal(getAudioTrackPtr(audioTrack))
                } catch (e: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    null
                }.also { Log.d(TRACE_TAG, "native getHalChannelCountInternal/getAudioTrackPtr is done: $it") }
        }

        private external fun getHalChannelCountInternal(@Suppress("unused") audioTrackPtr: Long): Int

        private fun getHalFormat(audioTrack: AudioTrack): String? {
            if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
                throw IllegalArgumentException("cannot get hal format for released AudioTrack")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // getHalFormat() exists since below commit which first appeared in Android U
                // https://cs.android.com/android/_/android/platform/frameworks/av/+/310037a32d56e361d5b5156b74f8846f92bc245e
                val ret = try {
                    Log.d(TRACE_TAG, "calling native getHalFormatInternal/getAudioTrackPtr")
                    getHalFormatInternal(getAudioTrackPtr(audioTrack))
                } catch (e: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    null
                }.also { Log.d(TRACE_TAG, "native getHalChannelCountInternal/getAudioTrackPtr is done: $it") }
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
                // IAudioFlingerService.format(audio_io_handle_t)
                Log.d(TRACE_TAG, "trying to call format() via binder")
                try {
                    af.transact(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 4 else 5,
                        inParcel, outParcel, 0
                    )
                } catch (e: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    return null
                }
                Log.d(TRACE_TAG, "done calling format() via binder")
                if (!readStatus(outParcel))
                    return null
                // In T, return value changed from legacy audio_format_t to AudioFormatDescription
                // https://cs.android.com/android/_/android/platform/frameworks/av/+/b60bd1b586b74ddf375257c4d07323e271d84ff3
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (outParcel.readInt() != 1 /* kNonNullParcelableFlag */) {
                        Log.e(TAG, "got a null parcelable unexpectedly")
                        return null
                    }
                    return simplifyAudioFormatDescription(outParcel)?.let { audioFormatToString(it.toUInt()) }
                } else
                    return audioFormatToString(outParcel.readInt().toUInt())
            } finally {
                inParcel.recycle()
                outParcel.recycle()
            }
        }

        private external fun getHalFormatInternal(@Suppress("unused") audioTrackPtr: Long): Int

        private fun audioFormatToString(audioFormat: UInt): String {
            for (encoding in AudioFormatDetector.Encoding.entries) {
                if (encoding.isSupportedAsNative && encoding.native == audioFormat)
                    encoding.enc2?.let { return it }
            }
            return "AUDIO_FORMAT_($audioFormat)"
        }

        @SuppressLint("PrivateApi") // sorry, not sorry...
        private fun listAudioPorts(): Pair<List<Any>, Int>? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                return null // while listAudioPorts exists in L, it just returns an error
            val ports = ArrayList<Any?>()
            val generation = IntArray(1)
            try {
                Class.forName("android.media.AudioSystem").getMethod(
                    "listAudioPorts", ArrayList::class.java, IntArray::class.java
                ).invoke(null, ports, generation) as Int
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                return null
            }
            if (ports.contains(null))
                Log.e(TAG, "why does listAudioPorts() return a null port?!")
            return ports.filterNotNull() to generation[0]
        }

        @SuppressLint("PrivateApi") // only Android T, private API stability
        private fun simplifyAudioFormatDescription(out: Parcel): Int? {
            return try {
                Class.forName("android.media.audio.common.AidlConversion").getDeclaredMethod(
                    "aidl2legacy_AudioFormatDescription_Parcel_audio_format_t", Parcel::class.java
                ).also {
                    it.isAccessible = true
                }.invoke(null, out) as Int
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                null
            }
        }

        private fun findAfFlagsForPort(id: Int, sr: Int, type: Int): Int? {
            // flags exposed to app process since below commit which first appeared in T release.
            // https://cs.android.com/android/_/android/platform/frameworks/av/+/99809024b36b243ad162c780c1191bb503a8df47
            if (type == 0 && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                return null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                return null // need listAudioPorts or getAudioPort
            return try {
                Log.d(TRACE_TAG, "calling native findAfFlagsForPortInternal")
                findAfFlagsForPortInternal(id, sr, type).let {
                    if (it == Int.MAX_VALUE || it == Int.MIN_VALUE)
                        null // something went wrong, this was logged to logcat
                    else it
                }.also { Log.d(TRACE_TAG, "native findAfFlagsForPortInternal is done: $it") }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                null
            }
        }
        @Suppress("unused") // for parameters
        private external fun findAfFlagsForPortInternal(id: Int, sr: Int, type: Int): Int

        private fun findAfTrackFlags(dump: String?, latency: Int?, track: AudioTrack, grantedFlags: Int?): Int? {
            // First exposure to client process was below commit, which first appeared in U QPR2.
            // https://cs.android.com/android/_/android/platform/frameworks/av/+/94ed47c6b6ca5a69b90238f6ae97af2ce7df9be0
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                return null
            try {
                val dump = dump ?: throw NullPointerException("af track dump is null, check prior logs")
                val latency = latency ?: throw NullPointerException("af track latency is null, check prior logs")
                val theLine = dump.split('\n').first { it.contains("AF SampleRate") }
                val theLine2 = dump.split('\n').first { it.contains("format(0x") }
                val regex = Regex(".*AF latency \\(([0-9]+)\\) AF frame count\\(([0-9]+)\\) AF SampleRate\\(([0-9]+)\\).*")
                val regex2 = Regex(".*format\\(0x([0-9a-f]+)\\), .*")
                val match = regex.matchEntire(theLine) ?: throw NullPointerException("failed match of $theLine")
                val match2 = regex2.matchEntire(theLine2) ?: throw NullPointerException("failed match2 of $theLine2")
                val afLatency = match.groupValues.getOrNull(1)?.toIntOrNull()
                    ?: throw NullPointerException("failed parsing afLatency in: $theLine")
                val afFrameCount = match.groupValues.getOrNull(2)?.toLongOrNull()
                    ?: throw NullPointerException("failed parsing afFrameCount in: $theLine")
                val afSampleRate = match.groupValues.getOrNull(3)?.toIntOrNull()
                    ?: throw NullPointerException("failed parsing afSampleRate in: $theLine")
                val format = match2.groupValues.getOrNull(1)?.toUIntOrNull(radix = 16)?.toInt()
                    ?: throw NullPointerException("failed parsing format in: $theLine2")
                val ptr = getAudioTrackPtr(track)
                Log.d(TRACE_TAG, "calling native findAfTrackFlagsInternal")
                return findAfTrackFlagsInternal(ptr, afLatency, afFrameCount, afSampleRate, latency, format).let {
                    if (it == Int.MAX_VALUE || it == Int.MIN_VALUE)
                        null // something went wrong, this was logged to logcat
                    else if (grantedFlags != null && (it or grantedFlags) != it) {
                        // should never happen
                        Log.e(TAG, "af track flags($it) are nonsense, |$grantedFlags = ${it or grantedFlags}")
                        null
                    } else it
                }.also { Log.d(TRACE_TAG, "native findAfTrackFlagsInternal is done: $it") }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                return null
            }
        }
        @Suppress("unused") // for parameters
        private external fun findAfTrackFlagsInternal(pointer: Long, afLatency: Int, afFrameCount: Long,
                                                      afSampleRate: Int, latency: Int, format: Int): Int

        private fun getOutput(audioTrack: AudioTrack): Int? {
            if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
                throw IllegalArgumentException("cannot get hal output for released AudioTrack")
            Log.d(TRACE_TAG, "calling native getOutputInternal/getAudioTrackPtr")
            return try {
                getOutputInternal(getAudioTrackPtr(audioTrack))
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                null
            }.also { Log.d(TRACE_TAG, "native getOutputInternal/getAudioTrackPtr is done: $it") }
        }

        private external fun getOutputInternal(@Suppress("unused") audioTrackPtr: Long): Int

        private fun dump(audioTrack: AudioTrack): String? {
            if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
                throw IllegalArgumentException("cannot dump released AudioTrack")
            Log.d(TRACE_TAG, "calling native dump/getAudioTrackPtr")
            return try {
                dumpInternal(getAudioTrackPtr(audioTrack))
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                null
            }.also { Log.d(TRACE_TAG, "native dump/getAudioTrackPtr is done: $it") }
        }

        /*private*/ external fun dumpInternal(@Suppress("unused") audioTrackPtr: Long): String

        @SuppressLint("PrivateApi") // only used below U, stable private API
        internal fun getAfService(): IBinder? {
            return try {
                Class.forName("android.os.ServiceManager").getMethod(
                    "getService", String::class.java
                ).invoke(null, "media.audio_flinger") as IBinder?
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                null
            }
        }

        internal fun readStatus(parcel: Parcel): Boolean {
            if (Build.VERSION.SDK_INT < 31) return true
            val status = parcel.readInt()
            if (status == 0) return true
            Log.e(TAG, "binder transaction failed with status $status")
            return false
        }

        fun getMixPortForThread(oid: Int, sampleRate: Int): MyMixPort? {
            val ports = listAudioPorts()
            if (ports != null)
                for (port in ports.first) {
                    try {
                        if (port.javaClass.canonicalName != "android.media.AudioMixPort") continue
                        val ioHandle = port.javaClass.getMethod("ioHandle").invoke(port) as Int
                        if (ioHandle != oid) continue
                        val id = port.javaClass.getMethod("id").invoke(port) as Int
                        val name = port.javaClass.getMethod("name").invoke(port) as String?
                        val flags = findAfFlagsForPort(id, sampleRate, 0)
                        val channelMask = findAfFlagsForPort(id, sampleRate, 1)
                        val format = findAfFlagsForPort(id, sampleRate, 2)
                        return MyMixPort(id, name, flags, channelMask, format)
                    } catch (t: Throwable) {
                        Log.e(TAG, Log.getStackTraceString(t))
                    }
                }
            return null
        }

        private val idRegex = Regex(".*id\\((.*)\\) .*")
        fun getPortIdFromDump(dump: String?): Int? {
            if (dump == null)
                return null
            // ID is in dump output since below commit which first appeared in Q.
            // https://cs.android.com/android/_/android/platform/frameworks/av/+/fb8ede2a020e741cb892ee024fcfba7e689183f2
            // https://cs.android.com/android/_/android/platform/frameworks/av/+/973db02ac18fa1de9ce6221f47b01af1bdc4bec2
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                return null
            var dt = dump.trim().split('\n').map { it.trim() }
            if (dt.size < 2) {
                Log.e(
                    TAG,
                    "getIdFromDump() failure: not enough lines, DUMP:\n$dump"
                )
                return null
            }
            if (dt[0] != "AudioTrack::dump") {
                Log.e(
                    TAG,
                    "getIdFromDump() failure: L0 isn't AudioTrack::dump, DUMP:\n$dump"
                )
                return null
            }
            if (!dt[1].contains("id(")) {
                Log.e(
                    TAG,
                    "getIdFromDump() failure: L1 didn't contain id(, DUMP:\n$dump"
                )
                return null
            }
            val idText = idRegex.matchEntire(dt[1])?.groupValues[1]
            if (idText == null) {
                Log.e(
                    TAG,
                    "getIdFromDump() failure: L1 didn't match regex, DUMP:\n$dump"
                )
                return null
            }
            idText.toIntOrNull()?.let { return it }
            Log.e(
                TAG,
                "getIdFromDump() failure: $idText didn't convert to int from base 10, DUMP:\n$dump"
            )
            return null
        }

        private val flagRegex = Regex(".*, flags\\(0x(.*)\\).*")
        private val flagRegexOld = Regex(".*, flags\\((.*)\\).*")
        fun getFlagsFromDump(dump: String?): Int? {
            if (dump == null)
                return null
            // Flags are in dump output since below commit which first appeared in Pie.
            // https://cs.android.com/android/_/android/platform/frameworks/av/+/d114b624ea2ec5c51779b74132a60b4a46f6cdba
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                return null
            var dt = dump.trim().split('\n').map { it.trim() }
            if (dt.size < 2) {
                Log.e(
                    TAG,
                    "getFlagFromDump() failure: not enough lines, DUMP:\n$dump"
                )
                return null
            }
            if (dt[0] != "AudioTrack::dump") {
                Log.e(
                    TAG,
                    "getFlagFromDump() failure: L0 isn't AudioTrack::dump, DUMP:\n$dump"
                )
                return null
            }
            if (!dt[1].contains(" flags(")) {
                Log.e(
                    TAG,
                    "getFlagFromDump() failure: L1 didn't contain flags(, DUMP:\n$dump"
                )
                return null
            }
            var flagText = flagRegex.matchEntire(dt[1])?.groupValues[1]
            if (flagText == null) {
                flagText = flagRegexOld.matchEntire(dt[1])?.groupValues[1]
                if (flagText == null) {
                    Log.e(
                        TAG,
                        "getFlagFromDump() failure: L1 didn't match regex, DUMP:\n$dump"
                    )
                    return null
                }
            }
            flagText.toIntOrNull(radix = 16)?.let { return it }
            Log.e(
                TAG,
                "getFlagFromDump() failure: $flagText didn't convert to int from base 16, DUMP:\n$dump"
            )
            return null
        }
    }

    // only access sink or track on PlaybackThread
    private var lastAudioTrack: AudioTrack? = null
    private var audioSink: DefaultAudioSink? = null
    var format: AfFormatInfo? = null
        private set
    var formatChangedCallback: ((AfFormatInfo?) -> Unit)? = null

    private val routingChangedListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        object : AudioRouting.OnRoutingChangedListener {
            override fun onRoutingChanged(router: AudioRouting) {
                this@AfFormatTracker.onRoutingChanged(router as AudioTrack)
            }
        } as Any
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        @Suppress("deprecation")
        object : AudioTrack.OnRoutingChangedListener {
            override fun onRoutingChanged(router: AudioTrack) {
                this@AfFormatTracker.onRoutingChanged(router)
            }
        } as Any
    } else null

    private fun onRoutingChanged(router: AudioTrack) {
        val audioTrack = (audioSink ?: throw NullPointerException(
            "audioSink is null in onAudioTrackInitialized"
        )).getAudioTrack()
        if (router !== audioTrack) return // stale callback
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
            if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED) return@let null
            val rd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                audioTrack.routedDevice else null
            val pn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                rd?.productName.toString() else null
            val t = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                rd?.type else null
            val id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                rd?.id else null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                handler.post {
                    val sd = MediaRoutes.getSelectedAudioDevice(context)
                    if (rd != sd)
                        Log.w(
                            TAG,
                            "routedDevice ${rd?.productName}(${rd?.id}) is not the same as MediaRoute " +
                                    "selected device ${sd?.productName}(${sd?.id})"
                        )
                }
            }
            val oid = getOutput(audioTrack)
            val sr = getHalSampleRate(audioTrack)
            val mp = if (oid != null && sr != null && sr > 8000 && sr < 1600000) {
                getMixPortForThread(oid, sr)
            } else null
            val latency = try {
                // this call writes to mAfLatency and mLatency fields, hence changes dump content
                AudioTrack::class.java.getMethod("getLatency").invoke(audioTrack) as Int
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
                null
            }
            val dump = dump(audioTrack)
            val grantedFlags = getFlagsFromDump(dump) // TODO implement extraction for L-O based on fixed offsets (or mayhaps something smarter?)
            AfFormatInfo(
                pn, id, t,
                mp?.id, mp?.name, mp?.flags,
                oid, sr,
                getHalFormat(audioTrack), getHalChannelCount(audioTrack), mp?.channelMask,
                grantedFlags, getPortIdFromDump(dump), findAfTrackFlags(dump, latency, audioTrack, grantedFlags)
            ) // TODO find policy port ID: https://cs.android.com/android/_/android/platform/frameworks/av/+/20b9ef0b55c9150ae11057ab997ae61be2d496ef
        }.let {
            if (LOG_EVENTS)
                Log.d(TAG, "audio hal format changed to: $it")
            format = it
            formatChangedCallback?.invoke(it)
        }
    }
}