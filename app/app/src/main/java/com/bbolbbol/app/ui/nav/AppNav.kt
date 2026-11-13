package com.bbolbbol.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bbolbbol.app.AppContainer
import com.bbolbbol.app.ui.history.ActivityLogScreen
import com.bbolbbol.app.ui.history.ActivityLogViewModel
import com.bbolbbol.app.ui.history.PointHistoryScreen
import com.bbolbbol.app.ui.history.PointHistoryViewModel
import com.bbolbbol.app.ui.login.LoginScreen
import com.bbolbbol.app.ui.login.LoginViewModel
import com.bbolbbol.app.ui.map.JONGNO_LAT
import com.bbolbbol.app.ui.map.JONGNO_LNG
import com.bbolbbol.app.ui.map.MapScreen
import com.bbolbbol.app.ui.map.MapViewModel
import com.bbolbbol.app.ui.me.MeScreen
import com.bbolbbol.app.ui.me.MeViewModel
import com.bbolbbol.app.ui.recommend.RecommendScreen
import com.bbolbbol.app.ui.recommend.RecommendViewModel
import com.bbolbbol.app.ui.signup.SignupScreen
import com.bbolbbol.app.ui.signup.SignupViewModel

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val MAP = "map"
    const val ME = "me"
    const val POINT_HISTORY = "point-history"
    const val ACTIVITY_LOG = "activity-log"
    const val RECOMMEND = "recommend/{lat}/{lng}"

    /** 추천 화면에서 지도 화면으로 돌려주는 장소 id(SavedStateHandle 키). */
    const val PICKED_PLACE = "pickedPlaceId"

    fun recommend(lat: Double, lng: Double) = "recommend/$lat/$lng"
}

@Suppress("UNCHECKED_CAST")
private inline fun <reified VM : ViewModel> factory(crossinline create: () -> VM) =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val loggedIn by container.session.loggedIn.collectAsState()

    // 로그인 상태가 바뀌면(로그인 성공, 로그아웃, AUTH401로 토큰 삭제) 백스택을 비우고 이동한다.
    LaunchedEffect(loggedIn) {
        val route = nav.currentDestination?.route
        if (loggedIn && (route == Routes.LOGIN || route == Routes.SIGNUP)) nav.resetTo(Routes.MAP)
        if (!loggedIn && route != null && route != Routes.LOGIN && route != Routes.SIGNUP) nav.resetTo(Routes.LOGIN)
    }

    NavHost(navController = nav, startDestination = if (loggedIn) Routes.MAP else Routes.LOGIN) {
        composable(Routes.LOGIN) {
            val vm: LoginViewModel = viewModel(factory = factory { LoginViewModel(container.authRepository) })
            LoginScreen(vm, onSignup = { nav.navigate(Routes.SIGNUP) })
        }
        composable(Routes.SIGNUP) {
            val vm: SignupViewModel = viewModel(factory = factory { SignupViewModel(container.authRepository) })
            SignupScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.MAP) { entry ->
            val vm: MapViewModel = viewModel(
                factory = factory { MapViewModel(container.placeRepository, container.completionRepository) },
            )
            val picked by entry.savedStateHandle.getStateFlow<Long?>(Routes.PICKED_PLACE, null).collectAsState()
            MapScreen(
                vm,
                onOpenMe = { nav.navigate(Routes.ME) },
                onOpenRecommend = { lat, lng -> nav.navigate(Routes.recommend(lat, lng)) },
                pickedPlaceId = picked,
                onPickedConsumed = { entry.savedStateHandle[Routes.PICKED_PLACE] = null },
            )
        }
        composable(Routes.ME) {
            val vm: MeViewModel = viewModel(
                factory = factory {
                    MeViewModel(container.userRepository, container.authRepository, container.pointRepository)
                },
            )
            MeScreen(
                vm,
                onBack = { nav.popBackStack() },
                onPointHistory = { nav.navigate(Routes.POINT_HISTORY) },
                onActivityLog = { nav.navigate(Routes.ACTIVITY_LOG) },
            )
        }
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
                    nav.previousBackStackEntry?.savedStateHandle?.set(Routes.PICKED_PLACE, id)
                    nav.popBackStack()
                },
            )
        }
    }
}

private fun NavHostController.resetTo(route: String) {
    navigate(route) { popUpTo(0) { inclusive = true } }
}
