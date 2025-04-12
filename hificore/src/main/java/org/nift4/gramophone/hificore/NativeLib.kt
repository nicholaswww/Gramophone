package org.nift4.gramophone.hificore

class NativeLib {

    /**
     * A native method that is implemented by the 'hificore' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'hificore' library on application startup.
        init {
            System.loadLibrary("hificore")
        }
    }
}