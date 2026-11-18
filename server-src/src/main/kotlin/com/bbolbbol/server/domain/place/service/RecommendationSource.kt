package com.bbolbbol.server.domain.place.service

import com.bbolbbol.server.domain.place.entity.HotType
import com.bbolbbol.server.domain.place.entity.Place
import com.bbolbbol.server.domain.place.repository.PlaceRepository
import org.springframework.stereotype.Component

/**
 * 추천 후보 장소의 공급원. [PlaceRecommendationService]는 등록된 모든 공급원의 후보를 합쳐 쓰므로,
 * 새 공급원은 이 인터페이스를 구현한 빈만 추가하면 되고 컨트롤러와 서비스는 바뀌지 않는다.
 * TODO(stage 3 stats): 최근 7일 인증 수 상위 20곳(최소 2회)을 돌려주는 POPULAR 공급원. 인증 집계가 필요하다.
 */
interface RecommendationSource {

	fun candidates(): List<Place>
}

/** 뽈뽈 추천(CURATED): hot_type이 CURATED인 활성 장소. */
@Component
class CuratedRecommendationSource(private val placeRepository: PlaceRepository) : RecommendationSource {

	override fun candidates(): List<Place> = placeRepository.findActiveByHotType(HotType.CURATED)
}
