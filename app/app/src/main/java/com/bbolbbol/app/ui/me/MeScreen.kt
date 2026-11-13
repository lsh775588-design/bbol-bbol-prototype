package com.bbolbbol.app.ui.me

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MeScreen(vm: MeViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text("내 정보", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text("지도") }
        }
        Spacer(Modifier.height(16.dp))
        when {
            s.loading -> CircularProgressIndicator()
            s.me == null -> {
                Text(s.error ?: "불러오지 못했어요.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = vm::load) { Text("다시 시도") }
            }
            else -> {
                val me = s.me!!
                InfoRow("이메일", me.email)
                InfoRow("가입 방식", me.loginType)
                InfoRow("가입일", me.createdAt?.take(10) ?: "-")
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = s.nicknameInput,
                    onValueChange = vm::onNickname,
                    label = { Text("닉네임") },
                    singleLine = true,
                    isError = s.nicknameMessage != null && s.nicknameIsError,
                    supportingText = {
                        Text(s.nicknameMessage ?: "닉네임은 변경 후 30일이 지나야 다시 바꿀 수 있어요.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = vm::saveNickname,
                    enabled = !s.saving && s.nicknameInput.trim() != me.nickname,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("닉네임 변경") }
            }
        }
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Text("로그아웃") }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Text(value)
    }
}
