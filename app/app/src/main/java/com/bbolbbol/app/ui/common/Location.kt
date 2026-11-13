package com.bbolbbol.app.ui.common

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

fun hasLocationPermission(context: Context) =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** 현재 위치를 한 번 얻는다. 못 얻으면 null. 위치는 서버나 로그로 보내지 않는다. */
@SuppressLint("MissingPermission")
fun fetchLocation(context: Context, onResult: (Location?) -> Unit) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    if (provider == null || !hasLocationPermission(context)) {
        onResult(null)
        return
    }
    val last = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { loc ->
            onResult(loc ?: last)
        }
    } else {
        // API 26~29: 최근 위치만 쓴다. 없으면 둘러보기 모드로 넘어간다.
        onResult(last)
    }
}
