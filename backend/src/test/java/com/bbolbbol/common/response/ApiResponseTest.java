package com.bbolbbol.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbolbbol.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    private final ObjectMapper om = new ObjectMapper();

    private JsonNode json(Object o) {
        return om.valueToTree(o);
    }

    private List<String> fields(JsonNode n) {
        List<String> names = new ArrayList<>();
        n.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    @DisplayName("success: COMMON200, 필드는 success/code/message/data 네 개뿐")
    void success() {
        JsonNode n = json(ApiResponse.success(Map.of("k", 1)));
        assertThat(fields(n)).containsExactlyInAnyOrder("success", "code", "message", "data");
        assertThat(n.get("success").asBoolean()).isTrue();
        assertThat(n.get("code").asText()).isEqualTo("COMMON200");
        assertThat(n.get("message").asText()).isNotBlank();
        assertThat(n.get("data").get("k").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("created: COMMON201")
    void created() {
        JsonNode n = json(ApiResponse.created("x"));
        assertThat(n.get("success").asBoolean()).isTrue();
        assertThat(n.get("code").asText()).isEqualTo("COMMON201");
        assertThat(n.get("data").asText()).isEqualTo("x");
    }

    @Test
    @DisplayName("error: 코드와 기본 메시지, data 전달")
    void error() {
        JsonNode n = json(ApiResponse.error(ErrorCode.COMMON400, Map.of("a", "b")));
        assertThat(n.get("success").asBoolean()).isFalse();
        assertThat(n.get("code").asText()).isEqualTo("COMMON400");
        assertThat(n.get("message").asText()).isEqualTo(ErrorCode.COMMON400.getMessage());
        assertThat(n.get("data").get("a").asText()).isEqualTo("b");
    }

    @Test
    @DisplayName("data가 null이어도 data 키는 유지되고 값은 null (기본 ObjectMapper)")
    void nullDataKeepsKey() {
        for (JsonNode n : List.of(json(ApiResponse.error(ErrorCode.AUTH401)),
                json(ApiResponse.success(null)), json(ApiResponse.created(null)))) {
            assertThat(n.has("data")).isTrue();
            assertThat(n.get("data").isNull()).isTrue();
            assertThat(fields(n)).hasSize(4);
        }
    }

    @Test
    @DisplayName("error(ErrorCode) 단일 인자는 data null")
    void errorWithoutData() {
        ApiResponse<Void> r = ApiResponse.error(ErrorCode.PLACE404);
        assertThat(r.data()).isNull();
        assertThat(r.success()).isFalse();
        assertThat(r.code()).isEqualTo("PLACE404");
    }
}
