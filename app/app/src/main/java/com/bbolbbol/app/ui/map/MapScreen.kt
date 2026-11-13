package com.bbolbbol.app.ui.map

import android.Manifest
import android.location.Location
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bbolbbol.app.data.api.PlaceDetail
import com.bbolbbol.app.ui.common.CategoryTile
import com.bbolbbol.app.ui.common.InfoChip
import com.bbolbbol.app.ui.common.PillChip
import com.bbolbbol.app.ui.common.PrimaryButton
import com.bbolbbol.app.ui.common.ScreenTitle
import com.bbolbbol.app.ui.common.StatusChip
import com.bbolbbol.app.ui.common.StatusKind
import com.bbolbbol.app.ui.common.fetchLocation
import com.bbolbbol.app.ui.common.hasLocationPermission
import com.bbolbbol.app.ui.theme.BbolColors
import com.bbolbbol.app.ui.theme.BbolShapes
import com.bbolbbol.app.util.Formatters
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
fun MapScreen(
    vm: MapViewModel,
    onOpenRecommend: (lat: Double, lng: Double) -> Unit,
    pickedPlaceId: Long? = null,
    onPickedConsumed: () -> Unit = {},
) {
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

    // 홈/추천에서 고른 장소는 지도에서 상세 시트를 열고 카메라를 옮긴다.
    // 위치 권한 설명 화면에서는 시트가 보이지 않으니, 지도가 뜰 때까지 기다린다.
    LaunchedEffect(pickedPlaceId, s.mode) {
        if (pickedPlaceId != null && s.mode != LocationMode.EXPLAIN && s.mode != LocationMode.LOCATING) {
            vm.openDetail(pickedPlaceId, centerOnPlace = true)
            onPickedConsumed()
        }
    }

    // 이미 권한이 있으면 설명 화면을 건너뛴다.
    LaunchedEffect(Unit) {
        if (s.mode == LocationMode.EXPLAIN && hasLocationPermission(context)) locate()
    }

    when (s.mode) {
        LocationMode.EXPLAIN -> ExplainScreen(
            onAllow = { launcher.launch(LOCATION_PERMISSIONS) },
            onBrowse = vm::enterBrowseMode,
        )
        LocationMode.LOCATING -> Box(
            Modifier.fillMaxSize().background(BbolColors.Background),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = BbolColors.Violet)
                Spacer(Modifier.height(12.dp))
                Text("내 위치를 찾는 중이에요")
            }
        }
        else -> MapContent(vm, onOpenRecommend, onRelocate = { launcher.launch(LOCATION_PERMISSIONS) })
    }
}

@Composable
private fun ExplainScreen(onAllow: () -> Unit, onBrowse: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BbolColors.Background)
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("위치 권한이 필요해요", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "내 주변의 장소를 보여주고, 장소에 도착했는지 확인하려면 위치 정보가 필요해요. " +
                "위치는 주변 장소를 찾는 데에만 쓰여요.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("위치 권한 허용하기", onClick = onAllow)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth(), shape = BbolShapes.Pill) {
            Text("둘러보기 모드로 시작")
        }
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
private fun MapContent(
    vm: MapViewModel,
    onOpenRecommend: (Double, Double) -> Unit,
    onRelocate: () -> Unit,
) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        OsmMap(
            lat = s.focusLat ?: s.lat,
            lng = s.focusLng ?: s.lng,
            zoom = if (s.isBrowse && s.focusLat == null) 14.0 else 16.0,
            places = s.places.map { MarkerData(it.placeId, it.name, it.lat, it.lng, it.isHot) }.let { list ->
                // 추천에서 고른 장소가 현재 검색 반경 밖이어도 마커를 보여준다.
                val d = s.detail
                if (d != null && list.none { it.id == d.placeId }) list + MarkerData(d.placeId, d.name, d.lat, d.lng, d.isHot) else list
            },
            onMarker = vm::openDetail,
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(top = 16.dp, bottom = 12.dp)) {
                    ScreenTitle("퀘스트", Modifier.padding(horizontal = 20.dp)) {
                        PillChip("뽈뽈 추천", selected = false, onClick = { onOpenRecommend(s.lat, s.lng) })
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            PillChip("전체", s.selectedCategory == null, onClick = { vm.selectCategory(null) })
                        }
                        items(s.categories, key = { it.code }) { c ->
                            PillChip(c.name, s.selectedCategory == c.code, onClick = { vm.selectCategory(c.code) })
                        }
                    }
                    if (s.isBrowse) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "둘러보기 모드 · 종로구 중심",
                                style = MaterialTheme.typography.labelMedium,
                                color = BbolColors.Violet,
                            )
                            TextButton(onClick = onRelocate) { Text("내 위치 사용", color = BbolColors.Violet) }
                        }
                    }
                }
            }
            if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = BbolColors.Violet)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
            when {
                s.error != null -> MapNotice {
                    Text(s.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f, fill = false))
                    TextButton(onClick = vm::loadPlaces) { Text("다시 시도", color = BbolColors.Violet) }
                }
                !s.loading && s.places.isEmpty() -> MapNotice {
                    Text("근처에 장소가 없어요.")
                    if (!s.isBrowse) TextButton(onClick = vm::enterBrowseMode) { Text("종로구 둘러보기", color = BbolColors.Violet) }
                }
                else -> MapNotice { Text("장소 ${s.places.size}곳", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }

    s.arrivalOutcome?.let { ArrivalDialog(it, vm::dismissArrivalOutcome) }

    if (s.detail != null || s.detailLoading || s.detailError != null) {
        ModalBottomSheet(
            onDismissRequest = vm::closeDetail,
            containerColor = Color.White,
            shape = BbolShapes.Sheet,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                when {
                    s.detailLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = BbolColors.Violet)
                    s.detail != null -> PlaceDetailContent(
                        d = s.detail!!,
                        blockedReason = s.arrivalBlockedReason(hasLocationPermission(context)),
                        arriving = s.arriving,
                        onArrive = {
                            val id = s.detail!!.placeId
                            fetchLocation(context) { loc ->
                                if (loc != null) vm.arrive(id, loc.toArrivalFix()) else vm.onArrivalLocationFailed()
                            }
                        },
                    )
                    else -> Text(s.detailError ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 하단에 떠 있는 둥근 흰 알림 카드. */
@Composable
private fun MapNotice(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(shape = BbolShapes.Pill, color = Color.White, shadowElevation = 6.dp) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun PlaceDetailContent(d: PlaceDetail, blockedReason: String?, arriving: Boolean, onArrive: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryTile(d.category, size = 64.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, style = MaterialTheme.typography.titleLarge)
            Text(d.categoryName ?: Formatters.categoryName(d.category), color = BbolColors.InkSoft)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        InfoChip("도착 시 ${d.expectedPoint}P")
        if (d.isHot) StatusChip("HOT", StatusKind.IN_PROGRESS)
        if (d.isCompleted) StatusChip("오늘 완료", StatusKind.DONE) else StatusChip("오늘 미방문", StatusKind.NOT_STARTED)
    }
    Spacer(Modifier.height(12.dp))
    (d.roadAddress ?: d.address)?.let { Text(it) }
    d.phone?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(4.dp))
    Text(
        "기본 ${d.rewardPoint}P · 누적 ${d.completedCount}명 방문",
        style = MaterialTheme.typography.bodySmall,
        color = BbolColors.InkSoft,
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton("도착 인증하기", onClick = onArrive, enabled = blockedReason == null && !arriving, loading = arriving)
    if (blockedReason != null) {
        Spacer(Modifier.height(8.dp))
        Text(blockedReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun ArrivalDialog(outcome: ArrivalOutcome, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = BbolShapes.Card,
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인", color = BbolColors.Violet) } },
        title = { Text(if (outcome is ArrivalOutcome.Success) "도착 인증 완료" else "도착 인증 실패") },
        text = {
            when (outcome) {
                is ArrivalOutcome.Success -> Column {
                    val r = outcome.result
                    Text(r.placeName, style = MaterialTheme.typography.titleMedium)
                    if (r.isFirstVisit) Text("첫 방문이에요", color = MaterialTheme.colorScheme.primary)
                    Text("${r.earnedPoint}P를 받았어요. 총 ${r.totalPoint}P")
                    Spacer(Modifier.height(12.dp))
                    com.bbolbbol.app.ui.photo.ArrivalPhotoSection(r)
                }
                is ArrivalOutcome.Error -> Text(outcome.message)
            }
        },
    )
}

/** Location.isMock은 API 31부터, 그 전에는 isFromMockProvider를 쓴다. */
@Suppress("DEPRECATION")
private fun Location.toArrivalFix() = ArrivalFix(
    lat = latitude,
    lng = longitude,
    accuracy = accuracy.toDouble(),
    isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider,
)

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
