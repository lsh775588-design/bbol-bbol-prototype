package com.bbolbbol.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    private static final Map<String, HttpStatus> SPEC = new LinkedHashMap<>();
    private static final Map<String, HttpStatus> EXTRA = new LinkedHashMap<>();

    static {
        SPEC.put("COMMON400", HttpStatus.BAD_REQUEST);
        SPEC.put("AUTH401", HttpStatus.UNAUTHORIZED);
        SPEC.put("AUTH4011", HttpStatus.UNAUTHORIZED);
        SPEC.put("AUTH4012", HttpStatus.UNAUTHORIZED);
        SPEC.put("KAKAO401", HttpStatus.UNAUTHORIZED);
        SPEC.put("KAKAO502", HttpStatus.BAD_GATEWAY);
        SPEC.put("USER404", HttpStatus.NOT_FOUND);
        SPEC.put("USER409", HttpStatus.CONFLICT);
        SPEC.put("USER4001", HttpStatus.BAD_REQUEST);
        SPEC.put("USER4002", HttpStatus.BAD_REQUEST);
        SPEC.put("USER4092", HttpStatus.CONFLICT);
        SPEC.put("USER4093", HttpStatus.CONFLICT);
        SPEC.put("EMAIL4001", HttpStatus.BAD_REQUEST);
        SPEC.put("EMAIL4002", HttpStatus.BAD_REQUEST);
        SPEC.put("EMAIL4003", HttpStatus.BAD_REQUEST);
        SPEC.put("EMAIL429", HttpStatus.TOO_MANY_REQUESTS);
        SPEC.put("EMAIL502", HttpStatus.BAD_GATEWAY);
        SPEC.put("PLACE404", HttpStatus.NOT_FOUND);
        SPEC.put("COMPLETION4001", HttpStatus.BAD_REQUEST);
        SPEC.put("COMPLETION4002", HttpStatus.BAD_REQUEST);
        SPEC.put("COMPLETION4003", HttpStatus.BAD_REQUEST);
        SPEC.put("COMPLETION4004", HttpStatus.BAD_REQUEST);
        SPEC.put("COMPLETION403", HttpStatus.FORBIDDEN);
        SPEC.put("COMPLETION404", HttpStatus.NOT_FOUND);
        SPEC.put("COMPLETION409", HttpStatus.CONFLICT);
        SPEC.put("COMPLETION4091", HttpStatus.CONFLICT);
        SPEC.put("FILE4001", HttpStatus.BAD_REQUEST);
        SPEC.put("FILE413", HttpStatus.PAYLOAD_TOO_LARGE);

        EXTRA.put("COMMON404", HttpStatus.NOT_FOUND);
        EXTRA.put("COMMON405", HttpStatus.METHOD_NOT_ALLOWED);
        EXTRA.put("COMMON415", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        EXTRA.put("COMMON500", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static Map<String, ErrorCode> byCode() {
        return Arrays.stream(ErrorCode.values())
                .collect(Collectors.toMap(ErrorCode::getCode, e -> e));
    }

    @Test
    @DisplayName("명세 코드 27개가 정확한 코드 문자열과 HTTP 상태로 존재한다")
    void specCodes() {
        Map<String, ErrorCode> map = byCode();
        SPEC.forEach((code, status) -> {
            assertThat(map).as(code).containsKey(code);
            assertThat(map.get(code).getStatus()).as(code).isEqualTo(status);
        });
    }

    @Test
    @DisplayName("명세 밖 추가 코드 COMMON404/405/415/500")
    void extraCodes() {
        Map<String, ErrorCode> map = byCode();
        EXTRA.forEach((code, status) -> {
            assertThat(map).as(code).containsKey(code);
            assertThat(map.get(code).getStatus()).as(code).isEqualTo(status);
        });
    }

    @Test
    @DisplayName("enum에는 명세 목록과 추가 4개 외의 코드가 없다")
    void noUnexpectedCodes() {
        Set<String> expected = new HashSet<>(SPEC.keySet());
        expected.addAll(EXTRA.keySet());
        assertThat(ErrorCode.values()).hasSize(expected.size());
        assertThat(byCode().keySet()).isEqualTo(expected);
    }

    @Test
    @DisplayName("불규칙한 번호는 명세 그대로 보존(USER4091 없음, USER4092, COMPLETION403)")
    void irregularNumbering() {
        Map<String, ErrorCode> map = byCode();
        assertThat(map).doesNotContainKey("USER4091");
        assertThat(map.get("USER4092").getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(map.get("USER4093").getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(map.get("COMPLETION403").getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(map.get("COMPLETION409").getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(map.get("COMPLETION4091").getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("코드 문자열은 유일하고 enum 이름과 같으며 메시지는 비어 있지 않다")
    void uniqueAndNonBlank() {
        Set<String> codes = Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode)
                .collect(Collectors.toSet());
        assertThat(codes).hasSize(ErrorCode.values().length);
        for (ErrorCode ec : ErrorCode.values()) {
            assertThat(ec.getCode()).isEqualTo(ec.name());
            assertThat(ec.getMessage()).as(ec.name()).isNotBlank();
            assertThat(ec.getStatus()).isNotNull();
        }
    }
}
