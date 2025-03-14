package org.akanework.gramophone.logic.utils

import android.content.Context
import android.os.Build
import android.os.Parcel
import android.util.Log

class NativeTrack(context: Context) {
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
    private external fun initDlsym(): Boolean
    private external fun create(@Suppress("unused") parcel: Parcel?): Long
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
}