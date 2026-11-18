package com.bbolbbol.server.contract

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.TreeMap

/**
 * 앱-서버 계약 골든 파일 도구. 응답은 값이 아니라 "모양(shape)"만 비교한다.
 *
 * shape: 키 경로 -> 타입. 경로는 `$.data.places[].placeId` 형식이고 `[]`는 배열 원소다.
 * 타입: string, date(yyyy-MM-dd), datetime(ISO LocalDateTime 모양), integer, decimal, boolean, object, array, null,
 * 여러 번 관찰되면 `string|null`처럼 합친다. 합쳐진 표본에서 일부에만 없던 키는 `absent`가 붙는다.
 * 값(토큰, id, 시각, 점수)은 기록하지 않으므로 휘발성 값은 문제가 되지 않는다.
 */
object ContractSupport {

	val mapper: JsonMapper = JsonMapper.builder().build()

	/** `-Dcontract.update=true` 또는 환경변수 CONTRACT_UPDATE=true 이면 골든을 다시 쓴다. */
	val updateMode: Boolean =
		System.getProperty("contract.update") == "true" || System.getenv("CONTRACT_UPDATE") == "true"

	/** 골든은 소스 폴더에 있다(Gradle 테스트의 작업 디렉터리는 프로젝트 루트). */
	val root: Path = Paths.get("src", "test", "resources", "contract")
	val requestsDir: Path = root.resolve("requests")
	val responsesDir: Path = root.resolve("responses")

	fun JsonNode.items(): List<JsonNode> = (0 until size()).map { get(it) }

	private val DATE = Regex("""^[0-9]{4}-[0-9]{2}-[0-9]{2}$""")
	private val DATETIME = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$""")

	// ---- shape 계산

	private class Acc {
		val types = HashMap<String, MutableSet<String>>()
		val count = HashMap<String, Int>()
		val instances = HashMap<String, Int>() // 객체 경로 -> 관찰된 객체 수
	}

	/** 여러 표본(JSON 본문)을 하나의 shape로 합친다. */
	fun shapeOf(vararg bodies: String): TreeMap<String, String> {
		val acc = Acc()
		for (b in bodies) walk(mapper.readTree(b), "$", acc)
		val out = TreeMap<String, String>()
		for ((path, set) in acc.types) {
			val s = set.toMutableSet()
			if ("array" in s) s.remove("array:empty")
			val parent = parentOf(path)
			if (parent != null && (acc.count[path] ?: 0) < (acc.instances[parent] ?: 0)) s.add("absent")
			out[path] = s.sortedWith(compareBy({ it == "null" }, { it == "absent" }, { it })).joinToString("|")
		}
		return out
	}

	private fun parentOf(path: String): String? {
		val i = path.lastIndexOf('.')
		return if (i <= 0 || path.endsWith("[]")) null else path.substring(0, i)
	}

	private fun walk(n: JsonNode, path: String, acc: Acc) {
		acc.count.merge(path, 1, Int::plus)
		val set = acc.types.getOrPut(path) { mutableSetOf() }
		when {
			n.isObject -> {
				set += "object"
				acc.instances.merge(path, 1, Int::plus)
				for (name in n.propertyNames()) walk(n.get(name), "$path.$name", acc)
			}
			n.isArray -> {
				if (n.size() == 0) set += "array:empty" else set += "array"
				for (el in n) walk(el, "$path[]", acc)
			}
			n.isString -> set += when {
				DATETIME.matches(n.asString()) -> "datetime"
				DATE.matches(n.asString()) -> "date"
				else -> "string"
			}
			n.isBoolean -> set += "boolean"
			n.isNull -> set += "null"
			n.isIntegralNumber -> set += "integer"
			n.isNumber -> set += "decimal"
			else -> set += "unknown"
		}
	}

	// ---- 골든 파일 입출력

	data class Golden(
		val endpoint: String,
		val case: String,
		val httpStatus: Int,
		val code: String,
		val shape: Map<String, String>,
	)

	fun responseFile(id: String, case: String): Path = responsesDir.resolve("$id.$case.json")

	fun render(g: Golden): String {
		val q = { s: String -> mapper.writeValueAsString(s) }
		val sb = StringBuilder()
		sb.append("{\n")
		sb.append("  \"endpoint\": ${q(g.endpoint)},\n")
		sb.append("  \"case\": ${q(g.case)},\n")
		sb.append("  \"httpStatus\": ${g.httpStatus},\n")
		sb.append("  \"code\": ${q(g.code)},\n")
		sb.append("  \"shape\": {\n")
		val entries = g.shape.entries.toList()
		entries.forEachIndexed { i, (k, v) ->
			sb.append("    ${q(k)}: ${q(v)}").append(if (i < entries.size - 1) ",\n" else "\n")
		}
		sb.append("  }\n}\n")
		return sb.toString()
	}

	fun parse(text: String): Golden {
		val n = mapper.readTree(text)
		val shape = TreeMap<String, String>()
		for (k in n.get("shape").propertyNames()) shape[k] = n.get("shape").get(k).asString()
		return Golden(
			n.get("endpoint").asString(), n.get("case").asString(),
			n.get("httpStatus").asInt(), n.get("code").asString(), shape,
		)
	}

	/** 실제와 골든을 비교한다. 같으면 null, 다르면 읽기 쉬운 차이 설명. 업데이트 모드면 파일을 쓰고 null. */
	fun verify(id: String, actual: Golden): String? {
		val file = responseFile(id, actual.case)
		if (updateMode) {
			// 모양이 같으면 다시 쓰지 않는다(줄바꿈만 달라지는 diff를 막는다)
			if (Files.exists(file) && parse(Files.readString(file)) == actual) return null
			Files.createDirectories(file.parent)
			Files.writeString(file, render(actual))
			return null
		}
		if (!Files.exists(file)) {
			return "골든 파일이 없다: $file. 새 계약이면 -Dcontract.update=true 로 만들고 diff를 검토해 커밋한다."
		}
		val expected = parse(Files.readString(file))
		val diffs = mutableListOf<String>()
		if (expected.httpStatus != actual.httpStatus) diffs += "HTTP 상태: 골든 ${expected.httpStatus} -> 실제 ${actual.httpStatus}"
		if (expected.code != actual.code) diffs += "code: 골든 ${expected.code} -> 실제 ${actual.code}"
		for ((k, t) in expected.shape) {
			val a = actual.shape[k]
			if (a == null) diffs += "키 사라짐(앱이 읽을 수 있음): $k (골든 $t)"
			else if (a != t) diffs += "타입 바뀜: $k 골든 $t -> 실제 $a"
		}
		for ((k, t) in actual.shape) if (k !in expected.shape) diffs += "키 새로 생김: $k ($t)"
		if (diffs.isEmpty()) return null
		return "계약 불일치 [${actual.endpoint} / ${actual.case}] ${file}\n  " + diffs.joinToString("\n  ") +
			"\n의도한 변경이면 앱 DTO를 맞추고 -Dcontract.update=true 로 골든을 갱신한다."
	}

	// ---- 요청 골든

	class RequestSpec(val id: String, val node: JsonNode) {
		val method: String get() = node.get("method").asString()
		val path: String get() = node.get("path").asString()
		val auth: Boolean get() = node.get("auth").asBoolean()
		val body: JsonNode? get() = node.get("body")
		val requiredQuery: List<String> get() = node.get("requiredQuery").items().map { it.asString() }

		/** 본문 키 중 빠지면 COMMON400이어야 하는 키. 파일에 requiredBody가 없으면 본문 키 전부다(PATCH처럼 전부 선택이면 빈 배열). */
		val requiredBody: List<String>
			get() = node.get("requiredBody")?.items()?.map { it.asString() } ?: body?.propertyNames()?.toList().orEmpty()
		val multipart: JsonNode? get() = node.get("multipart")

		fun query(): Map<String, String> {
			val m = LinkedHashMap<String, String>()
			val q = node.get("query")
			for (k in q.propertyNames()) m[k] = q.get(k).asString()
			return m
		}

		fun bodyJson(): String? = body?.let { mapper.writeValueAsString(it) }
		fun bodyWithout(key: String): String {
			val copy = body!!.deepCopy() as tools.jackson.databind.node.ObjectNode
			copy.remove(key)
			return mapper.writeValueAsString(copy)
		}
	}

	fun request(id: String): RequestSpec =
		RequestSpec(id, mapper.readTree(Files.readString(requestsDir.resolve("$id.json"))))

	/** 이메일 인증, 재발급, 탈퇴, 카카오처럼 상태를 바꾸는 인증 흐름 엔드포인트. ContractAuthFlowTest가 담당한다(나머지는 ContractGoldenTest). */
	val authFlowIds: Set<String> = setOf(
		"auth-email-verifications", "auth-email-verifications-confirm", "auth-password-reset",
		"auth-reissue", "auth-logout", "auth-kakao", "users-me-delete",
	)

	fun requestIds(): List<String> =
		Files.list(requestsDir).use { s -> s.map { it.fileName.toString().removeSuffix(".json") }.sorted().toList() }
}
