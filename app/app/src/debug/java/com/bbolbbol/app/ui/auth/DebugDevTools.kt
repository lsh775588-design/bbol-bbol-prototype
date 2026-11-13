package com.bbolbbol.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.ui.theme.BbolColors
import com.bbolbbol.app.ui.theme.BbolShapes

/** debug 변형 전용(src/debug). release에는 이 파일이 컴파일되지 않는다. */
fun createDevTools(): DevTools = DebugDevTools()

/** 서버 FakeKakaoClient 형식(`DEV-KAKAO|<id>|<email>|<nick>`)의 문자열을 직접 입력받는다. */
class DevKakaoTokenProvider : KakaoTokenProvider {
    override val mode = KakaoTokenProvider.Mode.DEV_MANUAL
    override suspend fun getToken(manualInput: String?): String? = manualInput?.trim()?.takeIf { it.isNotEmpty() }
}

/** 개발 서버의 고정 토큰 형식은 "{고정토큰}:{이메일}". 입력에 콜론이 없으면 이메일을 붙인다. */
fun buildDevVerificationToken(devToken: String, email: String): String {
    val t = devToken.trim()
    return if (t.contains(':')) t else "$t:${email.trim()}"
}

class DebugDevTools : DevTools {
    override val kakaoProvider: KakaoTokenProvider = DevKakaoTokenProvider()
    override val codeHint: String? = "개발 서버에서는 인증 코드가 서버 로그에 찍혀요"

    @Composable
    override fun SignupShortcut(email: String, onToken: (String) -> Unit) {
        var dev by remember { mutableStateOf("") }
        OutlinedTextField(
            value = dev,
            onValueChange = { dev = it },
            label = { Text("개발용 인증 토큰") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            supportingText = { Text("개발 서버 전용 지름길. 고정 토큰을 넣고 아래를 누르면 코드 단계를 건너뛰어요.") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { if (dev.isNotBlank()) onToken(buildDevVerificationToken(dev, email)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("개발용 토큰으로 건너뛰기") }
    }

    @Composable
    override fun KakaoDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
        var token by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Color.White,
            shape = BbolShapes.Card,
            title = { Text("개발용 카카오 로그인") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("개발 서버 전용입니다. 형식: DEV-KAKAO|아이디|이메일(비워도 됨)|닉네임", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = token, onValueChange = { token = it }, singleLine = true, label = { Text("카카오 토큰") })
                }
            },
            confirmButton = { TextButton(onClick = { onSubmit(token) }, enabled = token.isNotBlank()) { Text("로그인", color = BbolColors.Violet) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        )
    }
}
