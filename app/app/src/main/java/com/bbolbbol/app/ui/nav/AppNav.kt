package com.bbolbbol.app.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bbolbbol.app.AppContainer
import com.bbolbbol.app.ui.history.ActivityLogScreen
import com.bbolbbol.app.ui.history.ActivityLogViewModel
import com.bbolbbol.app.ui.history.PointHistoryScreen
import com.bbolbbol.app.ui.history.PointHistoryViewModel
import com.bbolbbol.app.ui.home.HomeScreen
import com.bbolbbol.app.ui.home.HomeViewModel
import com.bbolbbol.app.ui.login.LoginScreen
import com.bbolbbol.app.ui.login.LoginViewModel
import com.bbolbbol.app.ui.map.JONGNO_LAT
import com.bbolbbol.app.ui.map.JONGNO_LNG
import com.bbolbbol.app.ui.map.MapScreen
import com.bbolbbol.app.ui.map.MapViewModel
import com.bbolbbol.app.ui.me.MeScreen
import com.bbolbbol.app.ui.me.MeViewModel
import com.bbolbbol.app.ui.me.RecentActivityViewModel
import com.bbolbbol.app.ui.ranking.RankingScreen
import com.bbolbbol.app.ui.ranking.RankingViewModel
import com.bbolbbol.app.ui.stats.StatsScreen
import com.bbolbbol.app.ui.stats.StatsViewModel
import com.bbolbbol.app.ui.recommend.RecommendScreen
import com.bbolbbol.app.ui.recommend.RecommendViewModel
import com.bbolbbol.app.ui.theme.BbolColors
import com.bbolbbol.app.ui.auth.AccountSection
import com.bbolbbol.app.ui.auth.AccountViewModel
import com.bbolbbol.app.ui.auth.AuthRoutes
import com.bbolbbol.app.ui.auth.KakaoLoginViewModel
import com.bbolbbol.app.ui.auth.LoginExtras
import com.bbolbbol.app.ui.auth.authFlowDestinations
import com.bbolbbol.app.ui.auth.checkOnboardingOnStart

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val MAP = "map"
    const val RANKING = "ranking"
    const val ME = "me"
    const val POINT_HISTORY = "point-history"
    const val ACTIVITY_LOG = "activity-log"
    const val STATS = "stats"
    const val RECOMMEND = "recommend/{lat}/{lng}"

    fun recommend(lat: Double, lng: Double) = "recommend/$lat/$lng"

    /** 하단 탭이 보이는 메인 화면들. */
    val TABS = listOf(HOME, MAP, RANKING, ME)
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TAB_ITEMS = listOf(
    Tab(Routes.HOME, "홈", Icons.Filled.Home),
    Tab(Routes.MAP, "지도", Icons.Filled.Map),
    Tab(Routes.RANKING, "랭킹", Icons.Filled.Leaderboard),
    Tab(Routes.ME, "마이", Icons.Filled.Person),
)

@Suppress("UNCHECKED_CAST")
private inline fun <reified VM : ViewModel> factory(crossinline create: () -> VM) =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val loggedIn by container.session.loggedIn.collectAsState()
    // 시작 화면은 처음 한 번만 정한다. loggedIn이 바뀔 때마다 바꾸면 NavHost가 그래프를 다시 만들어 백스택이 초기화된다.
    val startDestination = remember { if (container.session.loggedIn.value) Routes.HOME else Routes.LOGIN }
    val backEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route

    // 홈/추천에서 고른 장소. 지도 탭이 받아서 상세 시트를 열고 비운다.
    var pendingPlaceId by remember { mutableStateOf<Long?>(null) }
    // 비밀번호 재설정 성공 안내(로그인 화면에 표시)와 가입/로그인 직후 온보딩 필요 여부.
    var resetSucceeded by remember { mutableStateOf(false) }

    // 로그인 상태가 바뀌면(로그인 성공, 로그아웃, AUTH401로 토큰 삭제) 백스택을 비우고 이동한다.
    LaunchedEffect(loggedIn) {
        if (loggedIn) resetSucceeded = false
        // collectAsState로 읽은 값은 markOnboarding 직후 아직 갱신 전일 수 있다. 흐름의 현재 값을 직접 읽는다.
        loginStateTarget(loggedIn, nav.currentDestination?.route, container.session)?.let { nav.resetTo(it) }
    }

    // 앱 시작 시 저장된 토큰이 있으면 온보딩 완료 여부를 한 번 확인한다(실패해도 진입을 막지 않는다).
    LaunchedEffect(Unit) {
        if (checkOnboardingOnStart(container.userRepository, container.session)) nav.navigate(AuthRoutes.ONBOARDING)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = BbolColors.Background,
        bottomBar = {
            if (currentRoute in Routes.TABS) {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    TAB_ITEMS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BbolColors.Violet,
                                selectedTextColor = BbolColors.Violet,
                                indicatorColor = BbolColors.Lavender,
                                unselectedIconColor = BbolColors.InkSoft,
                                unselectedTextColor = BbolColors.InkSoft,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // 아래쪽 패딩만 쓴다. 위쪽(상태바)은 각 화면이 직접 처리한다.
        NavHost(
            navController = nav,
            startDestination = startDestination,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Routes.LOGIN) {
                val vm: LoginViewModel = viewModel(factory = factory { LoginViewModel(container.authRepository) })
                val kakaoVm: KakaoLoginViewModel = viewModel(
                    factory = factory { KakaoLoginViewModel(container.authExtraRepository, container.kakaoTokenProvider) },
                )
                LoginScreen(
                    vm,
                    onSignup = { nav.navigate(AuthRoutes.SIGNUP_FLOW) },
                    extras = {
                        LoginExtras(kakaoVm, container.devTools, resetSucceeded, container.session, onForgotPassword = {
                            resetSucceeded = false
                            nav.navigate(AuthRoutes.PASSWORD_RESET)
                        })
                    },
                )
            }
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel(
                    factory = factory {
                        HomeViewModel(
                            container.userRepository,
                            container.pointRepository,
                            container.placeRepository,
                            container.completionRepository,
                            container.questBook,
                            container::currentUserId,
                        )
                    },
                )
                HomeScreen(
                    vm,
                    onOpenPlace = { id ->
                        pendingPlaceId = id
                        nav.switchTab(Routes.MAP)
                    },
                    onOpenRecommend = { lat, lng -> nav.navigate(Routes.recommend(lat, lng)) },
                    onGoMap = { nav.switchTab(Routes.MAP) },
                    onOpenActivityLog = { nav.navigate(Routes.ACTIVITY_LOG) },
                )
            }
            composable(Routes.MAP) {
                val vm: MapViewModel = viewModel(
                    factory = factory { MapViewModel(container.placeRepository, container.completionRepository, container.questBook, container::currentUserId) },
                )
                MapScreen(
                    vm,
                    onOpenRecommend = { lat, lng -> nav.navigate(Routes.recommend(lat, lng)) },
                    pickedPlaceId = pendingPlaceId,
                    onPickedConsumed = { pendingPlaceId = null },
                    onOpenOnboarding = { nav.navigate(AuthRoutes.ONBOARDING) },
                )
            }
            composable(Routes.RANKING) {
                val vm: RankingViewModel = viewModel(factory = factory { RankingViewModel(container.rankingRepository) })
                RankingScreen(vm)
            }
            composable(Routes.STATS) {
                val vm: StatsViewModel = viewModel(factory = factory { StatsViewModel(container.statsRepository) })
                StatsScreen(vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.ME) {
                val vm: MeViewModel = viewModel(
                    factory = factory {
                        MeViewModel(container.userRepository, container.authRepository, container.pointRepository)
                    },
                )
                val recentVm: RecentActivityViewModel = viewModel(
                    factory = factory { RecentActivityViewModel(container.completionRepository) },
                )
                MeScreen(
                    vm,
                    recentVm,
                    onPointHistory = { nav.navigate(Routes.POINT_HISTORY) },
                    onActivityLog = { nav.navigate(Routes.ACTIVITY_LOG) },
                    onStats = { nav.navigate(Routes.STATS) },
                    accountSection = {
                        val accountVm: AccountViewModel = viewModel(
                            factory = factory { AccountViewModel(container.authExtraRepository, container.userRepository) },
                        )
                        AccountSection(accountVm, onOpenOnboarding = { nav.navigate(AuthRoutes.ONBOARDING) })
                    },
                )
            }
            authFlowDestinations(
                nav, container,
                goHome = { nav.resetTo(Routes.HOME) },
                onPasswordResetDone = { resetSucceeded = true },
            )
            composable(Routes.POINT_HISTORY) {
                val vm: PointHistoryViewModel = viewModel(
                    factory = factory { PointHistoryViewModel(container.pointRepository) },
                )
                PointHistoryScreen(vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.ACTIVITY_LOG) {
                val vm: ActivityLogViewModel = viewModel(
                    factory = factory { ActivityLogViewModel(container.completionRepository) },
                )
                ActivityLogScreen(vm, onBack = { nav.popBackStack() })
            }
            composable(
                Routes.RECOMMEND,
                arguments = listOf(
                    navArgument("lat") { type = NavType.StringType },
                    navArgument("lng") { type = NavType.StringType },
                ),
            ) { entry ->
                val lat = entry.arguments?.getString("lat")?.toDoubleOrNull() ?: JONGNO_LAT
                val lng = entry.arguments?.getString("lng")?.toDoubleOrNull() ?: JONGNO_LNG
                val vm: RecommendViewModel = viewModel(
                    factory = factory { RecommendViewModel(container.placeRepository, lat, lng) },
                )
                RecommendScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onPick = { id ->
                        pendingPlaceId = id
                        nav.popBackStack()
                        nav.switchTab(Routes.MAP)
                    },
                )
            }
        }
    }
}

/** 로그인 상태와 현재 화면으로 정한 이동 대상. 이동이 필요 없으면 null. */
internal fun loginStateTarget(
    loggedIn: Boolean,
    route: String?,
    session: com.bbolbbol.app.data.local.AuthSession,
): String? {
    val preLogin = route == Routes.LOGIN || route == AuthRoutes.SIGNUP_FLOW || route == AuthRoutes.PASSWORD_RESET
    return when {
        loggedIn && preLogin -> postLoginRoute(session)
        !loggedIn && route != null && !preLogin -> Routes.LOGIN
        else -> null
    }
}

/** 로그인/가입 직후 갈 곳. 상태 흐름의 현재 값을 직접 읽는다. */
internal fun postLoginRoute(session: com.bbolbbol.app.data.local.AuthSession): String =
    if (session.onboardingRequired.value) AuthRoutes.ONBOARDING else Routes.HOME

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.resetTo(route: String) {
    navigate(route) { popUpTo(0) { inclusive = true } }
}
