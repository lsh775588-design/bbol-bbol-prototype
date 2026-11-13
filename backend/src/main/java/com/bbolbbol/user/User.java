package com.bbolbbol.user;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** users 테이블. 다른 도메인은 이 엔티티가 아니라 UserService를 쓴다. */
@Entity
@Table(name = "users")
public class User {

    /** 닉네임 변경 간격(일). */
    public static final int NICKNAME_CHANGE_INTERVAL_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Convert(converter = EnumConverters.LoginTypeConverter.class)
    @Column(name = "login_type", nullable = false)
    private LoginType loginType;

    @Column(name = "kakao_id")
    private String kakaoId;

    @Column(name = "nickname")
    private String nickname;

    /** 가입/온보딩 때 정한 닉네임은 null(제한에 포함하지 않음). */
    @Column(name = "nickname_changed_at")
    private LocalDateTime nicknameChangedAt;

    @Convert(converter = EnumConverters.UserStatusConverter.class)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected User() {}

    public static User createEmailUser(String email, String passwordHash, String nickname, LocalDateTime now) {
        User u = new User();
        u.email = email;
        u.passwordHash = passwordHash;
        u.loginType = LoginType.EMAIL;
        u.nickname = nickname;
        u.nicknameChangedAt = null;
        u.status = UserStatus.ACTIVE;
        u.onboardingCompleted = false;
        u.createdAt = now;
        u.updatedAt = now;
        return u;
    }

    /** 마지막 변경 시각 + 30일. 한 번도 안 바꿨으면 null(바로 변경 가능). */
    public LocalDateTime nextNicknameChangeableAt() {
        return nicknameChangedAt == null ? null : nicknameChangedAt.plusDays(NICKNAME_CHANGE_INTERVAL_DAYS);
    }

    public boolean canChangeNickname(LocalDateTime now) {
        LocalDateTime next = nextNicknameChangeableAt();
        return next == null || !now.isBefore(next);
    }

    public void changeNickname(String newNickname, LocalDateTime now) {
        this.nickname = newNickname;
        this.nicknameChangedAt = now;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public LoginType getLoginType() { return loginType; }
    public String getNickname() { return nickname; }
    public LocalDateTime getNicknameChangedAt() { return nicknameChangedAt; }
    public UserStatus getStatus() { return status; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
