package com.bbolbbol.app

import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.repository.AuthRepository
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.data.repository.UserRepository
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryTest {
    private val fx = Fixture()

    @After
    fun tearDown() = fx.shutdown()

    @Test
    fun loginSuccessStoresTokens() = runBlocking {
        fx.enqueue(200, Json.TOKENS)
        val r = AuthRepository(fx.api, fx.session).login(" a@b.com ", "pw")
        assertTrue(r is ApiResult.Success)
        assertTrue(fx.session.loggedIn.value)
        assertEquals("acc-1", fx.store.accessToken)
        val req = fx.server.takeRequest()
        assertEquals("/auth/login", req.path)
        assertNull(req.getHeader("Authorization"))
        assertTrue(req.body.readUtf8().contains("\"email\":\"a@b.com\""))
    }

    @Test
    fun loginFailureMapsCodeAndKeepsLoggedOut() = runBlocking {
        fx.enqueue(401, Json.error("AUTH4011"))
        val r = AuthRepository(fx.api, fx.session).login("a@b.com", "bad")
        assertEquals("AUTH4011", (r as ApiResult.Failure).code)
        assertFalse(fx.session.loggedIn.value)
    }

    @Test
    fun networkErrorWhenServerDown() = runBlocking {
        fx.server.shutdown()
        val r = AuthRepository(fx.api, fx.session).login("a@b.com", "pw")
        assertEquals(ApiResult.NetworkError, r)
    }

    @Test
    fun signupSendsOnlyTokenPasswordNickname() = runBlocking {
        fx.enqueue(201, Json.TOKENS)
        val r = AuthRepository(fx.api, fx.session).signup("tok:a@b.com", "Passw0rd!", "뽈뽈이")
        assertTrue(r is ApiResult.Success)
        val body = fx.server.takeRequest().body.readUtf8()
        assertEquals("""{"verificationToken":"tok:a@b.com","password":"Passw0rd!","nickname":"뽈뽈이"}""", body)
    }

    @Test
    fun bearerHeaderIsAddedForProtectedCalls() = runBlocking {
        fx.session.saveTokens("acc-9", "ref-9")
        fx.enqueue(
            200,
            Json.ok("""{"userId":1,"email":"a@b.com","nickname":"닉","loginType":"EMAIL","onboardingCompleted":false,"createdAt":"2026-11-01T10:00:00"}"""),
        )
        val r = UserRepository(fx.api).me()
        assertEquals("닉", (r as ApiResult.Success).data.nickname)
        assertEquals("Bearer acc-9", fx.server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun auth401ClearsTokens() = runBlocking {
        fx.session.saveTokens("old", "ref")
        fx.enqueue(401, Json.error("AUTH401"))
        val r = UserRepository(fx.api).me()
        assertEquals("AUTH401", (r as ApiResult.Failure).code)
        assertFalse(fx.session.loggedIn.value)
        assertNull(fx.store.accessToken)
    }

    @Test
    fun placesParseHotAndCompletedFieldNames() = runBlocking {
        fx.session.saveTokens("a", "r")
        fx.enqueue(
            200,
            Json.ok(
                """{"places":[
                {"placeId":1,"name":"경복궁","category":"HISTORY","lat":37.5796,"lng":126.9770,"distance":120,"radius":100,"notifyRadius":300,
                 "rewardPoint":30,"expectedPoint":15,"isHot":true,"hotType":"TRENDING","visitedBefore":true,"isCompleted":true,"extra":1},
                {"placeId":2,"name":"카페","category":"CAFE","lat":37.57,"lng":126.98,"distance":300,"radius":50,"notifyRadius":200,
                 "rewardPoint":10,"expectedPoint":10,"isHot":false,"hotType":null,"visitedBefore":false,"isCompleted":false}]}""",
            ),
        )
        val r = PlaceRepository(fx.api).places(37.5729, 126.9794, 1000, "CAFE", 50)
        val places = (r as ApiResult.Success).data
        assertEquals(2, places.size)
        assertTrue(places[0].isHot)
        assertTrue(places[0].isCompleted)
        assertEquals("TRENDING", places[0].hotType)
        assertFalse(places[1].isHot)
        assertFalse(places[1].isCompleted)
        assertNull(places[1].hotType)
        val path = fx.server.takeRequest().path!!
        assertTrue(path, path.startsWith("/places?lat=37.5729&lng=126.9794&radius=1000&category=CAFE&size=50"))
    }

    @Test
    fun placeDetailAndCategoriesParse() = runBlocking {
        fx.enqueue(200, Json.ok("""{"categories":[{"code":"CAFE","name":"카페","rewardPoint":10}]}"""))
        val cats = (PlaceRepository(fx.api).categories() as ApiResult.Success).data
        assertEquals("카페", cats[0].name)
        fx.enqueue(
            200,
            Json.ok(
                """{"placeId":3,"name":"x","category":"CAFE","categoryName":"카페","address":"a","roadAddress":null,"phone":null,"lat":37.5,"lng":127.0,"radius":50,"notifyRadius":100,"kakaoPlaceUrl":null,"rewardPoint":10,"expectedPoint":10,"photoBonusAvailable":true,"isHot":true,"hotType":"NEW","completedCount":5,"visitedBefore":false,"isCompleted":true,"myLastCompletedAt":"2026-11-01T09:00:00"}""",
            ),
        )
        val d = (PlaceRepository(fx.api).place(3) as ApiResult.Success).data
        assertTrue(d.isHot)
        assertTrue(d.isCompleted)
        assertEquals(5L, d.completedCount)
        assertNull(d.roadAddress)
    }

    @Test
    fun placesCommon400FieldErrors() = runBlocking {
        fx.enqueue(400, Json.error("COMMON400", """{"errors":[{"field":"radius","reason":"반경 오류"}]}"""))
        val r = PlaceRepository(fx.api).places(1.0, 2.0, 9999, null)
        assertEquals("반경 오류", ErrorMessages.message(r))
    }
}
