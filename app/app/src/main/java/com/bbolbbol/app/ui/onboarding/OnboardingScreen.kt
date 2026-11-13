package com.bbolbbol.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.data.api.AgeGroups
import com.bbolbbol.app.data.api.Genders
import com.bbolbbol.app.ui.common.BbolCard
import com.bbolbbol.app.ui.common.ErrorState
import com.bbolbbol.app.ui.common.LoadingState
import com.bbolbbol.app.ui.common.PillChip
import com.bbolbbol.app.ui.common.PrimaryButton
import com.bbolbbol.app.ui.theme.BbolColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(vm: OnboardingViewModel, onDone: () -> Unit, onSkip: () -> Unit) {
    val s by vm.state.collectAsState()
    LaunchedEffect(s.done) { if (s.done) onDone() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BbolColors.Background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("추가 정보", style = MaterialTheme.typography.headlineMedium, color = BbolColors.Ink)
        Text(
            "도착 인증으로 포인트를 받으려면 이 정보를 먼저 입력해야 해요.",
            style = MaterialTheme.typography.bodyMedium,
            color = BbolColors.InkSoft,
        )
        when {
            s.loading -> LoadingState()
            s.loadError != null -> ErrorState(s.loadError!!, vm::load)
            else -> {
                if (s.showNickname) {
                    OutlinedTextField(
                        value = s.nickname,
                        onValueChange = vm::onNickname,
                        label = { Text("닉네임") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        isError = s.nicknameError != null,
                        supportingText = { Text(s.nicknameError ?: "2~10자, 한글/영문/숫자") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                BbolCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("연령대", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AgeGroups.all.forEach { (code, label) ->
                                PillChip(label, s.ageGroup == code, { vm.onAgeGroup(code) })
                            }
                        }
                        s.ageGroupError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(4.dp))
                        Text("성별", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Genders.all.forEach { (code, label) ->
                                PillChip(label, s.gender == code, { vm.onGender(code) })
                            }
                        }
                        s.genderError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                BbolCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("관심 카테고리 (최대 ${MAX_INTERESTS}개, 선택)", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.categories.forEach { c ->
                                PillChip(c.name, c.code in s.interests, { vm.toggleInterest(c.code) })
                            }
                        }
                        Text(
                            s.interestsError ?: "${s.interests.size}/$MAX_INTERESTS 선택",
                            color = if (s.interestsError != null) MaterialTheme.colorScheme.error else BbolColors.InkSoft,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                s.formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                PrimaryButton("저장하기", onClick = vm::submit, enabled = !s.saving, loading = s.saving)
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text("나중에 하기 (도착 인증 전에 꼭 필요해요)", color = BbolColors.InkSoft)
                }
            }
        }
    }
}
