package com.bbolbbol.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bbolbbol.app.data.api.PlaceDetail
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(vm: MapViewModel, onOpenMe: () -> Unit) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    fun locate() {
        vm.onLocating()
        fetchLocation(context) { loc ->
            if (loc != null) vm.onLocation(loc.latitude, loc.longitude) else vm.enterBrowseMode()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locate() else vm.enterBrowseMode()
    }

    // 이미 권한이 있으면 설명 화면을 건너뛴다.
    LaunchedEffect(Unit) {
        if (s.mode == LocationMode.EXPLAIN && hasLocationPermission(context)) locate()
    }

    when (s.mode) {
        LocationMode.EXPLAIN -> ExplainScreen(
            onAllow = {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onBrowse = vm::enterBrowseMode,
        )
        LocationMode.LOCATING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("내 위치를 찾는 중이에요")
            }
        }
        else -> MapContent(vm, onOpenMe, onRelocate = { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) })
    }
}

@Composable
private fun ExplainScreen(onAllow: () -> Unit, onBrowse: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("위치 권한이 필요해요", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "내 주변의 장소를 보여주고, 장소에 도착했는지 확인하려면 위치 정보가 필요해요. " +
                "위치는 주변 장소를 찾는 데에만 쓰여요.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) { Text("위치 권한 허용하기") }
        OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) { Text("둘러보기 모드로 시작") }
        Spacer(Modifier.height(8.dp))
        Text(
            "둘러보기 모드에서는 종로구 중심으로 장소를 볼 수 있지만 도착 인증은 할 수 없어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapContent(vm: MapViewModel, onOpenMe: () -> Unit, onRelocate: () -> Unit) {
    val s by vm.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        OsmMap(
            lat = s.lat,
            lng = s.lng,
            zoom = if (s.isBrowse) 14.0 else 16.0,
            places = s.places.map { MarkerData(it.placeId, it.name, it.lat, it.lng, it.isHot) },
            onMarker = vm::openDetail,
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Surface(tonalElevation = 3.dp, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("주변 장소", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = onOpenMe) { Text("내 정보") }
                    }
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = s.selectedCategory == null,
                                onClick = { vm.selectCategory(null) },
                                label = { Text("전체") },
                            )
                        }
                        items(s.categories, key = { it.code }) { c ->
                            FilterChip(
                                selected = s.selectedCategory == c.code,
                                onClick = { vm.selectCategory(c.code) },
                                label = { Text(c.name) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (s.isBrowse) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "둘러보기 모드 · 종로구 중심",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            TextButton(onClick = onRelocate) { Text("내 위치 사용") }
                        }
                    }
                }
            }
            if (s.loading) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) {
            when {
                s.error != null -> Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f, fill = false))
                        TextButton(onClick = vm::loadPlaces) { Text("다시 시도") }
                    }
                }
                !s.loading && s.places.isEmpty() -> Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("근처에 장소가 없어요.")
                        if (!s.isBrowse) TextButton(onClick = vm::enterBrowseMode) { Text("종로구 둘러보기") }
                    }
                }
                else -> Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
                    Text("장소 ${s.places.size}곳", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
    }

    if (s.detail != null || s.detailLoading || s.detailError != null) {
        ModalBottomSheet(onDismissRequest = vm::closeDetail) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                when {
                    s.detailLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    s.detail != null -> PlaceDetailContent(s.detail!!, s.isBrowse)
                    else -> Text(s.detailError ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PlaceDetailContent(d: PlaceDetail, browse: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(d.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
        if (d.isHot) Text("HOT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
    Text(d.categoryName ?: d.category, color = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(8.dp))
    (d.roadAddress ?: d.address)?.let { Text(it) }
    d.phone?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(8.dp))
    Text("도착 시 ${d.expectedPoint}P (기본 ${d.rewardPoint}P)")
    Text("오늘 방문 완료: ${if (d.isCompleted) "예" else "아니요"} · 누적 ${d.completedCount}명")
    if (browse) {
        Spacer(Modifier.height(8.dp))
        Text(
            "둘러보기 모드에서는 도착 인증을 할 수 없어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

private data class MarkerData(val id: Long, val title: String, val lat: Double, val lng: Double, val hot: Boolean)

@Composable
private fun OsmMap(
    lat: Double,
    lng: Double,
    zoom: Double,
    places: List<MarkerData>,
    onMarker: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(zoom)
            controller.setCenter(GeoPoint(lat, lng))
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    // 중심이 바뀔 때만 카메라를 옮긴다(사용자가 지도를 움직인 뒤에는 건드리지 않는다).
    LaunchedEffect(lat, lng, zoom) {
        mapView.controller.setZoom(zoom)
        mapView.controller.animateTo(GeoPoint(lat, lng))
    }
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.overlays.clear()
            places.forEach { p ->
                view.overlays.add(
                    Marker(view).apply {
                        position = GeoPoint(p.lat, p.lng)
                        title = if (p.hot) "${p.title} (HOT)" else p.title
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            onMarker(p.id)
                            true
                        }
                    },
                )
            }
            view.invalidate()
        },
    )
}

private fun hasLocationPermission(context: Context) =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** 현재 위치를 한 번 얻는다. 못 얻으면 null. 위치는 서버나 로그로 보내지 않는다. */
@SuppressLint("MissingPermission")
private fun fetchLocation(context: Context, onResult: (Location?) -> Unit) {
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
