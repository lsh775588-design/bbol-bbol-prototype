package com.bbolbbol.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "place_categories")
public class PlaceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "reward_point", nullable = false)
    private int rewardPoint;

    protected PlaceCategory() {
    }

    public PlaceCategory(String code, String name, int rewardPoint) {
        this.code = code;
        this.name = name;
        this.rewardPoint = rewardPoint;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getRewardPoint() { return rewardPoint; }
}
