package com.bbolbbol.app.data.local

import android.content.Context
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** 받은 퀘스트를 문자열 집합으로 저장하는 곳(SharedPreferences 또는 테스트용 메모리). */
interface QuestStorage {
    fun read(): Set<String>
    fun write(entries: Set<String>)
}

class InMemoryQuestStorage : QuestStorage {
    private var data: Set<String> = emptySet()
    override fun read() = data
    override fun write(entries: Set<String>) { data = entries }
}

class PrefsQuestStorage(context: Context) : QuestStorage {
    private val prefs = context.applicationContext.getSharedPreferences("bbol_accepted_quests", Context.MODE_PRIVATE)
    override fun read(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
    override fun write(entries: Set<String>) { prefs.edit().putStringSet(KEY, entries).apply() }
    private companion object { const val KEY = "entries" }
}

/**
 * 받은(수락한) 퀘스트 기록. 서버에는 수락 API가 없어서 앱 안에만 둔다.
 * 퀘스트는 하루 단위라서 Asia/Seoul 날짜가 바뀌면 지난 기록은 사라진다. 사용자(userId)마다 따로 센다.
 */
class QuestBook(
    private val storage: QuestStorage,
    private val clock: Clock = Clock.system(KST),
) {
    private fun today(): LocalDate = LocalDate.now(clock.withZone(KST))

    private fun key(userId: Long, placeId: Long, date: LocalDate) = "$userId|$placeId|$date"

    /** 오늘 날짜가 아닌 기록은 버린 목록. 바뀌었으면 저장소에도 반영한다. */
    @Synchronized
    private fun live(): Set<String> {
        val all = storage.read()
        val suffix = "|${today()}"
        val kept = all.filterTo(HashSet()) { it.endsWith(suffix) }
        if (kept.size != all.size) storage.write(kept)
        return kept
    }

    @Synchronized
    fun accept(userId: Long, placeId: Long) {
        storage.write(live() + key(userId, placeId, today()))
    }

    @Synchronized
    fun isAccepted(userId: Long, placeId: Long): Boolean = key(userId, placeId, today()) in live()

    /** 이 사용자가 오늘 받은 장소 id 모음. */
    @Synchronized
    fun acceptedIds(userId: Long): Set<Long> {
        val prefix = "$userId|"
        return live().filter { it.startsWith(prefix) }.mapNotNull { it.split('|').getOrNull(1)?.toLongOrNull() }.toSet()
    }

    /** 포기하거나 도착 인증에 성공했을 때. */
    @Synchronized
    fun remove(userId: Long, placeId: Long) {
        storage.write(live() - key(userId, placeId, today()))
    }

    /** 로그아웃, 탈퇴, 세션 만료. */
    @Synchronized
    fun clearAll() {
        storage.write(emptySet())
    }

    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
