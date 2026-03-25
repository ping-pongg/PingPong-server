package pingpong.backend.domain.qa.dto;

import java.util.Map;

public record RequestData(
	String method,        // GET, POST, PUT, DELETE 등
	String url,           // 호출할 엔드포인트 경로
	Map<String,String> pathVariables,
	Map<String,String> queryParams,
	Map<String, String> headers, // 필요한 HTTP 헤더 (JSON 등)
	Object body     // 요청 본문 데이터 (JSON 객체 또는 배열)
) {}
