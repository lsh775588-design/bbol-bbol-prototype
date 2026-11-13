package com.bbolbbol.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** package-private: 다른 도메인은 UserService만 쓴다. */
interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNicknameIgnoreCase(String nickname);

    boolean existsByIdAndStatus(Long id, UserStatus status);

    List<User> findAllByIdIn(Collection<Long> ids);
}
