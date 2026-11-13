package com.bbolbbol.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.bbolbbol.app.AppContainer
import com.bbolbbol.app.ui.onboarding.OnboardingScreen
import com.bbolbbol.app.ui.onboarding.OnboardingViewModel

/** 이 파일이 등록하는 화면의 경로. (AppNav의 Routes는 건드리지 않으려고 따로 둔다.) */
object AuthRoutes {
    const val SIGNUP_FLOW = "signup-flow"
    const val PASSWORD_RESET = "password-reset"
    const val ONBOARDING = "onboarding"
}

@Suppress("UNCHECKED_CAST")
private inline fun <reified VM : ViewModel> vmFactory(crossinline create: () -> VM) =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

/** 가입, 비밀번호 재설정, 온보딩 화면 등록. [goHome]은 백스택을 비우고 홈으로 간다. */
fun NavGraphBuilder.authFlowDestinations(
    nav: NavHostController,
    container: AppContainer,
    goHome: () -> Unit,
    onPasswordResetDone: () -> Unit,
) {
    composable(AuthRoutes.SIGNUP_FLOW) {
        val vm: SignupFlowViewModel = viewModel(
            factory = vmFactory { SignupFlowViewModel(container.authRepository, container.authExtraRepository) },
        )
        SignupFlowScreen(vm, container.devTools, onBack = { nav.popBackStack() })
    }
    composable(AuthRoutes.PASSWORD_RESET) {
        val vm: PasswordResetViewModel = viewModel(
            factory = vmFactory { PasswordResetViewModel(container.authExtraRepository) },
        )
        PasswordResetScreen(vm, container.devTools, onBack = { nav.popBackStack() }, onDone = {
            onPasswordResetDone()
            nav.popBackStack()
        })
    }
    composable(AuthRoutes.ONBOARDING) {
        val vm: OnboardingViewModel = viewModel(
            factory = vmFactory {
                OnboardingViewModel(
                    container.authExtraRepository, container.userRepository,
                    container.placeRepository, container.session,
                )
            },
        )
        // 가입 직후라면 백스택에 이전 화면이 없다 -> 홈으로. 마이/도착 인증 안내에서 왔다면 돌아간다.
        val leave = { if (nav.previousBackStackEntry != null) nav.popBackStack() else goHome() }
        OnboardingScreen(vm, onDone = {
            container.session.markOnboarding(false)
            leave()
        }, onSkip = {
            container.session.markOnboarding(false)
            leave()
        })
    }
}
