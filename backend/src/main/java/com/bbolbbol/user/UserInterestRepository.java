package com.bbolbbol.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserInterestRepository extends JpaRepository<UserInterest, UserInterestId> {

    List<UserInterest> findByUserId(Long userId);
}
