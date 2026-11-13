package com.bbolbbol.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.ui.common.BbolCard
import com.bbolbbol.app.ui.common.PawIcon
import com.bbolbbol.app.ui.common.PrimaryButton
import com.bbolbbol.app.ui.theme.BbolColors

@Composable
fun LoginScreen(vm: LoginViewModel, onSignup: () -> Unit) {
    val s by vm.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BbolColors.Background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(BbolColors.Lavender), contentAlignment = Alignment.Center) {
            Icon(PawIcon, null, tint = BbolColors.PawPink, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("뽈뽈", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Text("종로구 장소를 돌아다니며 포인트를 모아요", style = MaterialTheme.typography.bodyMedium, color = BbolColors.InkSoft)
        Spacer(Modifier.height(28.dp))
        BbolCard {
            Column(Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = s.email,
                    onValueChange = vm::onEmail,
                    label = { Text("이메일") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = s.password,
                    onValueChange = vm::onPassword,
                    label = { Text("비밀번호") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (s.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(s.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(20.dp))
                PrimaryButton("로그인", onClick = vm::submit, enabled = s.canSubmit, loading = s.loading)
            }
        }
        TextButton(onClick = onSignup) { Text("회원가입", color = BbolColors.Violet) }
    }
}
