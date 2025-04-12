package org.nift4.gramophone.hifitrack

class NativeLib {

    /**
     * A native method that is implemented by the 'hifitrack' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'hifitrack' library on application startup.
        init {
            System.loadLibrary("hifitrack")
        }
    }
}