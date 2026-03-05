package pingpong.backend.domain.flow.enums;

public enum BulkCompleteResultStatus {
	COMPLETE,        // 이번 호출로 COMPLETE 처리됨
	ALREADY_COMPLETE,// 이미 COMPLETE라서 그대로 반환(멱등)
	FAILED           // 검증 실패(사유는 errorCode/errorMessage)
}
