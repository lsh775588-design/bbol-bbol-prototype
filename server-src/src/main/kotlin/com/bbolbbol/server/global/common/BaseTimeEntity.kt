package com.bbolbbol.server.global.common

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 생성 & 수정 시각이 필요한 엔티티는 이 클래스를 상속하면 됨
 * ex) class User(...) : BaseTimeEntity()
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseTimeEntity {

	@CreatedDate
	@Column(nullable = false, updatable = false)
	var createdAt: LocalDateTime? = null
		protected set

	@LastModifiedDate
	@Column(nullable = false)
	var updatedAt: LocalDateTime? = null
		protected set
}
