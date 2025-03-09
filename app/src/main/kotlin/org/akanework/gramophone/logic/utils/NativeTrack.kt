package org.akanework.gramophone.logic.utils

import android.content.Context
import android.os.Build
import android.os.Parcel

class NativeTrack(context: Context) {
    val ptr: Long
    var state = State.NOT_SET
        private set
    init {
        try {
            System.loadLibrary("libgramophone")
        } catch (t: Throwable) {
            throw NativeTrackException("failed to load libgramophone.so", t)
        }
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
    private external fun doSet(ptr: Long): Int
    fun set(): Boolean {
        doSet(ptr)
        return state == State.ALIVE
    }

    class NativeTrackException : Exception {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }
    enum class State {
        NOT_SET, // did not call set() yet
        DEAD_OBJECT, // we got killed by lower layer
        ALIVE, // ready to use
    }
}