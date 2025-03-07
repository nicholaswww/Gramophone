package org.akanework.gramophone.logic.utils

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

data class BtCodecInfo(val codec: String?, val sampleRateHz: Int?, val channelConfig: Int?, val bitsPerSample: Int?) {
    companion object {
        private const val TAG = "BtCodecInfo"

        @RequiresApi(Build.VERSION_CODES.O)
        fun fromCodecConfig(codecConfig: BluetoothCodecConfig?): BtCodecInfo {
            Log.i(TAG, codecConfig.toString())
            return BtCodecInfo(null, null, null, null) // TODO
        }

        // TODO test stability
        @RequiresApi(Build.VERSION_CODES.O)
        fun getCodec(context: Context, callback: (BtCodecInfo?) -> Unit) {
            val adapter = ContextCompat.getSystemService(context, BluetoothManager::class.java)!!.adapter
            if (!adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    val a2dp = proxy as BluetoothA2dp
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "missing bluetooth permission")
                        callback(null)
                        return
                    }
                    val cd = a2dp.connectedDevices
                    val device = if (cd.size <= 1) cd.firstOrNull() else try {
                        BluetoothA2dp::class.java.getMethod("getActiveDevice").invoke(a2dp) as BluetoothDevice?
                    } catch (t: Throwable) {
                        Log.e(TAG, Log.getStackTraceString(t))
                        callback(null)
                        return
                    }
                    if (device == null) return
                    @SuppressLint("NewApi")
                    val codec = try {
                        BluetoothA2dp::class.java.getMethod("getCodecStatus", BluetoothDevice::class.java)
                            .invoke(a2dp, device) as BluetoothCodecStatus?
                    } catch (t: Throwable) {
                        Log.e(TAG, Log.getStackTraceString(t))
                        null
                    }?.codecConfig
                    callback(fromCodecConfig(codec))
                }

                override fun onServiceDisconnected(profile: Int) {
                    // do nothing
                }
            }, BluetoothProfile.A2DP)) {
                Log.e(TAG, "getProfileProxy error")
                callback(null)
            }
        }
    }
}