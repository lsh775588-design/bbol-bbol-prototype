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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.overlay.CopyrightOverlay
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
    onOpenOnboarding: () -> Unit = {},
) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    fun locate() {
        vm.onLocating()
        fetchLocation(context) { loc ->
            if (loc != null) vm.onLocation(loc.latitude, loc.longitude, loc.accuracy) else vm.enterBrowseMode()
        }
    }

    // "내 위치로" 버튼: 화면을 바꾸지 않고 위치만 다시 읽어 카메라를 옮긴다.
    fun recenter() {
        if (!hasLocationPermission(context)) {
            vm.enterBrowseMode()
            return
        }
        fetchLocation(context) { loc -> if (loc != null) vm.onLocation(loc.latitude, loc.longitude, loc.accuracy) }
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
        else -> MapContent(vm, onOpenRecommend, onRelocate = { launcher.launch(LOCATION_PERMISSIONS) }, onRecenter = ::recenter, onOpenOnboarding = onOpenOnboarding)
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
    onRecenter: () -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        val markers = s.places.map {
            MarkerData(it.placeId, it.name, it.category, it.lat, it.lng, it.isHot, pinState(it.isCompleted, it.placeId in s.acceptedIds))
        }.let { list ->
            // 추천에서 고른 장소가 현재 검색 반경 밖이어도 마커를 보여준다.
            val d = s.detail
            if (d != null && list.none { it.id == d.placeId }) {
                list + MarkerData(d.placeId, d.name, d.category, d.lat, d.lng, d.isHot, pinState(d.isCompleted, d.placeId in s.acceptedIds))
            } else list
        }
        OsmMap(
            lat = s.focusLat ?: s.lat,
            lng = s.focusLng ?: s.lng,
            zoom = if (s.isBrowse && s.focusLat == null) BROWSE_ZOOM else 16.0,
            recenterNonce = s.recenterNonce,
            fitToPlaces = s.isBrowse && s.focusLat == null,
            places = markers,
            selectedId = s.detail?.placeId,
            myLocation = if (s.isBrowse) null else Triple(s.lat, s.lng, s.accuracy),
            onMarker = vm::openDetail,
            modifier = Modifier.fillMaxSize(),
        )
        // 상태바와 제목이 지도 위에서도 읽히도록 위쪽을 흰색에서 투명으로 옅게 덮는다.
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.96f),
                        0.7f to Color.White.copy(alpha = 0.85f),
                        1f to Color.White.copy(alpha = 0f),
                    ),
                )
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 22.dp),
        ) {
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
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = BbolShapes.Pill,
                    color = BbolColors.Lavender,
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("둘러보기 모드 · 종로구 중심", style = MaterialTheme.typography.labelMedium, color = BbolColors.VioletDeep)
                        TextButton(onClick = onRelocate) { Text("내 위치 사용", color = BbolColors.Violet) }
                    }
                }
            }
            if (s.loading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = BbolColors.Violet)
            }
        }
        // 내 위치로 돌아가는 둥근 버튼.
        Surface(
            onClick = onRecenter,
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 20.dp).size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MyLocation, contentDescription = "내 위치로", tint = BbolColors.Violet)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(12.dp).padding(end = 40.dp)) {
            when {
                s.error != null -> MapNotice {
                    Text(s.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f, fill = false))
                    TextButton(onClick = vm::loadPlaces) { Text("다시 시도", color = BbolColors.Violet) }
                }
                !s.loading && s.places.isEmpty() -> MapNotice {
                    Text("근처에 장소가 없어요.")
                    if (!s.isBrowse) TextButton(onClick = vm::enterBrowseMode) { Text("종로구 둘러보기", color = BbolColors.Violet) }
                }
                else -> MapNotice {
                    Text("장소 ${s.places.size}곳", style = MaterialTheme.typography.labelLarge)
                    if (s.acceptedIds.isNotEmpty()) {
                        Text("  ·  진행 중 ${s.acceptedIds.size}", style = MaterialTheme.typography.labelLarge, color = BbolColors.Violet)
                    }
                }
            }
        }
    }

    s.arrivalOutcome?.let {
        ArrivalDialog(it, vm::dismissArrivalOutcome, onOpenOnboarding = {
            vm.dismissArrivalOutcome()
            onOpenOnboarding()
        })
    }

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
                        accepted = s.detailAccepted,
                        distanceToPlace = if (s.isBrowse) null else distanceMeters(s.lat, s.lng, s.detail!!.lat, s.detail!!.lng),
                        blockedReason = s.arrivalBlockedReason(hasLocationPermission(context)),
                        arriving = s.arriving,
                        onAccept = { vm.acceptQuest(s.detail!!.placeId) },
                        onCancel = { vm.cancelQuest(s.detail!!.placeId) },
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

private fun pinState(completed: Boolean, accepted: Boolean) = when {
    completed -> PinState.COMPLETED
    accepted -> PinState.ACCEPTED
    else -> PinState.NORMAL
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
private fun ColumnScope.PlaceDetailContent(
    d: PlaceDetail,
    accepted: Boolean,
    distanceToPlace: Long?,
    blockedReason: String?,
    arriving: Boolean,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    onArrive: () -> Unit,
) {
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
    when {
        d.isCompleted -> PrimaryButton("오늘 완료", onClick = {}, enabled = false)
        !accepted -> PrimaryButton("퀘스트 받기", onClick = onAccept)
        else -> {
            Surface(shape = BbolShapes.Card, color = BbolColors.Lavender, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("진행 중인 퀘스트", style = MaterialTheme.typography.titleSmall, color = BbolColors.VioletDeep)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (distanceToPlace != null) "지금 ${Formatters.distance(distanceToPlace)} 떨어져 있어요" else "위치를 확인하면 남은 거리를 알려드려요",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "장소 반경 ${if (d.radius > 0) "${d.radius}m " else ""}안에 들어가서 도착 인증을 눌러 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BbolColors.InkSoft,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                "도착 인증하기",
                onClick = onArrive,
                enabled = blockedReason == null && !arriving,
                loading = arriving,
            )
            TextButton(onClick = onCancel, enabled = !arriving, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("퀘스트 포기하기", color = BbolColors.InkSoft, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (blockedReason != null && !d.isCompleted && accepted) {
        Spacer(Modifier.height(4.dp))
        Text(blockedReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun ArrivalDialog(outcome: ArrivalOutcome, onDismiss: () -> Unit, onOpenOnboarding: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = BbolShapes.Card,
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인", color = BbolColors.Violet) } },
        dismissButton = if (outcome is ArrivalOutcome.Error && outcome.code == "COMPLETION4031") {
            { TextButton(onClick = onOpenOnboarding) { Text("추가 정보 입력하기", color = BbolColors.Violet) } }
        } else null,
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


private data class MarkerData(
    val id: Long,
    val title: String,
    val category: String,
    val lat: Double,
    val lng: Double,
    val hot: Boolean,
    val state: PinState,
)

@Composable
private fun OsmMap(
    lat: Double,
    lng: Double,
    zoom: Double,
    recenterNonce: Int,
    fitToPlaces: Boolean,
    places: List<MarkerData>,
    selectedId: Long?,
    myLocation: Triple<Double, Double, Float?>?,
    onMarker: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            // 베이스 지도를 옅게 해서 핀이 돋보이게 한다. 출처 표기(CopyrightOverlay)는 아래 update에서 유지한다.
            overlayManager.tilesOverlay.setColorFilter(softTileFilter())
            setBackgroundColor(0xFFEFEAFB.toInt())
            minZoomLevel = 11.0
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
    // 둘러보기 모드에서는 장소가 로드되면 장소들이 한눈에 들어오게 맞춘다(너무 가깝거나 멀지 않게 14~15.5).
    val fitKey = if (fitToPlaces) places.size else -1
    LaunchedEffect(lat, lng, zoom, recenterNonce, fitKey) {
        if (fitToPlaces && places.size >= 2) {
            val box = BoundingBox.fromGeoPointsSafe(places.map { GeoPoint(it.lat, it.lng) })
            mapView.post {
                if (mapView.width > 0) {
                    mapView.zoomToBoundingBox(box.increaseByScale(1.25f), false)
                    val z = mapView.zoomLevelDouble.coerceIn(14.0, 15.5)
                    mapView.controller.setZoom(z)
                    mapView.controller.setCenter(box.centerWithDateLine)
                }
            }
        } else {
            mapView.controller.setZoom(zoom)
            mapView.controller.animateTo(GeoPoint(lat, lng))
        }
    }
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.overlays.clear()
            if (myLocation != null) view.overlays.add(MyLocationDot(myLocation.first, myLocation.second, myLocation.third))
            // 선택된 핀이 맨 위에 오도록 마지막에 그린다.
            places.sortedBy { it.id == selectedId }.forEach { p ->
                view.overlays.add(
                    Marker(view).apply {
                        position = GeoPoint(p.lat, p.lng)
                        title = if (p.hot) "${p.title} (HOT)" else p.title
                        icon = PinFactory.get(view.context, p.category, p.state, p.id == selectedId, p.hot)
                        setAnchor(Marker.ANCHOR_CENTER, 0.88f)
                        setOnMarkerClickListener { _, _ ->
                            onMarker(p.id)
                            true
                        }
                    },
                )
            }
            view.overlays.add(CopyrightOverlay(view.context).apply { setAlignBottom(true) })
            view.invalidate()
        },
    )
}
