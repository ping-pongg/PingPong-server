package pingpong.backend.domain.flow.enums;

/**
 * 이미지에 할당된 엔드포인트들에 대한 status
 */
public enum FlowEndpointLinkStatus {
	BACKEND_IN_PROGRESS,
	FRONTEND_IN_PROGRESS, //화면에 API 할당
	FULLY_INTEGRATED //연동 완료
}
