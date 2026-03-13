package pingpong.backend.domain.eval.dto;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * QA 시나리오 생성이 완료되었음을 알리는 이벤트
 */
public record QaEvalEvent(
	Long endpointId,
	String swaggerSpecJson,    // 입력된 명세 데이터
	String generatedRawJson,   // AI가 내뱉은 원본 JSON
	ChatResponse chatResponse, // 토큰 및 메타데이터 정보
	long latencyMs             // 생성에 걸린 시간
) {}