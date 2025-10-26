package org.nift4.gramophone.hificore

object AudioSystemHiddenApi {
    private const val TAG = "AudioSystemHiddenApi"
    private const val TRACE_TAG = "GpNativeTrace2"
    private val libLoaded
        get() = AudioTrackHiddenApi.libLoaded

    /*
     * descriptors:
     * getDeviceIdsForIo
     * getFrameCountHAL
     * getLatency
     * getSamplingRate
     * getFrameCount
     * + something to enumerate AudioIoDescriptor and io open & close callbacks + get format
     * + get channel mask + get patch + get is input
     *
     * if needed, for now not needed: getAAudioHardwareBurstMinUsec, getAAudioMixerBurstCount
     *
     * other ideas:
     * getProductStrategyFromAudioAttributes
     * listAudioProductStrategies
     * getMasterBalance
     * getMasterMono
     * listDeclaredDevicePorts
     * getDeviceConnectionState
     * getRenderPosition
     * addErrorCallback
     * removeErrorCallback
     * setParameters (ver with io handle - or is it a job for NativeTrack?)
     * getParameters (ver with io handle - or is it a job for NativeTrack?)
     * getMasterVolume
     */
}