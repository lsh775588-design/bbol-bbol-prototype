package com.bbolbbol.server.support

import tools.jackson.databind.JsonNode

/** Jackson 3의 JsonNode는 자체 map(...) 멤버가 있어 Kotlin의 map이 가려진다. 배열 원소를 List로 꺼내서 쓴다. */
fun JsonNode.arr(): List<JsonNode> = values().toList()
