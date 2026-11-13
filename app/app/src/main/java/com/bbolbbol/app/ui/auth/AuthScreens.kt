package com.bbolbbol.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.ui.theme.BbolShapes
import com.bbolbbol.app.ui.common.PrimaryButton
import com.bbolbbol.app.ui.theme.BbolColors
import kotlinx.coroutines.delay

@Composable
private fun FlowPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BbolColors.Background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = BbolColors.Ink)
        content()
        TextButton(onClick = onBack) { Text("돌아가기", color = BbolColors.Violet) }
    }
}

/** 이메일 입력 + 코드 입력 두 단계. 가입과 비밀번호 재설정이 같이 쓴다. */
@Composable
private fun EmailCodeSteps(c: CodeStepState, controller: EmailCodeController, sendLabel: String, devHint: String?) {
    // 1초마다 쿨다운/유효 시간을 줄인다.
    LaunchedEffect(c.sent, c.verified) {
        while (c.sent && !c.verified) {
            delay(1000)
            controller.tick()
        }
    }
    OutlinedTextField(
        value = c.email,
        onValueChange = controller::onEmail,
        label = { Text("이메일") },
        singleLine = true,
        enabled = !c.sent,
        shape = MaterialTheme.shapes.medium,
        isError = c.emailError != null,
        supportingText = { c.emailError?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    if (!c.sent) {
        if (devHint != null) {
            Text(
                devHint,
                style = MaterialTheme.typography.bodySmall,
                color = BbolColors.InkSoft,
            )
        }
        c.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        PrimaryButton(sendLabel, onClick = controller::send, enabled = c.canSend, loading = c.sending)
        return
    }
    Text(
        if (c.expiresIn > 0) "인증 코드를 보냈어요. 남은 시간 ${mmss(c.expiresIn)}" else "인증 코드가 만료되었어요. 다시 받아 주세요.",
        style = MaterialTheme.typography.bodyMedium,
        color = BbolColors.InkSoft,
    )
    if (devHint != null) {
        Text(
            devHint,
            style = MaterialTheme.typography.bodySmall,
            color = BbolColors.InkSoft,
        )
    }
    OutlinedTextField(
        value = c.code,
        onValueChange = controller::onCode,
        label = { Text("인증 코드 6자리") },
        singleLine = true,
        enabled = !c.codeInvalid,
        shape = MaterialTheme.shapes.medium,
        isError = c.error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    c.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    PrimaryButton("인증하기", onClick = controller::confirm, enabled = c.canConfirm, loading = c.confirming)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = controller::changeEmail) { Text("이메일 바꾸기", color = BbolColors.Violet) }
        TextButton(onClick = controller::send, enabled = c.canResend) {
            Text(if (c.resendIn > 0) "다시 보내기 (${c.resendIn}초)" else "다시 보내기", color = if (c.canResend) BbolColors.Violet else BbolColors.InkSoft)
        }
    }
}

@Composable
fun SignupFlowScreen(vm: SignupFlowViewModel, devTools: DevTools, onBack: () -> Unit) {
    val s by vm.state.collectAsState()
    FlowPage("회원가입", onBack) {
        when (s.step) {
            SignupStep.EMAIL, SignupStep.CODE -> {
                EmailCodeSteps(s.code, vm.codeFlow, "인증 코드 받기", devTools.codeHint)
                if (s.step == SignupStep.EMAIL) devTools.SignupShortcut(s.email, vm::applyVerificationToken)
            }
            SignupStep.PROFILE -> {
                val p = s.profile
                Text("${s.email} 인증이 완료되었어요.", color = BbolColors.Success, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = p.password,
                    onValueChange = vm::onPassword,
                    label = { Text("비밀번호") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    isError = p.passwordError != null,
                    supportingText = { Text(p.passwordError ?: "8~20자, 영문 + 숫자 + 특수문자") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                var nickTouched by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = p.nickname,
                    onValueChange = vm::onNickname,
                    label = { Text("닉네임") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    isError = p.nicknameError != null,
                    supportingText = {
                        Text(
                            when {
                                p.nicknameError != null -> p.nicknameError
                                p.nicknameOk -> "사용할 수 있는 닉네임이에요."
                                else -> "2~10자, 한글/영문/숫자"
                            },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) nickTouched = true else if (nickTouched) vm.checkNickname()
                        },
                )
                p.formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(4.dp))
                PrimaryButton("가입하기", onClick = vm::submit, enabled = !p.loading, loading = p.loading)
                Text(
                    "가입 후 추가 정보(연령대, 성별, 관심 카테고리)를 입력하면 도착 인증을 할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BbolColors.InkSoft,
                )
            }
        }
    }
}

@Composable
fun PasswordResetScreen(vm: PasswordResetViewModel, devTools: DevTools, onBack: () -> Unit, onDone: () -> Unit) {
    val s by vm.state.collectAsState()
    LaunchedEffect(s.reset.done) { if (s.reset.done) onDone() }
    FlowPage("비밀번호 재설정", onBack) {
        when (s.step) {
            ResetStep.EMAIL, ResetStep.CODE -> EmailCodeSteps(s.code, vm.codeFlow, "인증 코드 받기", devTools.codeHint)
            ResetStep.PASSWORD -> {
                val r = s.reset
                Text("${s.code.email} 인증이 완료되었어요. 새 비밀번호를 입력해 주세요.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = r.newPassword,
                    onValueChange = vm::onPassword,
                    label = { Text("새 비밀번호") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    isError = r.passwordError != null,
                    supportingText = { Text(r.passwordError ?: "8~20자, 영문 + 숫자 + 특수문자") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                r.formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                PrimaryButton("비밀번호 변경", onClick = vm::submit, enabled = !r.loading, loading = r.loading)
            }
        }
    }
}

/** 로그인 화면 아래에 끼우는 부분: 비밀번호 찾기, 카카오 로그인, 재설정 성공 안내. */
@Composable
fun LoginExtras(
    vm: KakaoLoginViewModel,
    devTools: DevTools,
    resetSucceeded: Boolean,
    session: com.bbolbbol.app.data.local.AuthSession,
    onForgotPassword: () -> Unit,
) {
    val s by vm.state.collectAsState()
    val expired by session.sessionExpired.collectAsState()
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (expired) {
            Text("로그인 시간이 만료되어 다시 로그인해 주세요.", color = BbolColors.InkSoft, style = MaterialTheme.typography.bodySmall)
        }
        if (resetSucceeded) {
            Text("비밀번호를 변경했어요. 새 비밀번호로 로그인해 주세요.", color = BbolColors.Success, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onForgotPassword) { Text("비밀번호를 잊으셨나요?", color = BbolColors.InkSoft) }
        OutlinedButton(
            onClick = vm::onButton,
            enabled = vm.enabled && !s.loading,
            shape = BbolShapes.Pill,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (vm.enabled) "카카오로 시작하기" else "카카오로 시작하기 (준비 중)")
        }
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (s.showDevDialog) devTools.KakaoDialog(onDismiss = vm::dismissDialog, onSubmit = vm::login)
}

/** 마이 화면의 계정 영역: 온보딩 안내, 로그아웃, 회원 탈퇴(확인 다이얼로그). */
@Composable
fun AccountSection(vm: AccountViewModel, onOpenOnboarding: () -> Unit) {
    val s by vm.state.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    // 온보딩 화면에서 돌아오면 다시 읽는다.
    LaunchedEffect(Unit) { vm.refreshOnboarding() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onOpenOnboarding, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Text(if (s.onboardingCompleted == false) "추가 정보 입력하기 (도착 인증에 필요해요)" else "추가 정보 수정")
        }
        OutlinedButton(onClick = { showLogoutDialog = true }, enabled = !s.busy, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Text("로그아웃")
        }
        TextButton(onClick = vm::askWithdraw, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) {
            Text("회원 탈퇴", color = MaterialTheme.colorScheme.error)
        }
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = Color.White,
            shape = BbolShapes.Card,
            title = { Text("로그아웃할까요?") },
            confirmButton = { TextButton(onClick = { showLogoutDialog = false; vm.logout() }) { Text("로그아웃") } },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("취소") } },
        )
    }
    if (s.showWithdrawDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissWithdraw,
            containerColor = Color.White,
            shape = BbolShapes.Card,
            title = { Text("정말 탈퇴할까요?") },
            text = {
                Text(
                    "이메일, 닉네임, 연령대 같은 개인정보가 바로 삭제되고 모든 기기에서 로그아웃돼요. " +
                        "도착 인증 기록과 포인트는 통계용으로만 남고 더 이상 내 계정과 연결되지 않아요. " +
                        "되돌릴 수 없어요.",
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmWithdraw) { Text("탈퇴하기", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = vm::dismissWithdraw) { Text("취소") } },
        )
    }
}

private fun mmss(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)
