package com.bbolbbol.user;

import java.io.Serializable;
import java.util.Objects;

public class UserInterestId implements Serializable {

    private Long userId;
    private Long categoryId;

    protected UserInterestId() {}

    public UserInterestId(Long userId, Long categoryId) {
        this.userId = userId;
        this.categoryId = categoryId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UserInterestId other
                && Objects.equals(userId, other.userId) && Objects.equals(categoryId, other.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, categoryId);
    }
}
