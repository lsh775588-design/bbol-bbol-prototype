package com.bbolbbol.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "places")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private PlaceCategory category;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(name = "road_address")
    private String roadAddress;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "radius_m", nullable = false)
    private int radiusM = 100;

    @Column(name = "notify_radius_m", nullable = false)
    private int notifyRadiusM = 200;

    /** null이면 카테고리 기본 점수를 쓴다(장소 값이 카테고리 값을 덮는다, 쟁점 7 가정). */
    @Column(name = "reward_point")
    private Integer rewardPoint;

    @Column(name = "kakao_place_id", nullable = false, length = 30, unique = true)
    private String kakaoPlaceId;

    @Column(name = "kakao_place_url", nullable = false)
    private String kakaoPlaceUrl;

    /** 뽈뽈 추천이면 CURATED, 아니면 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "hot_type", length = 20)
    private HotType hotType;

    @Column(nullable = false)
    private boolean active = true;

    // created_at, updated_at은 DB 기본값(CURRENT_TIMESTAMP)을 쓰고 엔티티는 매핑하지 않는다.

    protected Place() {
    }

    public Place(PlaceCategory category, String name, String address, String roadAddress, String phone,
                 double lat, double lng, int radiusM, int notifyRadiusM, Integer rewardPoint,
                 String kakaoPlaceId, String kakaoPlaceUrl, HotType hotType, boolean active) {
        this.category = category;
        this.name = name;
        this.address = address;
        this.roadAddress = roadAddress;
        this.phone = phone;
        this.lat = lat;
        this.lng = lng;
        this.radiusM = radiusM;
        this.notifyRadiusM = notifyRadiusM;
        this.rewardPoint = rewardPoint;
        this.kakaoPlaceId = kakaoPlaceId;
        this.kakaoPlaceUrl = kakaoPlaceUrl;
        this.hotType = hotType;
        this.active = active;
    }

    /** 첫 방문 기준 점수: 장소 값이 있으면 그것, 없으면 카테고리 기본 점수. */
    public int effectiveRewardPoint() {
        return RewardPolicy.effective(rewardPoint, category.getRewardPoint());
    }

    public Long getId() { return id; }
    public PlaceCategory getCategory() { return category; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getRoadAddress() { return roadAddress; }
    public String getPhone() { return phone; }
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public int getRadiusM() { return radiusM; }
    public int getNotifyRadiusM() { return notifyRadiusM; }
    public Integer getRewardPoint() { return rewardPoint; }
    public String getKakaoPlaceId() { return kakaoPlaceId; }
    public String getKakaoPlaceUrl() { return kakaoPlaceUrl; }
    public HotType getHotType() { return hotType; }
    public boolean isActive() { return active; }
}
