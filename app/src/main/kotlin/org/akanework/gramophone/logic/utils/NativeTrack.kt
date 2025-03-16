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
import androidx.core.content.getSystemService
import androidx.core.util.Consumer
import java.nio.ByteBuffer

class NativeTrack(context: Context) {
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

        fun getDirectPlaybackSupport(context: Context, sampleRate: Int, encoding: Int, channelMask: Int): DirectPlaybackSupport {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            var hasDirect: Boolean? = null
            val format = buildAudioFormat(sampleRate, encoding, channelMask)
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
            val encoding = encodingToNative(encoding)
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
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                hasDirect = false // TODO we have to create a track to find if direct is supported
            }
            val bitWidth = when (encoding) {
                1, 0x0D000000 -> 16
                2 -> 8
                3, 4, 5 -> 32
                6 -> 24
                else -> 0
            }
            var hasOffload: Boolean? = null
            if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                        && Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) || bitWidth < 24
            ) {
                // this cannot be trusted on N/O with hi-res formats due to format confusion bug
                hasOffload = try {
                    isOffloadSupported(sampleRate, encoding, channelMask)
                } catch (t: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(t))
                    false
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                return DirectPlaybackSupport(hasOffload!!, false, hasDirect!!)
            // only L-O will enter below code path TODO is this really better than track creation?
            val bitrate = if (bitWidth != 0) {
                bitWidth * Integer.bitCount(channelMask) * sampleRate
            } else 128 // arbitrary guess for compressed formats
            // safeguard against bad direct track recycling on O by opening new session every time
            val sessionId = context.getSystemService<AudioManager>()!!.generateAudioSessionId()
            try {
                val port = run {
                    var port: MyMixPort? = null
                    runWithOpenedOutput(
                        AudioManager.STREAM_MUSIC, sampleRate, encoding, channelMask, 0, 0, sessionId,
                        0, bitrate, 2100 * 1000 * 1000, false, false, bitWidth, 0,
                        AudioAttributes.USAGE_MEDIA,
                        AudioAttributes.CONTENT_TYPE_MUSIC, 0, 0, "", Consumer<Int> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                port = AfFormatTracker.getMixPortForThread(it, sampleRate)
                            } else { // TODO dedup with afformattracker?
                                val af = AfFormatTracker.getAfService()
                                if (af == null)
                                    return@Consumer
                                val inParcel = AfFormatTracker.obtainParcel(af)
                                val outParcel = AfFormatTracker.obtainParcel(af)
                                try {
                                    inParcel.writeInterfaceToken(af.interfaceDescriptor!!)
                                    inParcel.writeInt(it)
                                    // IAudioFlingerService.format(audio_io_handle_t)
                                    Log.d(TAG, "trying to call format() via binder")
                                    try {
                                        af.transact(5, inParcel, outParcel, 0)
                                    } catch (e: Throwable) {
                                        Log.e(TAG, Log.getStackTraceString(e))
                                        return@Consumer
                                    }
                                    Log.d(TAG, "done calling format() via binder")
                                    if (!AfFormatTracker.readStatus(outParcel))
                                        return@Consumer
                                    port = MyMixPort(null, null, null, null, outParcel.readInt())
                                } finally {
                                    inParcel.recycle()
                                    outParcel.recycle()
                                }
                            }
                        }
                    )
                    port
                }
                Log.i(TAG, "got port $port")
                if (port == null)
                    return DirectPlaybackSupport.NONE
                if (port.format != encoding) {
                    Log.w(TAG, "port ${port.name} was found, but is format ${port.format} instead of $encoding")
                    return DirectPlaybackSupport.NONE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (((port.flags ?: 0) and 0x20000) != 0)
                        return DirectPlaybackSupport.GAPLESS_OFFLOAD
                    if (((port.flags ?: 0) and 0x10) != 0)
                        return DirectPlaybackSupport.OFFLOAD
                    if (((port.flags ?: 0) and 1) != 0)
                        return DirectPlaybackSupport.DIRECT
                }
                // TODO compare port name with audio_policy.conf (O: audio_policy_configuration.xml?) to decide if direct
                return DirectPlaybackSupport.NONE
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
            return DirectPlaybackSupport.NONE
        }
        private fun encodingToNative(encoding: Int): Int {
            return when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> 2
                AudioFormat.ENCODING_PCM_16BIT -> 1
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 6
                AudioFormat.ENCODING_PCM_32BIT -> 3
                AudioFormat.ENCODING_PCM_FLOAT -> 5
                else -> TODO()
            }
        }
        private fun channelMaskToNative(channelMask: Int): Int {
            return when (channelMask) {
                AudioFormat.CHANNEL_OUT_MONO -> 1
                AudioFormat.CHANNEL_OUT_STEREO -> 3
                else -> TODO()
            }
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
        private external fun runWithOpenedOutput(streamType: Int, sampleRate: Int, format: Int, channelMask: Int,
                                                 frameCount: Int, trackFlags: Int, sessionId: Int,
                                                 selectedDeviceId: Int, bitRate: Int, durationUs: Long, hasVideo: Boolean,
                                                 isStreaming: Boolean, bitWidth: Int, offloadBufferSize: Int, usage: Int,
                                                 contentType: Int,
                                                 source: Int, attrFlags: Int, tags: String, action: Consumer<Int>)
        private external fun initDlsym(): Boolean
    }
    val ptr: Long
    var myState = State.NOT_SET
        private set
    init {
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
        ptr = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val ats = context.attributionSource
                val parcel = Parcel.obtain()
                try {
                    ats.writeToParcel(parcel, 0)
                    create(parcel)
                } finally {
                    parcel.recycle()
                }
            } else create(null)
        } catch (t: Throwable) {
            throw NativeTrackException("create() threw exception", t)
        }
        if (ptr == 0L) {
            throw NativeTrackException("create() returned NULL")
        }
    }
    private external fun create(@Suppress("unused") parcel: Parcel?): Long
    /*
     * If used with direct playback before Android 9, it is required to first check format support using
     * getDirectPlaybackSupport to avoid invoking bugs in the platform for unsupported formats (ie: requested
     * float32 but got int24 instead, createTrack and hence set fails as consequence). Below description assumes
     * this when considering possible scenarios.
     *
     * CAUTION: Until including Android 7.1, direct outputs could be reused even with different session IDs.
     *          If another app is using a direct (or offload) stream, we might end up with no audio (there can
     *          only ever be one client). However, this problem is isolated to MediaPlayer using compressed offload
     *          (or another app like us doing that), and us using hidden API to offload in the same format, sample
     *          rate and channel mask. Audio focus sadly isn't enough as the track needs to be released to avoid
     *          this bug, so either avoid other media player apps or using compressed offload on these versions.
     *
     * CAUTION: From Android 7.0 until Android 8.1, direct outputs with PCM modes int24, int32 or float32 were all
     *          treated as compatible. To avoid bugs, we should always release our handle to the direct output
     *          before attempting to switch formats. But if we're unlucky, on Android 7.x only, we may get a busy
     *          output with a different format anyway because another app has an active direct PCM track - which
     *          will result in set() failing. In theory, there's another case where set() could fail: on N/O, when
     *          we request float32 but APM selects int32 instead (but for that, the device would need need to
     *          support both formats AND have non-deterministic selection, which doesn't seem to happen in AOSP).
     *          This is because it tries to choose the best profile by bit depth but ignores that int32 and float32
     *          are different formats.
     */
    @Suppress("unused") // for parameters, this method has a few of them
    private external fun doSet(ptr: Long, streamType: Int, sampleRate: Int, format: Int, channelMask: Int,
                               frameCount: Int, trackFlags: Int, sessionId: Int, maxRequiredSpeed: Float,
                               selectedDeviceId: Int, bitRate: Int, durationUs: Long, hasVideo: Boolean,
                               isStreaming: Boolean, bitWidth: Int, offloadBufferSize: Int, usage: Int,
                               encapsulationMode: Int, contentId: Int, syncId: Int, contentType: Int,
                               source: Int, attrFlags: Int, tags: String, notificationFrames: Int,
                               doNotReconnect: Boolean, transferMode: Int): Int
    private external fun getRealPtr(@Suppress("unused") ptr: Long): Long
    private external fun dtor(@Suppress("unused") ptr: Long)
    fun set(): Boolean {
        // TODO assert maxRequiredSpeed==1.0f on L
        doSet(ptr, 3, 13370, 1, 3, 0, 0, 0, 1.0f, 0, 0, 0, false, false, 16, 0, 1, 0, 0, 0, 2, 0, 0, "", 0, false, 3)
        Log.e("hi", "dump:${AfFormatTracker.dumpInternal(getRealPtr(ptr))}")
        return myState == State.ALIVE
    }

    fun release() {
        myState = State.RELEASED
        dtor(ptr)
    }

    class NativeTrackException : Exception {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }
    enum class State {
        NOT_SET, // did not call set() yet
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
    private fun onMoreData(frameCount: Long, buffer: ByteBuffer): Long {
        // Be careful to not hold a reference to the buffer after returning. It immediately becomes invalid!
        Log.i(TAG, "onMoreData called: frameCount=$frameCount sizeBytes=${buffer.capacity()}")
        return 0
    }
    @Suppress("unused") // called from native, on callback thread (not main thread!)
    private fun onCanWriteMoreData(frameCount: Long, sizeBytes: Long) {
        Log.i(TAG, "onCanWriteMoreData called: frameCount=$frameCount sizeBytes=$sizeBytes")
    }
}