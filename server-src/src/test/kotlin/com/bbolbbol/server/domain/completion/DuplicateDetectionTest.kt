package com.bbolbbol.server.domain.completion

import com.bbolbbol.server.domain.completion.service.CompletionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

/** 하루 1회 유니크 위반 식별(PostgreSQL 메시지 형태 포함)을 가짜 예외로 확인한다. */
class DuplicateDetectionTest {

	@Test
	@DisplayName("PostgreSQL 형태의 유니크 위반 메시지(제약 이름 포함)는 중복으로 본다")
	fun postgresUnique() {
		val root = RuntimeException(
			"ERROR: duplicate key value violates unique constraint \"uk_completions_user_place_date\"\n  Detail: Key (...)=(...) already exists.",
		)
		assertThat(CompletionService.isDuplicateCompletion(DataIntegrityViolationException("could not execute statement", root))).isTrue()
	}

	@Test
	@DisplayName("대문자 제약 이름(H2)도 중복으로 본다")
	fun upperCaseName() {
		val root = RuntimeException("Unique index or primary key violation: \"PUBLIC.UK_COMPLETIONS_USER_PLACE_DATE_INDEX_7 ON ...\"")
		assertThat(CompletionService.isDuplicateCompletion(DataIntegrityViolationException("x", root))).isTrue()
	}

	@Test
	@DisplayName("다른 제약(FK, NOT NULL, 다른 유니크) 위반은 중복이 아니다")
	fun otherConstraints() {
		for (msg in listOf(
			"ERROR: insert or update on table \"completions\" violates foreign key constraint \"fk_completions_user\"",
			"ERROR: null value in column \"lat\" violates not-null constraint",
			"ERROR: duplicate key value violates unique constraint \"uk_point_histories_completion_type\"",
		)) {
			assertThat(CompletionService.isDuplicateCompletion(DataIntegrityViolationException("x", RuntimeException(msg)))).isFalse()
		}
		assertThat(CompletionService.isDuplicateCompletion(DataIntegrityViolationException("no cause"))).isFalse()
	}
}
