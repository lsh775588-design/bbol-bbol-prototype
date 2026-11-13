package com.bbolbbol.app.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** 서버가 만든 응답 골든 한 파일. shape는 `$.data.places[].placeId` -> `string|null` 같은 평면 맵. */
data class GoldenResponse(
    val file: String,
    val id: String,
    val case: String,
    val endpoint: String,
    val httpStatus: Int,
    val code: String,
    val shape: Map<String, String>,
) {
    val label get() = "[$endpoint / $case]"
    val isError get() = case.startsWith("error-")

    /** path의 직계 자식 이름들 */
    fun childNames(path: String): List<String> =
        shape.keys.filter { it.startsWith("$path.") }
            .map { it.removePrefix("$path.").substringBefore('.').substringBefore('[') }
            .distinct()

    fun types(path: String): Set<String> = shape[path]?.split("|")?.toSet() ?: emptySet()
}

/** 서버가 정한 요청 골든(앱이 보내는 JSON과 쿼리). */
data class GoldenRequest(
    val id: String,
    val method: String,
    val path: String,
    val auth: Boolean,
    val query: Map<String, String>,
    val requiredQuery: List<String>,
    val body: JsonObject?,
) {
    val label get() = "[$method $path]"
}

object ContractFiles {
    private val root: File = File(ContractFiles::class.java.getResource("/contract")!!.toURI())
    private val json = Json

    fun responses(): List<GoldenResponse> =
        File(root, "responses").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }.map { f ->
            val o = json.parseToJsonElement(f.readText()).jsonObject
            GoldenResponse(
                file = f.name,
                id = f.name.substringBefore('.'),
                case = o.getValue("case").jsonPrimitive.content,
                endpoint = o.getValue("endpoint").jsonPrimitive.content,
                httpStatus = o.getValue("httpStatus").jsonPrimitive.int,
                code = o.getValue("code").jsonPrimitive.content,
                shape = o.getValue("shape").jsonObject.mapValues { it.value.jsonPrimitive.content },
            )
        }

    fun requests(): List<GoldenRequest> =
        File(root, "requests").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }.map { f ->
            val o = json.parseToJsonElement(f.readText()).jsonObject
            GoldenRequest(
                id = f.nameWithoutExtension,
                method = o.getValue("method").jsonPrimitive.content,
                path = o.getValue("path").jsonPrimitive.content,
                auth = o.getValue("auth").jsonPrimitive.boolean,
                query = o.getValue("query").jsonObject.mapValues { it.value.jsonPrimitive.content },
                requiredQuery = o.getValue("requiredQuery").jsonArray.map { it.jsonPrimitive.content },
                body = o["body"]?.jsonObject,
            )
        }

    /**
     * shape에서 실제 JSON 표본을 만든다. nullVariant=true면 null 가능한 값을 모두 null로(absent는 생략) 만든다.
     * 앱의 실제 Json 설정으로 읽어 보는 용도라 값 자체에는 의미가 없다.
     */
    fun sample(g: GoldenResponse, nullVariant: Boolean): String = build(g, "$", nullVariant).toString()

    private fun build(g: GoldenResponse, path: String, nullVariant: Boolean): JsonElement {
        // 공통 응답 봉투: code, success는 골든이 알고 있는 값으로 채운다
        if (path == "$.code") return JsonPrimitive(g.code)
        if (path == "$.success") return JsonPrimitive(!g.isError)
        val types = g.types(path)
        val nonNull = types - "null" - "absent"
        if (nullVariant && "null" in types) return JsonNull
        return when (val t = nonNull.sorted().firstOrNull()) {
            null -> JsonNull
            "object" -> JsonObject(
                g.childNames(path).mapNotNull { name ->
                    val child = "$path.$name"
                    if (nullVariant && "absent" in g.types(child)) null else name to build(g, child, nullVariant)
                }.toMap(),
            )
            "array" -> JsonArray(listOf(build(g, "$path[]", nullVariant)))
            "array:empty" -> JsonArray(emptyList())
            "string" -> JsonPrimitive("s")
            "datetime" -> JsonPrimitive("2026-11-10T12:00:00")
            "integer" -> JsonPrimitive(1)
            "decimal" -> JsonPrimitive(1.5)
            "boolean" -> JsonPrimitive(true)
            else -> error("알 수 없는 shape 타입 '$t' at $path (${g.label})")
        }
    }

    /** 요청 본문(JsonObject)을 shape로 줄인다: 키 -> string|integer|decimal|boolean|null|object|array */
    fun bodyShape(o: JsonObject): Map<String, String> = o.mapValues { (_, v) -> typeOf(v) }

    fun typeOf(v: JsonElement): String = when (v) {
        is JsonNull -> "null"
        is JsonObject -> "object"
        is JsonArray -> "array"
        is JsonPrimitive -> when {
            v.isString -> "string"
            v.booleanOrNull != null -> "boolean"
            Regex("-?[0-9]+").matches(v.content) -> "integer"
            else -> "decimal"
        }
    }
}
