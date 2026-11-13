package com.bbolbbol.app

import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.FieldError
import com.bbolbbol.app.data.api.parseFailure
import com.bbolbbol.app.util.ErrorMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {
    private fun failure(code: String, status: Int = 400, errors: List<FieldError> = emptyList()) =
        ApiResult.Failure(status, code, "서버 메시지", errors)

    @Test
    fun mapsKnownCodesToKorean() {
        assertEquals("이메일 또는 비밀번호가 일치하지 않아요.", ErrorMessages.message(failure("AUTH4011", 401)))
        assertEquals("이미 가입된 이메일이에요.", ErrorMessages.message(failure("USER409", 409)))
        assertEquals("이미 사용 중인 닉네임이에요.", ErrorMessages.message(failure("USER4092", 409)))
        assertEquals("사용할 수 없는 단어가 닉네임에 포함되어 있어요.", ErrorMessages.message(failure("USER4001")))
        assertEquals("이메일 인증 정보가 없거나 만료되었어요.", ErrorMessages.message(failure("EMAIL4003")))
        assertTrue(ErrorMessages.message(failure("AUTH401", 401)).contains("다시 로그인"))
    }

    @Test
    fun common400UsesFirstFieldReason() {
        val f = failure("COMMON400", errors = listOf(FieldError("nickname", "닉네임 형식 오류"), FieldError("password", "x")))
        assertEquals("닉네임 형식 오류", ErrorMessages.message(f))
        assertEquals(mapOf("nickname" to "닉네임 형식 오류", "password" to "x"), ErrorMessages.fieldErrors(f))
    }

    @Test
    fun common400WithoutFieldsFallsBack() {
        assertEquals("입력값을 다시 확인해 주세요.", ErrorMessages.message(failure("COMMON400")))
    }

    @Test
    fun unknownCodeAndServerErrorAndNetwork() {
        assertEquals(ErrorMessages.UNKNOWN, ErrorMessages.message(failure("WHAT", 418)))
        assertTrue(ErrorMessages.message(failure("", 502)).contains("서버에 문제"))
        assertEquals(ErrorMessages.NETWORK, ErrorMessages.message(ApiResult.NetworkError))
    }

    @Test
    fun parsesCommon400BodyAndNonErrorsData() {
        val f = parseFailure(
            400,
            """{"success":false,"code":"COMMON400","message":"m","data":{"errors":[{"field":"lat","reason":"r"}]}}""",
        )
        assertEquals("COMMON400", f.code)
        assertEquals(listOf(FieldError("lat", "r")), f.fieldErrors)
        val g = parseFailure(
            400,
            """{"success":false,"code":"USER4002","message":"m","data":{"nextChangeableAt":"2026-12-01T00:00:00"}}""",
        )
        assertEquals("USER4002", g.code)
        assertEquals("", parseFailure(500, "<html>").code)
    }
}
