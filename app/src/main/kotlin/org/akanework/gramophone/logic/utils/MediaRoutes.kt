package org.akanework.gramophone.logic.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.media.MediaRouter2
import android.os.Build
import android.text.TextUtils
import android.util.Log
import androidx.mediarouter.media.MediaRouter
import org.akanework.gramophone.R

object MediaRoutes {
    fun printRoutes(context: Context) {
        val router = MediaRouter.getInstance(context)
        // TODO why does compat not return a device type even on U? is MediaRoute2 even used by androidx?
        Log.i("hi", "found route of type: " + deviceTypeToString(context, router.selectedRoute.getDeviceTypeCompat()))
        val r2 = MediaRouter2.getInstance(context)
        Log.i("hi", "found route2 of type: " + r2.controllers.first().selectedRoutes[0].type)
    }

    // Inspired by https://github.com/timschneeb/RootlessJamesDSP/blob/593c0dc/app/src/main/java/me/timschneeberger/rootlessjamesdsp/utils/RoutingObserver.kt
    @SuppressLint("DiscouragedApi")
    @MediaRouter.RouteInfo.DeviceType
    fun MediaRouter.RouteInfo.getDeviceTypeCompat(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            deviceType != 0 /* MediaRouter.RouteInfo.DEVICE_TYPE_UNKNOWN */)
            return deviceType // since U, framework gives us this value in all cases
        if (isBluetooth)
            return MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP
        try {
            if (isDeviceSpeaker)
                return MediaRouter.RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER
        } catch (_: Resources.NotFoundException) {
            // shrug
        }
        try {
            if (isDefault && TextUtils.equals(Resources.getSystem().getText(Resources.getSystem()
                .getIdentifier("default_audio_route_name_hdmi", "string", "android")), name))
                return MediaRouter.RouteInfo.DEVICE_TYPE_HDMI
        } catch (_: Resources.NotFoundException) {
            // shrug
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isDefault &&
                TextUtils.equals(Resources.getSystem().getText(Resources.getSystem()
                    .getIdentifier("default_audio_route_name_usb", "string", "android")), name))
                return MediaRouter.RouteInfo.DEVICE_TYPE_USB_DEVICE
        } catch (_: Resources.NotFoundException) {
            // shrug
        }
        try {
            if (isDefault && TextUtils.equals(Resources.getSystem().getText(Resources.getSystem()
                    .getIdentifier("default_audio_route_name_headphones", "string", "android")), name))
                return MediaRouter.RouteInfo.DEVICE_TYPE_WIRED_HEADPHONES
        } catch (_: Resources.NotFoundException) {
            // shrug
        }
        @Suppress("WrongConstant")
        return 0 /* MediaRouter.RouteInfo.DEVICE_TYPE_UNKNOWN */
    }

    fun deviceTypeToString(context: Context, type: Int) = when (type) {
        0 /* MediaRouter.RouteInfo.DEVICE_TYPE_UNKNOWN */ -> context.getString(R.string.device_type_unknown)
        MediaRouter.RouteInfo.DEVICE_TYPE_TV -> context.getString(R.string.device_type_tv)
        MediaRouter.RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER -> context.getString(R.string.device_type_remote_speaker)
        MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP -> context.getString(R.string.device_type_bluetooth_a2dp)
        MediaRouter.RouteInfo.DEVICE_TYPE_AUDIO_VIDEO_RECEIVER -> context.getString(R.string.device_type_audio_video_receiver)
        MediaRouter.RouteInfo.DEVICE_TYPE_TABLET -> context.getString(R.string.device_type_remote_tablet)
        MediaRouter.RouteInfo.DEVICE_TYPE_TABLET_DOCKED -> context.getString(R.string.device_type_remote_tablet_docked)
        MediaRouter.RouteInfo.DEVICE_TYPE_COMPUTER -> context.getString(R.string.device_type_remote_computer)
        MediaRouter.RouteInfo.DEVICE_TYPE_GAME_CONSOLE -> context.getString(R.string.device_type_game_remote_console)
        MediaRouter.RouteInfo.DEVICE_TYPE_CAR -> context.getString(R.string.device_type_remote_car)
        MediaRouter.RouteInfo.DEVICE_TYPE_SMARTWATCH -> context.getString(R.string.device_type_remote_smartwatch)
        MediaRouter.RouteInfo.DEVICE_TYPE_SMARTPHONE -> context.getString(R.string.device_type_remote_smartphone)
        MediaRouter.RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER -> context.getString(R.string.device_type_builtin_speaker)
        MediaRouter.RouteInfo.DEVICE_TYPE_WIRED_HEADSET -> context.getString(R.string.device_type_wired_headset)
        MediaRouter.RouteInfo.DEVICE_TYPE_WIRED_HEADPHONES -> context.getString(R.string.device_type_wired_headphones)
        MediaRouter.RouteInfo.DEVICE_TYPE_HDMI -> context.getString(R.string.device_type_hdmi)
        MediaRouter.RouteInfo.DEVICE_TYPE_USB_DEVICE -> context.getString(R.string.device_type_usb_device)
        MediaRouter.RouteInfo.DEVICE_TYPE_USB_ACCESSORY -> context.getString(R.string.device_type_usb_accessory)
        MediaRouter.RouteInfo.DEVICE_TYPE_DOCK -> context.getString(R.string.device_type_dock)
        MediaRouter.RouteInfo.DEVICE_TYPE_USB_HEADSET -> context.getString(R.string.device_type_usb_headset)
        MediaRouter.RouteInfo.DEVICE_TYPE_HEARING_AID -> context.getString(R.string.device_type_hearing_aid)
        MediaRouter.RouteInfo.DEVICE_TYPE_BLE_HEADSET -> context.getString(R.string.device_type_ble_headset)
        MediaRouter.RouteInfo.DEVICE_TYPE_HDMI_ARC -> context.getString(R.string.device_type_hdmi_arc)
        MediaRouter.RouteInfo.DEVICE_TYPE_HDMI_EARC -> context.getString(R.string.device_type_hdmi_earc)
        MediaRouter.RouteInfo.DEVICE_TYPE_GROUP -> context.getString(R.string.device_type_group)
        else -> throw IllegalStateException("unknown device type $type")
    }
}