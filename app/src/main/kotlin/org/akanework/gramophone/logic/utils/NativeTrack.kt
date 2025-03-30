package org.akanework.gramophone.logic.utils

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Parcel
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import java.nio.ByteBuffer

/*
 * Exposes entire API surface of AudioTrack.cpp, with some notable exceptions:
 * - Tuner API related features (including setAudioTrackCallback)
 * - setCallerName/getCallerName/getMetrics because only used for metrics
 * - TRANSFER_SHARED because short AudioTracks can be served by java API just fine
 * None of those will impose any limitations for music playback.
 */
class NativeTrack(context: Context, attributes: AudioAttributes, streamType: Int, sampleRate: Int,
                  format: AudioFormatDetector.Encoding,
                  channelMask: Int,
                  frameCount: Int?, trackFlags: Int, sessionId: Int, maxRequiredSpeed: Float,
                  selectedDeviceId: Int?, bitRate: Int, durationUs: Long, hasVideo: Boolean,
                  isStreaming: Boolean, offloadBufferSize: Int,
                  notificationFrames: Int, doNotReconnect: Boolean, transferMode: Int) {
    companion object {
        private const val TAG = "NativeTrack.kt"

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

        fun getDirectPlaybackSupport(context: Context, sampleRate: Int, encoding: AudioFormatDetector.Encoding, channelMask: Int): DirectPlaybackSupport {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            var hasDirect: Boolean? = null
            val format = encoding.enc?.let { buildAudioFormat(sampleRate, it, channelMask) } // TODO media3 does not have to equal platform format does it?
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
            val channelMask = channelMaskToNative(channelMask)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return DirectPlaybackSupport.NONE // TODO implement native getDirectPlaybackSupport
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return DirectPlaybackSupport.NONE // TODO implement native getPlaybackOffloadSupport
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasDirect == null) {
                hasDirect = false // TODO implement native isDirectPlaybackSupported
            }
            val bitWidth = bitsPerSampleForFormat(encoding)
            var hasOffload: Boolean? = null
            if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                        && Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) || bitWidth < 24
            ) {
                // this cannot be trusted on N/O with hi-res formats due to format confusion bug
                hasOffload = try {
                    isOffloadSupported(sampleRate, encoding.native!!.toInt(), channelMask)
                } catch (t: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(t))
                    false
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                return DirectPlaybackSupport(hasOffload!!, false, hasDirect!!)
            val tryOffload = hasDirect != null // if we already know if direct works, we want to test for offload.
            // only L-P will enter below code path
            val bitrate = if (bitWidth != 0) {
                bitWidth * Integer.bitCount(channelMask) * sampleRate
            } else 128 // arbitrary guess for compressed formats
            // safeguard against bad direct track recycling on O by opening new session every time
            val sessionId = context.getSystemService<AudioManager>()!!.generateAudioSessionId()
            try {
                /*val track = NativeTrack(context)
                track.set()
                track.release() TODO implement this*/
                val port: MyMixPort? = null
                Log.i(TAG, "got port $port")
                if (port == null) {
                    Log.w(TAG, "port is null")
                    return DirectPlaybackSupport.NONE
                }
                if (port.format != encoding.native!!.toInt()) {
                    Log.w(TAG, "port ${port.name} was found, but is format ${port.format} instead of $encoding")
                    return DirectPlaybackSupport.NONE
                }
                hasDirect = false
                // TODO look at granted flags to determine hasDirect
                return DirectPlaybackSupport(hasOffload == true, false, hasDirect)
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            return DirectPlaybackSupport.NONE
        }
        private fun channelMaskToNative(channelMask: Int): Int {
            return when (channelMask) {
                AudioFormat.CHANNEL_OUT_MONO -> 1
                AudioFormat.CHANNEL_OUT_STEREO -> 3
                else -> TODO()
            }
        }
        fun bitsPerSampleForFormat(format: AudioFormatDetector.Encoding): Int = when (format) {
            AudioFormatDetector.Encoding.ENCODING_PCM_16BIT, AudioFormatDetector.Encoding.ENCODING_IEC61937,
            AudioFormatDetector.Encoding.ENCODING_PCM_16_BIT_OFFLOAD -> 16
            AudioFormatDetector.Encoding.ENCODING_PCM_8BIT -> 8
            AudioFormatDetector.Encoding.ENCODING_PCM_32BIT, AudioFormatDetector.Encoding.ENCODING_PCM_FLOAT,
            AudioFormatDetector.Encoding.ENCODING_PCM_8_24BIT,
            AudioFormatDetector.Encoding.ENCODING_PCM_8_24_BIT_OFFLOAD -> 32
            AudioFormatDetector.Encoding.ENCODING_PCM_24BIT -> 24
            else -> 0
        }
        private fun buildAudioFormat(sampleRate: Int, encoding: Int, channelMask: Int): AudioFormat? {
            val formatBuilder = AudioFormat.Builder()
            try {
                formatBuilder.setSampleRate(sampleRate)
            } catch (_: IllegalArgumentException) {
                formatBuilder.setSampleRate(48000)
                try {
                    @SuppressLint("SoonBlockedPrivateApi")
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
        private external fun isOffloadSupported(sampleRate: Int, format: Int, channelMask: Int): Boolean
        private external fun initDlsym(): Boolean
        fun forTest(context: Context): NativeTrack {
            return NativeTrack(
                context,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioManager.STREAM_MUSIC,
                sampleRate = 13370,
                AudioFormatDetector.Encoding.ENCODING_PCM_16BIT,
                channelMask = 3,
                frameCount = null,
                trackFlags = 1,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
                maxRequiredSpeed = 1.0f,
                selectedDeviceId = null,
                bitRate = 0,
                durationUs = 0,
                hasVideo = false,
                isStreaming = false,
                offloadBufferSize = 0,
                notificationFrames = 0,
                doNotReconnect = false,
                transferMode = 3
            )
        }
    }
    private var sessionId: Int
    val ptr: Long
    var myState: State
    init {
        if (frameCount != null && frameCount == 0)
            throw IllegalArgumentException("frameCount cannot be zero (did you mean to use null?)")
        if (selectedDeviceId != null && selectedDeviceId == 0)
            throw IllegalArgumentException("selectedDeviceId cannot be zero (did you mean to use null?)")
        if (!format.isSupportedAsNative)
            throw IllegalArgumentException("encoding $format not supported on this SDK?")
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
            ContextCompat.getSystemService(context, AudioManager::class.java)!!
                .generateAudioSessionId()
        else sessionId
        val fmt = format.native!!.toInt()
        val usage = attributes.usage
        val contentType = attributes.contentType
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
                    } else 0x0)
        val bitWidth = bitsPerSampleForFormat(format)
        // TODO add attr flag AUDIO_FLAG_DEEP_BUFFER if output flag deep buffer is set
        // java streamType is compatible with native streamType
        val ret = try {
            set(ptr, streamType, sampleRate, fmt, channelMask, frameCount ?: 0, trackFlags,
                this.sessionId, maxRequiredSpeed, selectedDeviceId ?: 0, bitRate, durationUs, hasVideo,
                isStreaming, bitWidth, offloadBufferSize, usage, contentType, attrFlags, notificationFrames,
                        doNotReconnect, transferMode)
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
        myState = State.ALIVE
        Log.e("hi", "dump:${AfFormatTracker.dumpInternal(getRealPtr(ptr))}")
        Log.e("hi", "my flags:${flags()} nfa:${notificationFramesAct()}")
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
                             isStreaming: Boolean, bitWidth: Int, offloadBufferSize: Int, usage: Int,
                             contentType: Int, attrFlags: Int,
                             notificationFrames: Int, doNotReconnect: Boolean, transferMode: Int): Int
    private external fun getRealPtr(@Suppress("unused") ptr: Long): Long
    private external fun flagsFromOffset(@Suppress("unused") ptr: Long): Int
    private external fun notificationFramesActFromOffset(@Suppress("unused") ptr: Long): Int
    private external fun dtor(@Suppress("unused") ptr: Long)

    fun release() {
        myState = State.RELEASED
        dtor(ptr)
    }

    fun dump(): String {
        return ""
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

    fun format(): AudioFormatDetector.Encoding {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return AudioFormatDetector.Encoding.ENCODING_PCM_16BIT // TODO get from saved set state
    }

    fun sessionId() = sessionId

    fun flags(): Int {
        // TODO can we do anything about N MR0 (all) and N MR1 (caf) not adjusting at all (except fast) here?
        //  at least detect caf N MR1 programmatically to know if we can trust this or not
        //  actually yes we can: AudioTrackIsTrackOffloaded (caf only) will tell us if its direct PCM or not
        //  and offload uses different formats, if isOffloadSupported is true and our track works we have either
        //  offload or passthrough. compressed passthrough will be detected as offload and PCM passthrough won't be
        //  detected but it's better than nothing. if we aren't on caf and have N MR0, bad luck - we can only
        //  detect offload.
        // TODO document L/M not stripping all flags, only direct / offload / fast
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            AfFormatTracker.getFlagsFromDump(dump())
                ?: throw IllegalStateException("getFlags failed, check prior logs")
        else flagsFromOffset(ptr)
    }

    fun notificationFramesAct(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            AfFormatTracker.getFlagsFromDump(dump()) // TODO
                ?: throw IllegalStateException("notificationFramesAct failed, check prior logs")
        else notificationFramesActFromOffset(ptr)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun portId(): Int {
        return AfFormatTracker.getPortIdFromDump(dump())
            ?: throw IllegalStateException("getPortId failed, check prior logs")
    }

    fun channelMask(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return 0 // TODO get from saved set state
    }

    fun channelCount(): Int {
        return Integer.bitCount(channelMask()) // TODO is this valid?
    }

    fun frameSize(): Int {
        val bps = bitsPerSampleForFormat(format()) / 8
        if (bps == 0) // compressed
            return 1
        return channelCount() * bps
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
    @Suppress("unused") // called from native, on random thread (not main thread!)
    private fun onAudioDeviceUpdate(ioHandle: Int, routedDevices: IntArray) {
        Log.i(TAG, "onAudioDeviceUpdate called: ioHandle=$ioHandle routedDevices=$routedDevices")
    }
}