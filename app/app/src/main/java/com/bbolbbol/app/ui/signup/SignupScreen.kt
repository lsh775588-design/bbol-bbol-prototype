package com.bbolbbol.app.ui.signup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.BuildConfig

@Composable
fun SignupScreen(vm: SignupViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsState()
    // 포커스를 한 번 받았다가 잃을 때만 중복 확인을 호출한다.
    var emailTouched by remember { mutableStateOf(false) }
    var nickTouched by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("회원가입", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = s.email,
            onValueChange = vm::onEmail,
            label = { Text("이메일") },
            singleLine = true,
            isError = s.emailError != null,
            supportingText = {
                when {
                    s.emailError != null -> Text(s.emailError!!)
                    s.emailOk -> Text("사용할 수 있는 이메일이에요.")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    if (it.isFocused) emailTouched = true
                    else if (emailTouched) vm.checkEmail()
                },
        )
        OutlinedTextField(
            value = s.password,
            onValueChange = vm::onPassword,
            label = { Text("비밀번호") },
            singleLine = true,
            isError = s.passwordError != null,
            supportingText = { Text(s.passwordError ?: "8~20자, 영문 + 숫자 + 특수문자") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.nickname,
            onValueChange = vm::onNickname,
            label = { Text("닉네임") },
            singleLine = true,
            isError = s.nicknameError != null,
            supportingText = {
                Text(
                    when {
                        s.nicknameError != null -> s.nicknameError!!
                        s.nicknameOk -> "사용할 수 있는 닉네임이에요."
                        else -> "2~10자, 한글/영문/숫자"
                    },
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    if (it.isFocused) nickTouched = true
                    else if (nickTouched) vm.checkNickname()
                },
        )

        if (BuildConfig.DEBUG) {
            OutlinedTextField(
                value = s.devToken,
                onValueChange = vm::onDevToken,
                label = { Text("개발용 인증 토큰") },
                singleLine = true,
                isError = s.tokenError != null,
                supportingText = {
                    Text(s.tokenError ?: "개발 서버 전용. 고정 토큰만 입력하면 이메일을 자동으로 붙여요.")
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (s.tokenError != null) {
            Text(s.tokenError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (s.formError != null) {
            Text(
                s.formError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = vm::submit, enabled = !s.loading, modifier = Modifier.fillMaxWidth()) {
            if (s.loading) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp) else Text("가입하기")
        }
        TextButton(onClick = onBack) { Text("이미 계정이 있어요") }
    }
}
