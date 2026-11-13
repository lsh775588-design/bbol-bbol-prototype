package com.bbolbbol.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** user_interests 테이블(온보딩 관심 카테고리, 최대 3개). 온보딩 API는 쟁점 5 대기라 아직 쓰는 곳이 없다. */
@Entity
@Table(name = "user_interests")
@IdClass(UserInterestId.class)
public class UserInterest {

    public static final int MAX_INTERESTS = 3;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "category_id")
    private Long categoryId;

    protected UserInterest() {}

    public UserInterest(Long userId, Long categoryId) {
        this.userId = userId;
        this.categoryId = categoryId;
    }

    public Long getUserId() { return userId; }
    public Long getCategoryId() { return categoryId; }
}
