package pingpong.backend.domain.qa.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pingpong.backend.domain.swagger.dto.response.ApiExecuteResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.qa.dto.QaCaseResponse;
import pingpong.backend.domain.qa.service.QaService;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/qa")
@Tag(name = "QA API", description = "Endpoint에 대한 QA 케이스를 조회하는 API입니다.")
public class QaController {

	private final QaService qaService;

	@GetMapping
	@Operation(
		summary = "Endpoint별 QA 목록 조회",
		description = "endpointId에 해당하는 QA 케이스 목록을 반환합니다."
	)
	public SuccessResponse<List<QaCaseResponse>> getQaCases(@RequestParam Long endpointId) {
		return SuccessResponse.ok(qaService.getQaCasesByEndpointId(endpointId));
	}

	@PostMapping("/{qaId}/execute")
	@Operation(
		summary = "QA 케이스 실행",
		description = "qaId에 해당하는 QA 케이스를 실행합니다. 실행에 필요한 정보(pathVariables, queryParams, headers, body)는 QA 테이블에서 읽어오며, 실행 결과에 따라 isSuccess 필드가 자동으로 업데이트됩니다."
	)
	public SuccessResponse<ApiExecuteResponse> executeQaCase(
		@PathVariable Long qaId,
		@RequestHeader(value = "X-Proxy-Authorization", required = false) String proxyAuthorization
	) {
		return SuccessResponse.ok(qaService.executeQaCase(qaId, proxyAuthorization));
	}
}
