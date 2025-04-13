package org.nift4.gramophone.hificore

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMetadataReadMap
import android.media.AudioPresentation
import android.media.AudioRouting
import android.media.AudioTrack
import android.media.VolumeShaper
import android.os.Build
import android.os.Parcel
import android.os.PersistableBundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import java.nio.ByteBuffer

/*
 * Exposes most of the API surface of AudioTrack.cpp, with some minor exceptions:
 * - setCallerName/getCallerName because I want to avoid offset hardcoding, and it's only used for metrics
 * - Extended timestamps, due to complexity
 * All native method calls are wrapped to avoid Throwables from being thrown - only Exceptions will be thrown by
 * this class or its methods. However, you should always be prepared to handle such an exception, as everything can
 * fail.
 */
class NativeTrack(context: Context, attributes: AudioAttributes, streamType: Int, sampleRate: Int,
                  format: UInt, channelMask: Int, frameCount: Int?, trackFlags: Int,
                  sessionId: Int, maxRequiredSpeed: Float, selectedDeviceId: Int?, bitRate: Int, durationUs: Long,
                  hasVideo: Boolean, smallBuf: Boolean, isStreaming: Boolean, offloadBufferSize: Int,
                  notificationFrames: Int, doNotReconnect: Boolean, transferMode: TransferMode, contentId: Int?,
                  syncId: Int?, encapsulationMode: Int, sharedMem: ByteBuffer?) {
    companion object {
        private const val TAG = "NativeTrack.kt"
        const val ENCAPSULATION_MODE_NONE = 0 // AudioTrack.ENCAPSULATION_MODE_NONE
        const val ENCAPSULATION_MODE_ELEMENTARY_STREAM = 1 // AudioTrack.ENCAPSULATION_MODE_ELEMENTARY_STREAM
        const val ENCAPSULATION_MODE_HANDLE = 2 // AudioTrack.ENCAPSULATION_MODE_HANDLE

        enum class TransferMode(val id: Int) {
            Callback(1), // onMoreData() called by track
            Obtain(2), // user calls obtainBuffer() and releaseBuffer()
            Sync(3), // user calls write()
            Shared(4), // shared memory ctor parameter
            @RequiresApi(Build.VERSION_CODES.Q)
            SyncWithCallback(5) // user calls write(), track calls onCanWriteMoreData()
        }

        data class DirectPlaybackSupport(val normalOffload: Boolean, val gaplessOffload: Boolean,
                                         val directBitstream: Boolean) {
            companion object {
                val NONE = DirectPlaybackSupport(false, false, false)
                val OFFLOAD = DirectPlaybackSupport(true, false, false)
                val GAPLESS_OFFLOAD = DirectPlaybackSupport(false, true, false)
                val DIRECT = DirectPlaybackSupport(false, false, true)
            }
            val offload
                get() = normalOffload || gaplessOffload
            val directOrOffload
                get() = directBitstream || offload
        }

        fun getDirectPlaybackSupport(context: Context, sampleRate: Int, encoding: UInt, platformEncoding: Int?,
                                     channelMask: Int, platformChannelMask: Int?): DirectPlaybackSupport {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            var hasDirect: Boolean? = null
            val format = platformEncoding?.let { platformChannelMask?.let {
                buildAudioFormat(sampleRate, platformEncoding, channelMask) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && format != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val d = AudioManager.getDirectPlaybackSupport(format, attributes)
                    return if (d == AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED) DirectPlaybackSupport.NONE
                    else DirectPlaybackSupport(
                        (d and AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED) != 0,
                        (d and AudioManager.DIRECT_PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED) != 0,
                        (d and AudioManager.DIRECT_PLAYBACK_BITSTREAM_SUPPORTED) != 0
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return when ((@Suppress("deprecation") AudioManager.getPlaybackOffloadSupport(
                        format, attributes))) {
                        AudioManager.PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED -> DirectPlaybackSupport.GAPLESS_OFFLOAD
                        AudioManager.PLAYBACK_OFFLOAD_SUPPORTED -> DirectPlaybackSupport.OFFLOAD
                        else -> {
                            if (@Suppress("deprecation") AudioTrack.isDirectPlaybackSupported(
                                    format, attributes))
                                DirectPlaybackSupport.DIRECT else DirectPlaybackSupport.NONE
                        }
                    }
                } else {
                    hasDirect = @Suppress("deprecation") AudioTrack.isDirectPlaybackSupported(
                            format, attributes)
                }
            }
            if (!initDlsym()) {
                Log.e(TAG, "initDlsym() failed")
                return DirectPlaybackSupport.NONE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return DirectPlaybackSupport.NONE // TODO implement native getDirectPlaybackSupport
            }
            // TODO before T, inactive routes were considered in isDirectPlaybackSupported according to AOSP doc.
            //  does that apply to getPlaybackOffloadSupport and isOffloadSupported too?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return DirectPlaybackSupport.NONE // TODO implement native getPlaybackOffloadSupport
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasDirect == null) {
                hasDirect = false // TODO implement native isDirectPlaybackSupported
            }
            val bitWidth = bitsPerSampleForFormat(encoding)
            var hasOffload: Boolean? = null
            if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                        && Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1)
                || !formatIsRawPcm(encoding) || bitWidth < 24
            ) {
                // this cannot be trusted on N/O with 24+ bit PCM formats due to format confusion bug
                hasOffload = try {
                    isOffloadSupported(sampleRate, encoding.toInt(), channelMask, 0, bitWidth, 0)
                } catch (t: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(t))
                    false
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                return DirectPlaybackSupport(hasOffload!!, false, hasDirect!!)
            // only L-P will enter below code path
            val bitrate = if (bitWidth != 0) {
                bitWidth * Integer.bitCount(channelMask) * sampleRate
            } else 128 // arbitrary guess for compressed formats
            val durationUs = 2100L /* 3.5min * 60 */ * 1000 * 1000 // must be >60s
            if (hasOffload == null) {
                // safeguard against bad direct track recycling on O by opening new session every time
                val sessionId = context.getSystemService<AudioManager>()!!.generateAudioSessionId()
                try {
                    val track = NativeTrack(
                        context, attributes, AudioManager.STREAM_MUSIC, sampleRate, encoding,
                        channelMask, null, 0x11, sessionId, 1.0f, null, bitrate, durationUs, false, false,
                        false, 0, 0, true, TransferMode.Sync, null, null, ENCAPSULATION_MODE_NONE, null
                    )
                    val port = AudioTrackHiddenApi.getMixPortForThread(track.getOutput(), track.getHalSampleRate())
                    if (port == null) {
                        Log.w(TAG, "port is null")
                        hasOffload = false
                    } else if (port.format != encoding.toInt()) {
                        Log.e(
                            TAG,
                            "port ${port.name} was found, but is format ${port.format} instead of $encoding"
                        )
                        hasOffload = false
                    } else {
                        hasOffload = (track.flags() and 0x11) == 0x11
                        if ((track.flags() and 0x11) == 0x1) {
                            hasDirect = true
                        }
                    }
                    track.release()
                } catch (t: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(t)) // TODO don't stacktrace when set fails due to unsupported format
                }
            }
            if (hasDirect == null) {
                // safeguard against bad direct track recycling on O by opening new session every time
                val sessionId = context.getSystemService<AudioManager>()!!.generateAudioSessionId()
                try {
                    val track = NativeTrack(
                        context, attributes, AudioManager.STREAM_MUSIC, sampleRate, encoding,
                        channelMask, null, 0x1, sessionId, 1.0f, null, bitrate, durationUs, false, false,
                        false, 0, 0, true, TransferMode.Sync, null, null, ENCAPSULATION_MODE_NONE, null
                    )
                    val port = AudioTrackHiddenApi.getMixPortForThread(track.getOutput(), track.getHalSampleRate())
                    Log.i(TAG, "got port $port")
                    if (port == null) {
                        Log.w(TAG, "port is null")
                        hasDirect = false
                    } else if (port.format != encoding.toInt()) {
                        Log.e(
                            TAG,
                            "port ${port.name} was found, but is format ${port.format} instead of $encoding"
                        )
                        hasDirect = false
                    } else {
                        hasDirect = (track.flags() and 0x11) == 0x1
                    }
                    track.release()
                } catch (t: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(t)) // TODO don't stacktrace when set fails due to unsupported format
                }
            }
            return DirectPlaybackSupport(hasOffload == true, false, hasDirect == true)
        }
        fun bitsPerSampleForFormat(format: UInt): Int {
            val cafOffloadMain = when {
                Build.VERSION.SDK_INT >= 25 -> null
                Build.VERSION.SDK_INT >= 23 -> 0x1A000000U
                else -> 0x1C000000U
            }
            val normalized = if (cafOffloadMain != null && (format and 0xff000000U) == cafOffloadMain) {
                format and (0xff000000U.inv())
            } else format
            return when (normalized) {
                0x1U, 0x0D000000U -> 16
                0x2U -> 8
                0x3U, 0x4U, 0x5U -> 32
                0x6U -> 24
                else -> 0
            }
        }
        fun formatIsRawPcm(format: UInt) = (format and 0xff000000U /* AUDIO_FORMAT_MAIN_MASK */) == 0U
        private fun buildAudioFormat(sampleRate: Int, encoding: Int, channelMask: Int): AudioFormat? {
            val formatBuilder = AudioFormat.Builder()
            try {
                formatBuilder.setSampleRate(sampleRate)
            } catch (_: IllegalArgumentException) {
                formatBuilder.setSampleRate(48000)
                try {
                    @SuppressLint("PrivateApi")
                    val field = formatBuilder.javaClass.getDeclaredField("mSampleRate")
                    field.isAccessible = true
                    field.set(formatBuilder, sampleRate)
                } catch (t: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(t))
                    return null
                }
            }
            try {
                formatBuilder.setEncoding(encoding)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, Log.getStackTraceString(e))
                return null
            }
            return formatBuilder.setChannelMask(channelMask).build()
        }
        private external fun isOffloadSupported(sampleRate: Int, format: Int, channelMask: Int, bitRate: Int,
                                                bitWidth: Int, offloadBufferSize: Int): Boolean
        private external fun initDlsym(): Boolean
        fun forTest(context: Context): NativeTrack {
            return NativeTrack(
                context,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                0,
                sampleRate = 13370,
                0x1U, // pcm 16bit
                channelMask = 3,
                frameCount = null,
                trackFlags = 1,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
                maxRequiredSpeed = 1.0f,
                selectedDeviceId = null,
                bitRate = 0,
                durationUs = 0,
                hasVideo = false,
                smallBuf = false,
                isStreaming = false,
                offloadBufferSize = 0,
                notificationFrames = 0,
                doNotReconnect = false,
                transferMode = TransferMode.Sync,
                contentId = null,
                syncId = null,
                encapsulationMode = ENCAPSULATION_MODE_NONE,
                sharedMem = null
            )
        }
    }
    private var sessionId: Int
    val ptr: Long
    var myState: State
    // proxy limitations: a lot of fields not initialized (mSampleRate, mAudioFormat, mOffloaded, ...) which can
    // cause some internal checks in various methods to fail; stream event and playback position callbacks both
    // are no-op; we MUST call play(), pause(), stop() and don't use the native methods ourselves for this to work;
    // we must also not cache playing/paused/stopped/volume ourselves because it may change under our feet.
    // however, it does:
    // - register (and overwrite) any codec format listeners on native side ; good for us because we can't register
    //   one in a normal way due to dependence on volatile offsets. i.e. with proxy we get codec format listeners!
    // - register player base (which we really should have on N+ to be a nice citizen and have stuff like ducking)
    // - register (and overwrite) routing callback, which is meh but we can just use the java one, it don't hurt
    // - allow for volume shapers! these would be near-impossible using the native API because it's all inline.
    // it's a bit fiddly, but we get all possibilities of a native AudioTrack and a Java one - combined.
    // reminder: do not call write() or any other standard APIs as we break a lot of assumptions. + proxy is not
    //  always available (i.e. L/M), hence native methods are preferable where we can.
    private val proxy: AudioTrack?
    private val codecListener: AudioTrack.OnCodecFormatChangedListener?
    private val routingListener: AudioRouting.OnRoutingChangedListener?
    init {
        if (sharedMem?.isDirect == false)
            throw IllegalArgumentException("shared memory specified but isn't direct")
        if (sharedMem == null && transferMode == TransferMode.Shared)
            throw IllegalArgumentException("transfer mode is Shared but sharedMem is null")
        if (sharedMem != null && transferMode != TransferMode.Shared)
            throw IllegalArgumentException("transfer mode is not Shared but sharedMem is specified")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            transferMode == @Suppress("NewApi") TransferMode.SyncWithCallback)
            throw IllegalArgumentException("SyncWithCallback not supported on this android version")
        if (frameCount != null && frameCount == 0)
            throw IllegalArgumentException("frameCount cannot be zero (did you mean to use null?)")
        if (selectedDeviceId != null && selectedDeviceId == 0)
            throw IllegalArgumentException("selectedDeviceId cannot be zero (did you mean to use null?)")
        if (syncId != null && syncId < 1)
            throw IllegalArgumentException("syncId must be positive (did you mean to use null?)")
        if (contentId != null && contentId < 0)
            throw IllegalArgumentException("contentId cannot be negative (did you mean to use null?)")
        if (contentId == 0 && syncId == null)
            throw IllegalArgumentException("CONTENT_ID_NONE with no syncId (did you mean to use null?)")
        try {
            System.loadLibrary("gramophone")
        } catch (t: Throwable) {
            throw NativeTrackException("failed to load libgramophone.so", t)
        }
        if (!try {
            initDlsym()
        } catch (t: Throwable) {
            throw NativeTrackException("initDlsym() failed", t)
        })
            throw NativeTrackException("initDlsym() returned false")
        ptr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ats = context.attributionSource
            val parcel = Parcel.obtain()
            try {
                ats.writeToParcel(parcel, 0)
                try {
                    create(parcel)
                } catch (t: Throwable) {
                    throw NativeTrackException("create() threw exception", t)
                }
            } finally {
                parcel.recycle()
            }
        } else try {
            create(null)
        } catch (t: Throwable) {
            throw NativeTrackException("create() threw exception", t)
        }
        if (ptr == 0L) {
            throw NativeTrackException("create() returned NULL")
        }
        this.sessionId = if (sessionId == AudioManager.AUDIO_SESSION_ID_GENERATE)
            context.getSystemService<AudioManager>()!!.generateAudioSessionId()
        else sessionId
        val usage = attributes.usage
        val contentType = attributes.contentType
        val hasOutputFlagDeepBufferSet = false // TODO
        val attrFlags = attributes.flags or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2
                    && attributes.isContentSpatialized) 0x4000 else 0) or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 && attributes.spatializationBehavior
                    == AudioAttributes.SPATIALIZATION_BEHAVIOR_NEVER) 0x8000 else 0) or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && attributes.areHapticChannelsMuted()) 0x800 else 0) or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    when (attributes.allowedCapturePolicy) {
                        AudioAttributes.ALLOW_CAPTURE_BY_NONE -> 0x1400
                        AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM -> 0x400
                        else -> 0x0
                    } else 0) or (if (hasOutputFlagDeepBufferSet) 0x200 else 0x0)
        val bitWidth = bitsPerSampleForFormat(format)
        // java streamType is compatible with native streamType
        val ret = try {
            set(ptr = ptr, streamType = streamType, sampleRate = sampleRate, format = format.toInt(),
                channelMask = channelMask, frameCount = frameCount ?: 0, trackFlags = trackFlags,
                sessionId = this.sessionId, maxRequiredSpeed = maxRequiredSpeed,
                selectedDeviceId = selectedDeviceId ?: 0, bitRate = bitRate, durationUs = durationUs,
                hasVideo = hasVideo, smallBuf = smallBuf, isStreaming = isStreaming, bitWidth = bitWidth,
                offloadBufferSize = offloadBufferSize, usage = usage, contentType = contentType,
                attrFlags = attrFlags, notificationFrames = notificationFrames, doNotReconnect = doNotReconnect,
                transferMode = transferMode.id, contentId = contentId ?: 0, syncId = syncId ?: 0,
                encapsulationMode = encapsulationMode, sharedMem = sharedMem)
        } catch (t: Throwable) {
            try {
                dtor(ptr)
            } catch (t2: Throwable) {
                throw NativeTrackException("dtor() threw exception after set() threw exception: " +
                        Log.getStackTraceString(t2), t)
            }
            throw NativeTrackException("set() threw exception", t)
        }
        if (ret != 0) {
            try {
                dtor(ptr)
            } catch (t: Throwable) {
                throw NativeTrackException("dtor() threw exception after set() failed with code $ret", t)
            }
            throw NativeTrackException("set() failed with code $ret")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            proxy = try {
                getProxy(ptr, this.sessionId)
            } catch (t: Throwable) {
                try {
                    dtor(ptr)
                } catch (t2: Throwable) {
                    throw NativeTrackException("dtor() threw exception after getProxy() threw exception: " +
                            Log.getStackTraceString(t2), t)
                }
                throw NativeTrackException("getProxy() threw exception", t)
            }
            if (proxy == null) {
                try {
                    dtor(ptr)
                } catch (t: Throwable) {
                    throw NativeTrackException("dtor() threw exception after getProxy() returned null, " +
                            "check prior logs", t)
                }
                throw NativeTrackException("getProxy() returned null, check prior logs")
            }
            routingListener = object : AudioRouting.OnRoutingChangedListener {
                override fun onRoutingChanged(router: AudioRouting?) {
                    this@NativeTrack.onRoutingChanged()
                }
            }
            proxy.addOnRoutingChangedListener(routingListener, null)
        } else {
            proxy = null
            routingListener = null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            codecListener = object : AudioTrack.OnCodecFormatChangedListener {
                override fun onCodecFormatChanged(audioTrack: AudioTrack, info: AudioMetadataReadMap?) {
                    this@NativeTrack.onCodecFormatChanged(info)
                }
            }
            proxy!!.addOnCodecFormatChangedListener({ r -> r.run() }, codecListener)
        } else codecListener = null
        myState = State.ALIVE
        Log.e("hi", "dump:${AudioTrackHiddenApi.dumpInternal(getRealPtr(ptr))}")
        Log.e("hi", "my flags:${flags()} nfa:${notificationPeriodInFrames()}")
    }
    private external fun create(@Suppress("unused") parcel: Parcel?): Long
    /*
     * CAUTION: Until including Android 7.1, direct outputs could be reused even with different session IDs.
     *          If another app is using a direct (or offload) stream, we might end up with no audio (there can
     *          only ever be one client). However, this problem is isolated to MediaPlayer using compressed offload
     *          (or another app like us doing that), and us using hidden API to offload in the same format, sample
     *          rate and channel mask. Audio focus sadly isn't enough as the track needs to be released to avoid
     *          this bug, so either avoid other media player apps or using compressed offload on these versions.
     *
     * CAUTION: From Android 7.0 until Android 8.1, direct outputs with PCM modes int24, int32 or float32 were all
     *          treated as compatible. To avoid track creation failures caused by ourselves, we should always
     *          release active tracks on any direct output before attempting to switch formats - otherwise it may
     *          try to reuse the track despite the different format. But if we're unlucky, on Android 7.x only, we
     *          may get a busy output with a different format anyway because another app has an active direct PCM
     *          track - which will result in set() failing. There's another case where set() could fail: on N/O,
     *          when we request float32, APM may select int32 instead (because both have the same bit width) - if
     *          we request int32, we may get float32; and with some bad luck, if we request a 32-bit format, we may
     *          even get int24 (if no 32 bit format is supported) - or when requesting int24, we may get a 32-bit
     *          format (if int24 is not supported). After APM gives us that output, AF will fail creating the track
     *          causing set() to fail. In that case, we have to try again with another format.
     */
    @Suppress("unused") // for parameters, this method has a few of them
    private external fun set(ptr: Long, streamType: Int, sampleRate: Int, format: Int, channelMask: Int,
                             frameCount: Int, trackFlags: Int, sessionId: Int, maxRequiredSpeed: Float,
                             selectedDeviceId: Int, bitRate: Int, durationUs: Long, hasVideo: Boolean,
                             smallBuf: Boolean, isStreaming: Boolean, bitWidth: Int, offloadBufferSize: Int,
                             usage: Int, contentType: Int, attrFlags: Int, notificationFrames: Int,
                             doNotReconnect: Boolean, transferMode: Int, contentId: Int, syncId: Int,
                             encapsulationMode: Int, sharedMem: ByteBuffer? /* direct */): Int
    private external fun getRealPtr(@Suppress("unused") ptr: Long): Long
    private external fun notificationFramesActFromOffset(@Suppress("unused") ptr: Long): Int
    private external fun dtor(@Suppress("unused") ptr: Long)
    @RequiresApi(Build.VERSION_CODES.N)
    private external fun getProxy(@Suppress("unused") ptr: Long, @Suppress("unused") sessionId: Int): AudioTrack?

    fun release() {
        myState = State.RELEASED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && codecListener != null) {
            proxy!!.removeOnCodecFormatChangedListener(codecListener)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            proxy!!.removeOnRoutingChangedListener(routingListener)
            proxy.release() // this doesn't free native obj because we hold extra strong ref, cleared in dtor()
        }
        dtor(ptr)
    }

    fun dump(): String {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return try {
            AudioTrackHiddenApi.dumpInternal(getRealPtr(ptr))
        } catch (t: Throwable) {
            throw NativeTrackException("failed to dump", t)
        }
    }

    fun state(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return 0 // TODO get from dump()
    }

    fun isPlaying(): Boolean {
        val state = state()
        return state == 0 || state == 5
    }

    fun frameCount(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return 0 // TODO get from dump()
    }

    fun format(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return 0U // TODO get from saved set state
    }

    fun sessionId() = sessionId

    /**
     * The accuracy of this method depends on the Android version:
     * Android 8.0 or later / Non-CAF Android 7.1: all flags are adjusted to match output capabilities
     * CAF Android 7.0 / 7.1: system only adjusts fast flag, we adjust direct flag through a trick
     * Non-CAF Android 7.0: system only adjusts fast flag
     * (because Audio HALs from Android 7.x time don't support using compressed formats in for anything except
     * passthrough or offload, we can assume that if we request offload, we get offload, passthrough, or a creation
     * failure. this last disambiguation must be done by end user based on mix port name.)
     * Android 5.x / 6.x: system only adjusts fast, direct and offload flag
     */
    fun flags(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return try {
            AudioTrackHiddenApi.getFlagsInternal(proxy, getRealPtr(ptr)).let {
                if (it == Int.MAX_VALUE || it == Int.MIN_VALUE)
                    throw NativeTrackException("something went wrong while getting flags, check prior logs")
                else it
            }
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get flags", t)
        }
    }

    fun notificationPeriodInFrames(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            AudioTrackHiddenApi.getNotificationFramesActFromDump(dump())
                ?: throw IllegalStateException("notificationFramesAct failed, check prior logs")
        else notificationFramesActFromOffset(ptr)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun policyPortId(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return AudioTrackHiddenApi.getPortIdFromDump(dump())
            ?: throw IllegalStateException("getPortId failed, check prior logs")
    }

    fun channelMask(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return 0 // TODO get from saved set state
    }

    fun latency(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return 0U // TODO
    }

    fun getUnderrunCount(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return 0U // TODO
    }

    fun getBufferSizeInFrames(): ULong {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return 0UL // TODO
    }

    fun getBufferDurationInUs(): ULong {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return 0UL // TODO
    }

    fun setBufferSizeInFrames(size: ULong) {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        // TODO
    }

    fun getStartThresholdInFrames(): ULong {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return 0UL // TODO
    }

    fun setStartThresholdInFrames(size: ULong) {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        // TODO
    }

    fun sharedBuffer(): ByteBuffer {
        TODO()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun getMetrics(): PersistableBundle {
        return proxy!!.metrics
    }

    fun start() {
        // TODO call into proxy
    }

    fun stop() {
        // TODO call into proxy
    }

    fun stopped(): Boolean {
        TODO()
    }

    fun flush() {
        // TODO
    }

    fun pause() {
        // TODO call into proxy
    }

    fun pauseAndWait(timeoutMs: ULong): Boolean {
        // TODO call into proxy
        TODO()
    }

    fun setVolume(volume: Float) {
        // TODO call into proxy(?)
    }

    fun setAuxEffectSendLevel(level: Float) {
        // TODO
    }

    fun getAuxEffectSendLevel(): Float {
        TODO()
    }

    fun setSampleRate(rate: UInt) {
        // TODO
    }

    fun getSampleRate(): UInt {
        TODO()
    }

    fun getOriginalSampleRate(): UInt {
        TODO()
    }

    fun getHalSampleRate(): UInt {
        TODO()
    }

    fun getHalChannelCount(): UInt {
        TODO()
    }

    fun getHalFormat(): UInt {
        TODO()
    }

    fun setPlaybackRate(playbackRate: Nothing) {
        TODO()
    }

    fun getPlaybackRate(): Nothing {
        TODO()
    }

    fun setDualMonoMode(dualMonoMode: Nothing) {
        TODO()
    }

    fun getDualMonoMode(): Nothing {
        TODO()
    }

    fun setAudioDescriptionMixLevel(level: Float) {
        // TODO
    }

    fun getAudioDescriptionMixLevel(): Float {
        TODO()
    }

    fun setLoop(loopStart: UInt, loopEnd: UInt, loopCount: Int) {
        TODO()
    }

    fun setMarkerPosition(markerPosition: UInt) {
        // TODO
    }

    fun getMarkerPosition(): UInt {
        TODO()
    }

    fun setPositionUpdatePeriod(positionUpdatePeriod: UInt) {
        // TODO
    }

    fun setPositionUpdatePeriod(): UInt {
        TODO()
    }

    fun setPosition(position: UInt) {
        // TODO
    }

    fun getPosition(): UInt {
        TODO()
    }

    fun getBufferPosition(): UInt {
        TODO()
    }

    fun reload() {
        TODO()
    }

    fun getOutput(): Int {
        TODO()
    }

    fun setSelectedDevice(audioDeviceInfo: AudioDeviceInfo) {
        TODO()
    }

    fun getSelectedDevice(): AudioDeviceInfo {
        TODO()
    }

    fun getRoutedDevices(): List<AudioDeviceInfo> {
        TODO()
    }

    fun attachAuxEffect(effectId: Int) {
        TODO()
    }

    // TODO status_t    obtainBuffer(Buffer* audioBuffer, int32_t waitCount,
    //                               size_t *nonContig = NULL);

    // TODO void        releaseBuffer(const Buffer* audioBuffer);

    // TODO ssize_t     write(const void* buffer, size_t size, bool blocking = true);

    fun channelCount(): Int {
        return Integer.bitCount(channelMask()) // TODO is this valid?
    }

    fun frameSize(): Int {
        val bps = bitsPerSampleForFormat(format())
        if (bps == 0) // compressed
            return 1
        return channelCount() * (bps / 8)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createVolumeShaper(config: VolumeShaper.Configuration): VolumeShaper {
        return proxy!!.createVolumeShaper(config)
    }

    fun getUnderrunFrames(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return 0U // TODO
    }

    fun setParameters(params: String) {
        TODO()
    }

    fun getParameters(params: String): String {
        TODO()
    }

    fun selectPresentation(presentation: AudioPresentation) {
        TODO()
    }

    // TODO status_t    getTimestamp(AudioTimestamp& timestamp);

    // TODO status_t pendingDuration(int32_t *msec,
    //      ExtendedTimestamp::Location location = ExtendedTimestamp::LOCATION_SERVER);

    fun hasStarted(): Boolean {
        TODO()
    }

    fun setLogSessionId(params: String) {
        TODO()
    }

    class NativeTrackException : Exception {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }
    enum class State {
        DEAD_OBJECT, // we got killed by lower layer
        RELEASED, // release() called
        ALIVE, // ready to use
    }

    @Suppress("unused") // called from native, on callback thread (not main thread!)
    private fun onUnderrun() {
        Log.i(TAG, "onUnderrun called")
    }
    @Suppress("unused") // called from native, on callback thread (not main thread!)
    private fun onMarker(markerPosition: Int) {
        Log.i(TAG, "onMarker called: $markerPosition")
    }
    @Suppress("unused") // called from native, on callback thread (not main thread!)
    private fun onNewPos(newPos: Int) {
        Log.i(TAG, "onNewPos called: $newPos")
    }
    @Suppress("unused") // called from native, on callback thread (not main thread!)
    private fun onStreamEnd() {
        Log.i(TAG, "onStreamEnd called")
    }
    @Suppress("unused") // called from native, on callback thread (not main thread!)
    private fun onNewIAudioTrack() {
        Log.i(TAG, "onNewIAudioTrack called")
    }
    @Suppress("unused") // called from native, on callback thread (not main thread!)
    private fun onNewTimestamp(timestamp: Int, timeSec: Long, timeNanoSec: Long) {
        Log.i(TAG, "onNewTimestamp called: timestamp=$timestamp timeSec=$timeSec timeNanoSec=$timeNanoSec")
    }
    @Suppress("unused") // called from native, on callback thread (not main thread!)
    private fun onLoopEnd(loopsRemaining: Int) {
        Log.i(TAG, "onLoopEnd called")
    }
    @Suppress("unused") // called from native, on callback thread (not main thread!)
    private fun onBufferEnd() {
        Log.i(TAG, "onBufferEnd called")
    }
    @Suppress("unused") // called from native, on callback thread (not main thread!)
    // Be careful to not hold a reference to the buffer after returning. It will immediately be invalid!
    private fun onMoreData(frameCount: Long, buffer: ByteBuffer): Long {
        Log.i(TAG, "onMoreData called: frameCount=$frameCount sizeBytes=${buffer.capacity()}")
        return 0 // amount of bytes written
    }
    @Suppress("unused") // called from native, on callback thread (not main thread!)
    private fun onCanWriteMoreData(frameCount: Long, sizeBytes: Long) {
        Log.i(TAG, "onCanWriteMoreData called: frameCount=$frameCount sizeBytes=$sizeBytes")
    }
    @Suppress("unused") // called from native, on random thread (not main thread!) - only M for now, N+ uses proxy
    private fun onAudioDeviceUpdate(ioHandle: Int, routedDevices: IntArray) {
        Log.i(TAG, "onAudioDeviceUpdate called: ioHandle=$ioHandle routedDevices=${routedDevices.contentToString()}")
    }
    // called on audio track initialization thread, most often main thread but not always
    private fun onRoutingChanged() {
        Log.i(TAG, "onRoutingChanged called")
    }
    // called on random thread
    private fun onCodecFormatChanged(metadata: AudioMetadataReadMap?) {
        Log.i(TAG, "onCodecFormatChanged called: metadata=$metadata")
    }
}