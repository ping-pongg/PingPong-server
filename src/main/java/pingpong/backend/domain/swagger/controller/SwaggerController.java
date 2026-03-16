package pingpong.backend.domain.swagger.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.github.service.GithubService;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.qa.service.QaService;
import pingpong.backend.domain.swagger.dto.request.ApiExecuteRequest;
import pingpong.backend.domain.swagger.dto.response.ApiExecuteResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointDetailResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointSearchResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointDiffDetailResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointGroupResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointResponse;
import pingpong.backend.domain.swagger.dto.response.SyncResultResponse;
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
	private final GithubService githubService;
	private final QaService qaService;

	@Hidden
	@GetMapping("/api/v1/swagger/{teamId}")
	@Operation(summary = "swagger JSON 읽어오기", description = "현재 서버의 swagger docs를 불러옵니다.")
	public SuccessResponse<JsonNode> swaggerDiff(
		@PathVariable Long teamId
	) {
		return SuccessResponse.ok(swaggerService.readSwaggerDocs(teamId));
	}

	/**
	 * GET: 데이터 조회 (Query)
	 * 새로고침 없이 현재 DB에 저장된 최신 상태만 보고 싶을 때 사용합니다.
	 */
	@GetMapping("/{teamId}/latest")
	@Operation(summary = "Swagger 최신 결과 조회", description = "DB에 저장된 가장 최신 스냅샷 정보를 조회합니다.")
	public SuccessResponse<List<EndpointGroupResponse>> getLatest(
		@PathVariable Long teamId
	) {
		return SuccessResponse.ok(swaggerService.getLatestSnapshotGrouped(teamId));
	}

	@PostMapping("/api/v1/teams/{teamId}/sync-all")
	@Operation(
		summary = "팀 개발 자산(Swagger & GitHub) 통합 동기화",
		description = """
       팀에 등록된 외부 소스(Swagger Spec 및 GitHub Repository)의 최신 상태를 확인하고 변경사항을 DB에 기록합니다.
       
       [주요 프로세스]
       1. Swagger(OpenAPI): 최신 스펙 해시를 비교하여 변경 감지 시 Endpoint 정보를 정규화하여 업데이트합니다.
       2. GitHub: 최신 커밋 SHA를 확인하고, 해당 커밋 SHA를 저장합니다.
       
       ※ 이 API는 데이터를 갱신(Sync)하는 역할만 수행하며, 실제 변경 내역은 각 도메인의 Diff 조회 API(GET `/teams/{teamId}/github/sync-result`)를 통해 확인하시기 바랍니다.
       """
	)
	public SuccessResponse<SyncResultResponse> syncSwaggerAndCode(
		@PathVariable Long teamId,
		@CurrentMember Member currentMember
	) {
		boolean swaggerChanged=swaggerService.syncSwagger(teamId, currentMember);
		boolean githubChanged=githubService.syncGithubBranch(teamId);
		if(swaggerChanged){
			qaService.createQaCases;
		}
		return SuccessResponse.ok(SyncResultResponse.of(swaggerChanged, githubChanged));
	}

	@GetMapping("/api/v1/endpoints/diff/{endpointId}")
	@Operation(summary = "diff 포함 endpoint 상세 조회", description = "변경된 사항이 있는 경우 diff까지 함께 조회할 수 있도록 합니다. 변경 사항을 상세히 확인할 때 활용합니다.")
	public SuccessResponse<EndpointDiffDetailResponse> getEndpointDiffDetails(
		@PathVariable Long endpointId
	) {
		return SuccessResponse.ok(swaggerService.getEndpointDiffDetails(endpointId));
	}

	@GetMapping("/api/v1/endpoints/{endpointId}")
	@Operation(summary = "endpoint 상세 조회", description = "endpoint를 상세 조회 합니다. qa 엔드포인트 실행 시 활용합니다.")
	public SuccessResponse<EndpointDetailResponse> getEndpointDetails(
		@PathVariable Long endpointId
	) {
		return SuccessResponse.ok(swaggerService.getEndpointDetails(endpointId));
	}

	@GetMapping("/api/v1/endpoints")
	@Operation(
		summary = "팀에 속한 전체 엔드포인트 조회",
		description = "지정한 팀(프로젝트)에 등록된 모든 엔드포인트를 조회합니다. " +
			"각 엔드포인트의 태그, 경로, HTTP 메서드, 요약 정보 및 변경 여부를 포함하여 반환합니다."
	)
	public SuccessResponse<List<EndpointResponse>> getEndpointList(
		@RequestParam Long teamId
	) {
		return SuccessResponse.ok(endpointService.getEndpointList(teamId));
	}

	@GetMapping("/api/v1/endpoints/search")
	@Operation(summary = "엔드포인트 검색", description = "path에 검색어를 포함하는 엔드포인트를 조회합니다.")
	public SuccessResponse<List<EndpointSearchResponse>> searchEndpoints(
		@RequestParam Long teamId,
		@RequestParam String query
	) {
		return SuccessResponse.ok(endpointService.searchEndpoints(teamId, query));
	}

	@Hidden
	@GetMapping("/api/v1/endpoints/changed")
	@Operation(
		summary = "팀에 속한 전체 엔드포인트들 중 이번에 바뀐 엔드포인트만 조회",
		description = "지정한 팀(프로젝트)에서 변경된 엔드포인트를 조회합니다. " +
			"엔드포인트 매칭 화면에서 NEW 목록을 표시할 때 사용됩니다. "
	)
	public SuccessResponse<List<EndpointResponse>> getChangedEndpointList(
		@RequestParam Long teamId
	) {
		return SuccessResponse.ok(endpointService.getChangedEndpointList(teamId));
	}

	@PostMapping("/api/v1/endpoints/{endpointId}/execute")
	@Operation(
		summary = "엔드포인트 직접 실행",
		description = "지정한 엔드포인트를 팀 서버로 실제 HTTP 요청을 보내고 응답을 반환합니다."
	)
	public SuccessResponse<ApiExecuteResponse> executeEndpoint(
		@PathVariable Long endpointId,
		@RequestParam Long teamId,
		@RequestHeader(value = "X-Proxy-Authorization", required = false) String proxyAuthorization,
		@RequestBody ApiExecuteRequest request
	) {
		return SuccessResponse.ok(apiExecuteService.execute(endpointId, teamId, request, proxyAuthorization));
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

}
