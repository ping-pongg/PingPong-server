package pingpong.backend.domain.qa.dto;

import java.util.Map;

public record ExpectedResponse(

	int statusCode, // 예상 HTTP 상태 코드 (200, 400 등)

	Map<String, Object> bodyFields // 검증해야 할 핵심 필드와 예상값

) {}