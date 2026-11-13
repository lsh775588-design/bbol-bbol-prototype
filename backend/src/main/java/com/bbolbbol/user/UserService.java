package com.bbolbbol.user;

import com.bbolbbol.common.error.BusinessException;
import com.bbolbbol.common.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** user 도메인의 공개 진입점. 다른 도메인은 UserRepository가 아니라 이 서비스를 쓴다. */
@Service
public class UserService {

    /** 탈퇴한 사용자(또는 존재하지 않는 사용자)의 표시 닉네임. */
    public static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

    private final UserRepository userRepository;
    private final ForbiddenWordFilter forbiddenWordFilter;
    private final Clock clock;

    public UserService(UserRepository userRepository, ForbiddenWordFilter forbiddenWordFilter, Clock clock) {
        this.userRepository = userRepository;
        this.forbiddenWordFilter = forbiddenWordFilter;
        this.clock = clock;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * 이메일 가입 사용자를 만든다. 금칙어(USER4001), 이메일 중복(USER409), 닉네임 중복(USER4092).
     * 동시 가입 경합은 DB 유니크 제약 위반으로 잡아 같은 코드로 변환한다(트랜잭션 없이 saveAndFlush).
     */
    public User createEmailUser(String email, String passwordHash, String nickname) {
        String normalized = normalizeEmail(email);
        if (forbiddenWordFilter.containsForbiddenWord(nickname)) {
            throw new BusinessException(ErrorCode.USER4001);
        }
        if (userRepository.existsByEmail(normalized)) {
            throw new BusinessException(ErrorCode.USER409);
        }
        if (userRepository.existsByNicknameIgnoreCase(nickname)) {
            throw new BusinessException(ErrorCode.USER4092);
        }
        try {
            return userRepository.saveAndFlush(User.createEmailUser(normalized, passwordHash, nickname, now()));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(userRepository.existsByEmail(normalized)
                    ? ErrorCode.USER409 : ErrorCode.USER4092);
        }
    }

    @Transactional(readOnly = true)
    public Optional<User> findActiveByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email)).filter(User::isActive);
    }

    /** 활성 사용자만. 탈퇴/없음이면 USER404. */
    @Transactional(readOnly = true)
    public User getActiveUser(Long userId) {
        return userRepository.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER404));
    }

    /**
     * 닉네임 변경. 순서: 금칙어(USER4001) -> 30일(USER4002, data.nextChangeableAt) -> 중복(USER4092).
     * 현재 닉네임과 같은 값은 "이미 사용 중"으로 보아 USER4092.
     */
    public User changeNickname(Long userId, String nickname) {
        User user = getActiveUser(userId);
        if (forbiddenWordFilter.containsForbiddenWord(nickname)) {
            throw new BusinessException(ErrorCode.USER4001);
        }
        LocalDateTime now = now();
        if (!user.canChangeNickname(now)) {
            throw new BusinessException(ErrorCode.USER4002,
                    Map.of("nextChangeableAt", user.nextNicknameChangeableAt()));
        }
        if (userRepository.existsByNicknameIgnoreCase(nickname)) {
            throw new BusinessException(ErrorCode.USER4092);
        }
        user.changeNickname(nickname, now);
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.USER4092);
        }
    }

    @Transactional(readOnly = true)
    public AvailabilityResult checkEmailAvailability(String email) {
        return userRepository.findByEmail(normalizeEmail(email)).filter(User::isActive)
                .map(u -> AvailabilityResult.unavailable(
                        u.getLoginType() == LoginType.KAKAO ? "REGISTERED_WITH_KAKAO" : "DUPLICATED_EMAIL"))
                .orElseGet(AvailabilityResult::ok);
    }

    @Transactional(readOnly = true)
    public AvailabilityResult checkNicknameAvailability(String nickname) {
        if (!NicknamePolicy.isValidFormat(nickname)) {
            return AvailabilityResult.unavailable("INVALID_FORMAT");
        }
        if (forbiddenWordFilter.containsForbiddenWord(nickname)) {
            return AvailabilityResult.unavailable("FORBIDDEN_WORD");
        }
        if (userRepository.existsByNicknameIgnoreCase(nickname)) {
            return AvailabilityResult.unavailable("DUPLICATED");
        }
        return AvailabilityResult.ok();
    }

    // ---- 다른 도메인(ranking, completion, point 등)용 ----

    @Transactional(readOnly = true)
    public boolean isActive(Long userId) {
        return userRepository.existsByIdAndStatus(userId, UserStatus.ACTIVE);
    }

    /** 표시용 닉네임. 탈퇴/없음이면 "탈퇴한 사용자", 활성이지만 닉네임 미설정이면 null. */
    @Transactional(readOnly = true)
    public String getDisplayNickname(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.isActive() ? u.getNickname() : WITHDRAWN_NICKNAME)
                .orElse(WITHDRAWN_NICKNAME);
    }

    /** 여러 사용자의 표시 닉네임(랭킹 등). 없는 id는 "탈퇴한 사용자". 활성인데 닉네임이 없으면 값이 null. */
    @Transactional(readOnly = true)
    public Map<Long, String> getDisplayNicknames(Collection<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        userIds.forEach(id -> result.put(id, WITHDRAWN_NICKNAME));
        userRepository.findAllByIdIn(userIds)
                .forEach(u -> result.put(u.getId(), u.isActive() ? u.getNickname() : WITHDRAWN_NICKNAME));
        return result;
    }
}
