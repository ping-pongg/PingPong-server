package pingpong.backend.domain.swagger.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.swagger.dto.request.ApiExecuteRequest;
import pingpong.backend.domain.swagger.dto.response.ApiExecuteResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointDetailResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointGroupResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointStatusResponse;
import pingpong.backend.domain.swagger.service.ApiExecuteService;
import pingpong.backend.domain.swagger.service.EndpointService;
import pingpong.backend.domain.swagger.service.SwaggerService;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@Tag(name = "Swagger API", description = "Swagger JSON 동기화 및 엔드포인트 관리 API입니다.")
public class SwaggerController {

	private final SwaggerService swaggerService;
	private final ApiExecuteService apiExecuteService;
	private final EndpointService endpointService;

	@Hidden
	@GetMapping("/api/v1/swagger/{teamId}")
	@Operation(summary = "swagger JSON 읽어오기", description = "현재 서버의 swagger docs를 불러옵니다.")
	public SuccessResponse<JsonNode> swaggerDiff(
		@PathVariable Long teamId
	) {
		return SuccessResponse.ok(swaggerService.readSwaggerDocs(teamId));
	}

	@PostMapping("/api/v1/swagger/{teamId}/sync")
	@Operation(
		summary = "swagger JSON 정규화해서 DB에 저장",
		description = """
        지정한 서버의 Swagger(OpenAPI) 문서를 조회하여 최신 스냅샷과 비교합니다.
        스펙 해시가 동일한 경우에는 별도의 저장 없이 빈 결과를 반환합니다.

        스펙에 변경이 감지되면 다음 작업을 수행합니다:
        - endpoint, request, response, parameter 정보를 정규화하여 저장
        - 이전 스냅샷과 비교하여 endpoint 단위 변경 여부 판별
        - 신규/수정/삭제된 endpoint를 ChangeType 기준으로 구분

        최종적으로 변경이 발생한 endpoint만 controller(tag) 단위로 그룹화하여 반환합니다.
        """
	)
	public SuccessResponse<List<EndpointGroupResponse>> compareAndSaveSwagger(
		@PathVariable Long teamId,
		@CurrentMember Member currentMember
	) {
		return SuccessResponse.ok(swaggerService.syncSwagger(teamId, currentMember));
	}

	@GetMapping("/api/v1/endpoints/{endpointId}")
	@Operation(summary = "endpoint 상세 조회 (미완성)", description = "변경된 사항이 있는 경우 diff까지 함께 조회할 수 있도록 합니다.")
	public SuccessResponse<EndpointDetailResponse> getEndpointDetails(
		@PathVariable Long endpointId
	) {
		return SuccessResponse.ok(swaggerService.getEndpointDetails(endpointId));
	}

	@GetMapping("/api/v1/endpoints")
	@Operation(
		summary = "팀에 속한 전체 엔드포인트 조회",
		description = "지정한 팀(프로젝트)에 등록된 모든 엔드포인트를 조회합니다. " +
			"엔드포인트 매칭 화면 및 검색 화면에서 전체(ALL) 목록을 표시할 때 사용됩니다. " +
			"각 엔드포인트의 태그, 경로, HTTP 메서드, 요약 정보 및 변경 여부를 포함하여 반환합니다."
	)
	public SuccessResponse<List<EndpointResponse>> getEndpointList(
		@RequestParam Long teamId
	) {
		return SuccessResponse.ok(endpointService.getEndpointList(teamId));
	}

	@GetMapping("/api/v1/endpoints/changed")
	@Operation(
		summary = "팀에 속한 전체 엔드포인트들 중 이번에 바뀐 엔드포인트만 조회(미완성)",
		description = "지정한 팀(프로젝트)에서 변경된 엔드포인트를 조회합니다. " +
			"엔드포인트 매칭 화면에서 NEW 목록을 표시할 때 사용됩니다. "
	)
	public SuccessResponse<List<EndpointResponse>> getChangedEndpointList(
		@RequestParam Long teamId
	) {
		return SuccessResponse.ok(endpointService.getEndpointList(teamId));
	}

	@PostMapping("/api/v1/endpoints/{endpointId}/execute")
	@Operation(
		summary = "엔드포인트 직접 실행",
		description = "지정한 엔드포인트를 팀 서버로 실제 HTTP 요청을 보내고 응답을 반환합니다."
	)
	public SuccessResponse<ApiExecuteResponse> executeEndpoint(
		@PathVariable Long endpointId,
		@RequestParam Long teamId,
		@RequestBody ApiExecuteRequest request
	) {
		return SuccessResponse.ok(apiExecuteService.execute(endpointId, teamId, request));
	}

	@PatchMapping("/api/v1/flow-images/{flowImageId}/endpoints/{endpointId}/complete")
	@Operation(summary = "엔드포인트 연동 상태 완료로 변경", description = "FE가 해당 엔드포인트를 연동 완료 시 호출하는 API입니다.")
	public SuccessResponse<Boolean> completeEndpoint(
		@PathVariable Long flowImageId,
		@PathVariable Long endpointId,
		@CurrentMember Member member
	) {
		endpointService.completeEndpoint(flowImageId, endpointId, member);
		return SuccessResponse.ok();
	}

	@GetMapping("/api/v1/pm/progress/{endpointId}")
	@Operation(summary = "PM입장에서 엔드포인트 별 연동 상태 조회")
	public SuccessResponse<EndpointStatusResponse> getEndpointStatus(
		@PathVariable Long endpointId
	){
		return SuccessResponse.ok(endpointService.getEndpointStatus(endpointId));
	}
}
