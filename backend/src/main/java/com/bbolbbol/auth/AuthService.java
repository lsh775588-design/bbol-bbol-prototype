package com.bbolbbol.auth;

import com.bbolbbol.common.error.BusinessException;
import com.bbolbbol.common.error.ErrorCode;
import com.bbolbbol.common.security.JwtProvider;
import com.bbolbbol.user.User;
import com.bbolbbol.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    /** 존재하지 않는 이메일에도 BCrypt 비교를 한 번 수행해 응답 시간 차이로 이메일 존재를 알 수 없게 한다. */
    private final String dummyHash;

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenVerifier verificationTokenVerifier;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder,
                       VerificationTokenVerifier verificationTokenVerifier, JwtProvider jwtProvider,
                       RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.verificationTokenVerifier = verificationTokenVerifier;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public TokenResponse signup(SignupRequest request) {
        String email = verificationTokenVerifier.verifySignupToken(request.verificationToken());
        User user = userService.createEmailUser(email, passwordEncoder.encode(request.password()),
                request.nickname());
        verificationTokenVerifier.consume(request.verificationToken());
        return tokens(user, true);
    }

    public TokenResponse login(LoginRequest request) {
        User user = userService.findActiveByEmail(request.email()).orElse(null);
        String hash = user != null && user.getPasswordHash() != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || user.getPasswordHash() == null || !matches) {
            throw new BusinessException(ErrorCode.AUTH4011);
        }
        return tokens(user, !user.isOnboardingCompleted());
    }

    private TokenResponse tokens(User user, boolean isNewUser) {
        return new TokenResponse(user.getId(), jwtProvider.createAccessToken(user.getId()),
                refreshTokenService.issue(user.getId()), isNewUser);
    }
}
