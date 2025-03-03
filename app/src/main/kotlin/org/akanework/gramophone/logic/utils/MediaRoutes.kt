package org.akanework.gramophone.logic.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.mediarouter.media.MediaRouter
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.platformmedia.AudioFormatDescription

object MediaRoutes {
    private const val TAG = "MediaRoutes"
    private val addressRegex = Regex(".*, address=(.*), deduplicationIds=.*")

    fun printRoutes(context: Context) {
        val device = getSelectedAudioDevice(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.i("hi", "found route \"${device?.cleanUpProductName()}\" of type ${device.deviceTypeToString(context)}")
            /*
            audio chain in exoplayer is something like
            - file -> contains/outputs in downstream format
            - renderer (ie flac decoder / midi synth) -> outputs in audiosink input format
            - audio sink
              - audio processor
              -> outputs in audio track config
            - audio track -> converts from audio track format to audio hal format
            - audio hal -> if you're lucky it gives data to speaker
            TODO piece this together to UI?
             */
        }
    }

    fun getSelectedAudioDevice(context: Context): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val router = MediaRouter2.getInstance(context)
            val route = router.systemController.selectedRoutes.firstOrNull()
            return route?.getAudioDeviceForRoute(context)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val router = MediaRouter2.getInstance(context)
            val route = router.systemController.selectedRoutes.firstOrNull()
            return route?.getAudioDeviceForRoute(context)
                ?: MediaRouter.getInstance(context).selectedRoute.getAudioDeviceForRoute(context)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return MediaRouter.getInstance(context).selectedRoute.getAudioDeviceForRoute(context)
        } else return null
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun MediaRoute2Info.getAudioDeviceForRoute(context: Context): AudioDeviceInfo? {
        if (!isSystemRoute) {
            Log.e(TAG, "Route is not flagged as system route, however Gramophone only supports system routes?")
            return null
        }
        val audioManager = ContextCompat.getSystemService(context, AudioManager::class.java)!!
        val address = addressRegex.matchEntire(toString())?.groupValues?.getOrNull(1)
        if (address != null) {
            if (address != "null")
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .find { (it.address == address
                            // BT MAC addresses are censored by AudioManager but not MediaRouter2
                            || (it.address.startsWith("XX:XX:XX:XX:")
                            && it.address.substring(12) == address.substring(12))) &&
                            // if the internal speaker device has an address set, it will show up
                            // as TYPE_BUILTIN_EARPIECE, TYPE_BUILTIN_SPEAKER_SAFE and
                            // TYPE_TELEPHONY with the same address. just blacklist non-media types
                            it.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE &&
                            it.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE &&
                            it.type != AudioDeviceInfo.TYPE_TELEPHONY &&
                            it.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO &&
                            it.type != AudioDeviceInfo.TYPE_BUS &&
                            it.type != AudioDeviceInfo.TYPE_IP
                    }?.let { return it }
            Log.w(TAG, "Falling back to alternative code path because: didn't find audio device with address $address (there is: ${
                audioManager.getDevices(
                    AudioManager.GET_DEVICES_OUTPUTS
                ).joinToString { it.address }
            })")
        } else
            Log.e(TAG, "Falling back to alternative code path because: failed to parse address in $this")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return null // type field new in U
        // These proper mapping are established since Android V (but U ones are usable):
        // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/media/AudioManagerRouteController.java;l=604;drc=9500d2b91750c1fea05e6ab82f80a925179d5f3a
        return when (type) {
            MediaRoute2Info.TYPE_BLUETOOTH_A2DP ->
                audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, name = name)
            MediaRoute2Info.TYPE_BUILTIN_SPEAKER -> audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            MediaRoute2Info.TYPE_WIRED_HEADSET -> audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            MediaRoute2Info.TYPE_WIRED_HEADPHONES -> audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            MediaRoute2Info.TYPE_HDMI -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
                    audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_HDMI)
                else
                    audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_HDMI,
                        AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC)
            }
            MediaRoute2Info.TYPE_USB_DEVICE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
                    audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_USB_DEVICE)
                else
                    audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_USB_DEVICE,
                        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY)
            }
            MediaRoute2Info.TYPE_USB_ACCESSORY -> audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_USB_ACCESSORY)
            MediaRoute2Info.TYPE_DOCK -> audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_DOCK, AudioDeviceInfo.TYPE_DOCK_ANALOG)
            MediaRoute2Info.TYPE_USB_HEADSET -> audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_USB_HEADSET)
            MediaRoute2Info.TYPE_HEARING_AID -> audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_HEARING_AID)
            MediaRoute2Info.TYPE_BLE_HEADSET ->
                audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST, name = name)
            MediaRoute2Info.TYPE_HDMI_ARC -> audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_HDMI_ARC)
            MediaRoute2Info.TYPE_HDMI_EARC -> audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_HDMI_EARC)
            MediaRoute2Info.TYPE_UNKNOWN -> audioManager.firstOutputDeviceByType(
                AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_AUX_LINE)
            else -> {
                Log.e(TAG, "Route type $type is not mapped to audio device type?")
                null
            }
        }
    }

    // Approximation of audio device based on best effort
    // Inspired by https://github.com/timschneeb/RootlessJamesDSP/blob/593c0dc/app/src/main/java/me/timschneeberger/rootlessjamesdsp/utils/RoutingObserver.kt
    @SuppressLint("DiscouragedApi")
    @RequiresApi(Build.VERSION_CODES.M)
    fun MediaRouter.RouteInfo.getAudioDeviceForRoute(context: Context): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            throw IllegalStateException("getAudioDeviceForRoute must not be called on U+")
        // These internal resources changed in U!
        if (!isSystemRoute) {
            Log.e(TAG, "Route is not flagged as system route, however Gramophone only supports system routes?")
            return null
        }
        val audioManager = ContextCompat.getSystemService(context, AudioManager::class.java)!!
        if (isBluetooth) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
                    AudioDeviceInfo.TYPE_BLE_BROADCAST, name = name)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, name = name)
            } else {
                audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, name = name)
            }
        }
        try {
            if (isDeviceSpeaker)
                return audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        } catch (_: Resources.NotFoundException) {
            // shrug
        }
        try {
            if (isDefault && TextUtils.equals(Resources.getSystem().getText(Resources.getSystem()
                .getIdentifier("default_audio_route_name_hdmi", "string", "android")), name))
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_HDMI,
                        AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC)
                } else {
                    audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_HDMI,
                        AudioDeviceInfo.TYPE_HDMI_ARC)
                }
        } catch (_: Resources.NotFoundException) {
            // shrug
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isDefault &&
                TextUtils.equals(Resources.getSystem().getText(Resources.getSystem()
                    .getIdentifier("default_audio_route_name_usb", "string", "android")), name))
                return audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY)
        } catch (_: Resources.NotFoundException) {
            // shrug
        }
        try {
            if (isDefault && TextUtils.equals(Resources.getSystem().getText(Resources.getSystem()
                    .getIdentifier("default_audio_route_name_headphones", "string", "android")), name))
                return audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        } catch (_: Resources.NotFoundException) {
            // shrug
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun AudioManager.firstOutputDeviceByType(vararg type: Int, name: CharSequence? = null): AudioDeviceInfo? {
        val devices = getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val devicesByType = devices.filter { type.contains(it.type) }
        if (devicesByType.isEmpty()) {
            Log.w(TAG, "firstOutputDeviceByType() returning empty result")
            return null
        }
        if (devicesByType.size == 1)
            return devicesByType.first()
        if (name == null)
            return devicesByType.find { type[0] == it.type } ?: devicesByType.first()
        val devicesByName = devicesByType.filter { it.productName.contentEquals(name)
                || it.cleanUpProductName().contentEquals(name) }
        if (devicesByName.size == 1)
            return devicesByName[0]
        if (devicesByName.size > 1) {
            // Same model of USB connected twice?
            val theDevice = devicesByName[0]
            devicesByName.forEachIndexed { index, device ->
                if (!((Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            theDevice.audioProfiles == device.audioProfiles) &&
                            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                                    (theDevice.encapsulationModes == device.encapsulationModes &&
                                            theDevice.encapsulationMetadataTypes == device.encapsulationMetadataTypes)) &&
                            theDevice.channelCounts == device.channelCounts &&
                            theDevice.encodings == device.encodings &&
                            theDevice.sampleRates == device.sampleRates &&
                            theDevice.channelMasks == device.channelMasks &&
                            theDevice.channelIndexMasks == device.channelIndexMasks &&
                            theDevice.type == device.type)
                ) {
                    Log.e(
                        TAG,
                        "Weird, got more than one AudioDeviceInfo with same name but not same content?"
                    )
                    return null
                }
            }
            // Ok great, while we did find >1 devices, they all have same capabilities so it
            // doesn't matter which one we look at.
            return theDevice
        }
        Log.e(
            TAG,
            "Weird, got more than one AudioDeviceInfo for type but none match desired name? desired name: $name"
        )
        devicesByType.forEach {
            Log.e(
                TAG,
                "This device does not match desired name $name because it's actually ${it.productName}"
            )
        }
        return null
    }

    fun AudioDeviceInfo?.deviceTypeToString(context: Context) =
        if (this == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            context.getString(R.string.device_type_unknown)
        else when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> context.getString(R.string.device_type_bluetooth_a2dp)
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> context.getString(R.string.device_type_builtin_speaker)
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> context.getString(R.string.device_type_wired_headset)
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> context.getString(R.string.device_type_wired_headphones)
            AudioDeviceInfo.TYPE_HDMI -> context.getString(R.string.device_type_hdmi)
            AudioDeviceInfo.TYPE_USB_DEVICE -> context.getString(R.string.device_type_usb_device)
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> context.getString(R.string.device_type_usb_accessory)
            AudioDeviceInfo.TYPE_DOCK -> context.getString(
                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ) R.string.device_type_dock_digital
                else R.string.device_type_dock
            )

            AudioDeviceInfo.TYPE_DOCK_ANALOG -> context.getString(R.string.device_type_dock_analog)
            AudioDeviceInfo.TYPE_USB_HEADSET -> context.getString(R.string.device_type_usb_headset)
            AudioDeviceInfo.TYPE_HEARING_AID -> context.getString(R.string.device_type_hearing_aid)
            AudioDeviceInfo.TYPE_BLE_HEADSET -> context.getString(R.string.device_type_ble_headset)
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> context.getString(R.string.device_type_ble_broadcast)
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> context.getString(R.string.device_type_ble_speaker)
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> context.getString(R.string.device_type_line_digital)
            AudioDeviceInfo.TYPE_LINE_ANALOG -> context.getString(R.string.device_type_line_analog)
            AudioDeviceInfo.TYPE_AUX_LINE -> context.getString(R.string.device_type_aux_line)
            AudioDeviceInfo.TYPE_HDMI_ARC -> context.getString(R.string.device_type_hdmi_arc)
            AudioDeviceInfo.TYPE_HDMI_EARC -> context.getString(R.string.device_type_hdmi_earc)
            else -> throw IllegalStateException("unknown device type $type")
        }

    @RequiresApi(Build.VERSION_CODES.M)
    fun AudioDeviceInfo.cleanUpProductName(): CharSequence = productName.let {
        if (it.startsWith("USB-Audio - "))
            it.substring("USB-Audio - ".length)
        else it
    }
}

object AudioTrackHalInfoDetector {
    private const val TAG = "AudioTrackHalInfoDetect"

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

    fun getHalSampleRate(audioTrack: AudioTrack): Int? {
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
    private external fun getHalSampleRateInternal(audioTrackPtr: Long): Int

    private fun obtainParcel(binder: IBinder) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Parcel.obtain(binder) else Parcel.obtain()

    fun getHalChannelCount(audioTrack: AudioTrack): Int? {
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
    private external fun getHalChannelCountInternal(audioTrackPtr: Long): Int

    /**
     * Return AudioFlinger HAL format as aaudio_format_t.
     * If the HAL format cannot be represented by aaudio_format_t, returns AAUDIO_FORMAT_INVALID.
     * The conversion to aaudio_format_t is done using system libraries, so this method has the
     * advantage of being valid for the indefinite future and not relying on magic numbers.
     */
    fun getHalFormat(audioTrack: AudioTrack): AAudioFormat? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return AAudioFormat.AAUDIO_FORMAT_INVALID
        val fmt = getHalFormatInner(audioTrack)?.let { audioFormatToAAudioFormat(it) }
        return AAudioFormat.entries.find { it.format == fmt }
            ?: run {
                Log.e(TAG, "got aaudio_format_t $fmt which is missing on kotlin side")
                AAudioFormat.AAUDIO_FORMAT_INVALID
            }
    }

    /* Public NDK API from AAudio.h */
    enum class AAudioFormat(val format: Int) {
        AAUDIO_FORMAT_INVALID(-1),
        AAUDIO_FORMAT_UNSPECIFIED(0),

        /**
         * This format uses the int16_t data type.
         * The maximum range of the data is -32768 (0x8000) to 32767 (0x7FFF).
         */
        AAUDIO_FORMAT_PCM_I16(1),

        /**
         * This format uses the float data type.
         * The nominal range of the data is [-1.0f, 1.0f).
         * Values outside that range may be clipped.
         *
         * See also the float Data in
         * <a href="/reference/android/media/AudioTrack#write(float[],%20int,%20int,%20int)">
         *   write(float[], int, int, int)</a>.
         */
        AAUDIO_FORMAT_PCM_FLOAT(2),

        /**
         * This format uses 24-bit samples packed into 3 bytes.
         * The bytes are in little-endian order, so the least significant byte
         * comes first in the byte array.
         *
         * The maximum range of the data is -8388608 (0x800000)
         * to 8388607 (0x7FFFFF).
         *
         * Note that the lower precision bits may be ignored by the device.
         *
         * Available since API level 31.
         */
        AAUDIO_FORMAT_PCM_I24_PACKED(3),

        /**
         * This format uses 32-bit samples stored in an int32_t data type.
         * The maximum range of the data is -2147483648 (0x80000000)
         * to 2147483647 (0x7FFFFFFF).
         *
         * Note that the lower precision bits may be ignored by the device.
         *
         * Available since API level 31.
         */
        AAUDIO_FORMAT_PCM_I32(4),

        /**
         * This format is used for compressed audio wrapped in IEC61937 for HDMI
         * or S/PDIF passthrough.
         *
         * Unlike PCM playback, the Android framework is not able to do format
         * conversion for IEC61937. In that case, when IEC61937 is requested, sampling
         * rate and channel count or channel mask must be specified. Otherwise, it may
         * fail when opening the stream. Apps are able to get the correct configuration
         * for the playback by calling
         * <a href="/reference/android/media/AudioManager#getDevices(int)">
         *   AudioManager#getDevices(int)</a>.
         *
         * Available since API level 34.
         */
        AAUDIO_FORMAT_IEC61937(5),

        /**
         * This format is used for audio compressed in MP3 format.
         */
        AAUDIO_FORMAT_MP3(6),

        /**
         * This format is used for audio compressed in AAC LC format.
         */
        AAUDIO_FORMAT_AAC_LC(7),

        /**
         * This format is used for audio compressed in AAC HE V1 format.
         */
        AAUDIO_FORMAT_AAC_HE_V1(8),

        /**
         * This format is used for audio compressed in AAC HE V2 format.
         */
        AAUDIO_FORMAT_AAC_HE_V2(9),

        /**
         * This format is used for audio compressed in AAC ELD format.
         */
        AAUDIO_FORMAT_AAC_ELD(10),

        /**
         * This format is used for audio compressed in AAC XHE format.
         */
        AAUDIO_FORMAT_AAC_XHE(11),

        /**
         * This format is used for audio compressed in OPUS.
         */
        AAUDIO_FORMAT_OPUS(12)
    }

    /**
     * Return AudioFlinger HAL format as string converted from audio_format_t.
     * The conversion to string is NOT done using system libraries, so this method has the
     * disadvantage of being invalid in future AOSP versions because its reliance on magic numbers.
     */
    fun getHalFormat2(audioTrack: AudioTrack): String? =
        getHalFormatInner(audioTrack)?.let { audioFormatToString(it) }

    private fun getHalFormatInner(audioTrack: AudioTrack): Int? {
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
            af.transact(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 4 else 5,
                inParcel, outParcel, 0
            )
            if (!readStatus(outParcel))
                return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val format = AudioFormatDescription.CREATOR.createFromParcel(outParcel)
                // TODO...
                return null
            } else
                return outParcel.readInt()
        } finally {
            inParcel.recycle()
            outParcel.recycle()
        }
    }
    private external fun getHalFormatInternal(audioTrackPtr: Long): Int
    private external fun audioFormatToString(format: Int): String?
    private external fun audioFormatToAAudioFormat(format: Int): Int

    /*private*/ fun getOutput(audioTrack: AudioTrack): Int? {
        if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
            throw IllegalArgumentException("cannot get hal output for released AudioTrack")
        return try {
            getOutputInternal(getAudioTrackPtr(audioTrack))
        } catch (e: Throwable) {
            Log.e(TAG, Log.getStackTraceString(e))
            null
        }
    }
    private external fun getOutputInternal(audioTrackPtr: Long): Int

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