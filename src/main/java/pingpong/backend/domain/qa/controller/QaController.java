package pingpong.backend.domain.qa.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.qa.dto.EndpointQaTagGroupResponse;
import pingpong.backend.domain.qa.dto.QaCaseDetailDto;
import pingpong.backend.domain.qa.dto.QaCaseSummaryDto;
import pingpong.backend.domain.qa.dto.QaExecuteResultDto;
import pingpong.backend.domain.qa.dto.QaScenarioDetail;
import pingpong.backend.domain.qa.dto.QaScenarioResponse;
import pingpong.backend.domain.qa.dto.QaTeamFailureResponse;
import pingpong.backend.domain.qa.service.QaService;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/qa")
@Tag(name = "QA API", description = "Endpoint에 대한 QA 케이스를 조회하는 API입니다.")
public class QaController {

	private final QaService qaService;

	@PostMapping("/{endpointId}/auto")
	@Operation(
		summary="[AI] 해당 Endpoint의 QA 시나리오 생성",
		description = "endpoint에 해당하는 QA 케이스들을 AI가 생성합니다."
	)
	public SuccessResponse<QaScenarioResponse> createQaCases(@PathVariable Long endpointId){
		return SuccessResponse.ok(qaService.createQaCases(endpointId));
	}

	@PostMapping("/{endpointId}/manual")
	@Operation(
		summary="유저가 직접 해당 Endpoint의 QA 시나리오 추가",
		description = "유저가 직접 endpoint에 해당하는 QA 케이스들을 추가합니다."
	)
	public SuccessResponse<Long> createManualQaCases(
		@PathVariable Long endpointId,@RequestBody QaScenarioDetail request
		){
		return SuccessResponse.ok(qaService.createManualQaCase(endpointId,request));
	}

	@GetMapping
	@Operation(
		summary = "QA 케이스 목록 조회",
		description = "특정 엔드포인트에 대한 QA 케이스 요약 목록을 반환합니다."
	)
	public SuccessResponse<List<QaCaseSummaryDto>> getQaCaseList(@RequestParam Long endpointId) {
		return SuccessResponse.ok(qaService.getQaCasesByEndpointId(endpointId));
	}

	@GetMapping("/{qaId}/results")
	@Operation(
		summary = "QA 케이스 단건 상세 조회",
		description = "QA 케이스의 상세 정보를 엔드포인트 스키마 및 실제 테스트 값과 함께 반환합니다."
	)
	public SuccessResponse<QaCaseDetailDto> getQaCaseDetail(@PathVariable Long qaId) {
		return SuccessResponse.ok(qaService.getQaCaseDetail(qaId));
	}

	@GetMapping("/tags")
	@Operation(
		summary = "Tag별 Endpoint QA 성공률 조회",
		description = "teamId에 해당하는 최신 스냅샷의 endpoint를 tag 기준으로 그룹핑하여 각 endpoint의 QA 성공률을 반환합니다."
	)
	public SuccessResponse<List<EndpointQaTagGroupResponse>> getEndpointsByTag(@RequestParam Long teamId) {
		return SuccessResponse.ok(qaService.getEndpointsByTag(teamId));
	}

	@PostMapping("/{qaId}/execute")
	@Operation(
		summary = "QA 케이스 실행",
		description = "qaId에 해당하는 QA 케이스를 실행합니다. 실행에 필요한 정보(pathVariables, queryParams, headers, body)는 QA 테이블에서 읽어오며, 실행 결과에 따라 isSuccess 필드가 자동으로 업데이트됩니다."
	)
	public SuccessResponse<QaExecuteResultDto> executeQaCase(
		@PathVariable Long qaId,
		@RequestHeader(value = "X-Proxy-Authorization", required = false) String proxyAuthorization
	) {
		return SuccessResponse.ok(qaService.executeQaCase(qaId, proxyAuthorization));
	}

	@GetMapping("/failures")
	@Operation(hidden = true)
	public SuccessResponse<List<QaTeamFailureResponse>> getTeamFailures(@RequestParam Long teamId) {
		return SuccessResponse.ok(qaService.getTeamFailures(teamId));
	}
}
