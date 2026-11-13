package com.bbolbbol.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbolbbol.common.error.BusinessException;
import com.bbolbbol.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NicknameRulesTest {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), KST); // 2026-11-10T12:00 KST
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0, 0);

    @ParameterizedTest
    @ValueSource(strings = {"뽈뽈", "뽈뽈이", "abc", "Abc123", "가나다라마바사아자차", "ab"})
    @DisplayName("형식 통과: 2~10자 한글/영문/숫자")
    void validFormat(String nickname) {
        assertThat(NicknamePolicy.isValidFormat(nickname)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "가", "가나다라마바사아자차카", "뽈 뽈", "뽈뽈!", "ㅋㅋㅋ", "뽈뽈😀", "abc_d", " 뽈뽈"})
    @DisplayName("형식 위반: 길이, 공백, 특수문자, 자모, 이모지")
    void invalidFormat(String nickname) {
        assertThat(NicknamePolicy.isValidFormat(nickname)).isFalse();
    }

    @Test
    @DisplayName("null 닉네임은 형식 위반")
    void nullInvalid() {
        assertThat(NicknamePolicy.isValidFormat(null)).isFalse();
    }

    @Test
    @DisplayName("금칙어: 포함 여부, 대소문자, 공백/특수문자 우회 모두 걸린다")
    void forbiddenWords() {
        ForbiddenWordFilter f = new ForbiddenWordFilter(List.of("금지", "Bad Word", "# 아님", ""));
        assertThat(f.containsForbiddenWord("금지어")).isTrue();
        assertThat(f.containsForbiddenWord("착한금지")).isTrue();
        assertThat(f.containsForbiddenWord("BADWORD")).isTrue();
        assertThat(f.containsForbiddenWord("b a d-w.o_r d")).isTrue();
        assertThat(f.containsForbiddenWord("금 지")).isTrue();
        assertThat(f.containsForbiddenWord("금.지")).isTrue();
        assertThat(f.containsForbiddenWord("깨끗한이름")).isFalse();
        assertThat(f.containsForbiddenWord(null)).isFalse();
    }

    @Test
    @DisplayName("리소스 파일 금칙어 목록을 읽는다(주석과 빈 줄 제외)")
    void loadsResourceFile() {
        ForbiddenWordFilter f = new ForbiddenWordFilter(
                new org.springframework.core.io.ClassPathResource("forbidden-words.txt"));
        assertThat(f.containsForbiddenWord("관리자")).isTrue();
        assertThat(f.containsForbiddenWord("Admin")).isTrue();
        assertThat(f.containsForbiddenWord("PLACEHOLDER")).isFalse();
        assertThat(f.containsForbiddenWord("뽈뽈이")).isFalse();
    }

    @Test
    @DisplayName("30일 규칙: 한 번도 안 바꿨으면 가능, 29일 23시간은 불가, 정확히 30일이면 가능")
    void thirtyDayRule() {
        User fresh = User.createEmailUser("a@a.com", "h", "처음닉", NOW.minusDays(100));
        assertThat(fresh.nextNicknameChangeableAt()).isNull();
        assertThat(fresh.canChangeNickname(NOW)).isTrue();

        User u = User.createEmailUser("a@a.com", "h", "처음닉", NOW.minusDays(100));
        u.changeNickname("바뀐닉", NOW.minusDays(30).plusHours(1));
        assertThat(u.canChangeNickname(NOW)).isFalse();
        assertThat(u.nextNicknameChangeableAt()).isEqualTo(NOW.plusHours(1));

        User exact = User.createEmailUser("a@a.com", "h", "처음닉", NOW.minusDays(100));
        exact.changeNickname("바뀐닉", NOW.minusDays(30));
        assertThat(exact.canChangeNickname(NOW)).isTrue();
    }

    private UserService service(UserRepository repo) {
        return new UserService(repo, new ForbiddenWordFilter(List.of("금지")), CLOCK);
    }

    @Test
    @DisplayName("서비스: 고정 Clock 기준 nextChangeableAt = 변경 시각 + 30일, USER4002 data에 담긴다")
    void serviceUsesClock() {
        UserRepository repo = mock(UserRepository.class);
        User u = User.createEmailUser("a@a.com", "h", "처음닉", NOW.minusDays(100));
        when(repo.findById(1L)).thenReturn(Optional.of(u));
        when(repo.existsByNicknameIgnoreCase("새닉네임")).thenReturn(false);
        when(repo.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User changed = service(repo).changeNickname(1L, "새닉네임");
        assertThat(changed.getNicknameChangedAt()).isEqualTo(NOW);
        assertThat(changed.nextNicknameChangeableAt()).isEqualTo(NOW.plusDays(30));

        BusinessException e = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service(repo).changeNickname(1L, "또바꿈"));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER4002);
        assertThat(e.getData()).isEqualTo(Map.of("nextChangeableAt", NOW.plusDays(30)));
    }

    @Test
    @DisplayName("서비스: 금칙어는 30일/중복 검사보다 먼저, 중복은 저장 전에 거부")
    void serviceOrder() {
        UserRepository repo = mock(UserRepository.class);
        User u = User.createEmailUser("a@a.com", "h", "처음닉", NOW.minusDays(100));
        when(repo.findById(1L)).thenReturn(Optional.of(u));
        when(repo.existsByNicknameIgnoreCase("있는닉네임")).thenReturn(true);

        assertThatThrownBy(() -> service(repo).changeNickname(1L, "금지닉"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.USER4001);
        assertThatThrownBy(() -> service(repo).changeNickname(1L, "있는닉네임"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.USER4092);
        verify(repo, never()).saveAndFlush(any());
    }
}
