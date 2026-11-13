package com.bbolbbol.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bbolbbol.app.AppContainer
import com.bbolbbol.app.ui.login.LoginScreen
import com.bbolbbol.app.ui.login.LoginViewModel
import com.bbolbbol.app.ui.map.MapScreen
import com.bbolbbol.app.ui.map.MapViewModel
import com.bbolbbol.app.ui.me.MeScreen
import com.bbolbbol.app.ui.me.MeViewModel
import com.bbolbbol.app.ui.signup.SignupScreen
import com.bbolbbol.app.ui.signup.SignupViewModel

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val MAP = "map"
    const val ME = "me"
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
        composable(Routes.MAP) {
            val vm: MapViewModel = viewModel(factory = factory { MapViewModel(container.placeRepository) })
            MapScreen(vm, onOpenMe = { nav.navigate(Routes.ME) })
        }
        composable(Routes.ME) {
            val vm: MeViewModel = viewModel(
                factory = factory { MeViewModel(container.userRepository, container.authRepository) },
            )
            MeScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}

private fun NavHostController.resetTo(route: String) {
    navigate(route) { popUpTo(0) { inclusive = true } }
}
