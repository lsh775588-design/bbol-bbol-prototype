package com.bbolbbol.server.domain.place.entity

/** 핫플 종류. POPULAR는 인증 집계로 계산하는 값이라 DB에 저장하지 않는다(places.hot_type은 CURATED만 가진다). */
enum class HotType {
	CURATED,
	POPULAR,
}
