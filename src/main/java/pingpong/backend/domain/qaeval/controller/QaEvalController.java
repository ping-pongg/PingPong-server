package pingpong.backend.domain.qaeval.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.qaeval.dto.QaEvalSummaryResponse;
import pingpong.backend.domain.qaeval.dto.TeamItem;
import pingpong.backend.domain.qaeval.service.QaEvalService;
import pingpong.backend.global.response.result.SuccessResponse;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/qa")
public class QaEvalController {

	private final QaEvalService qaEvalService;

	@GetMapping("/summary")
	public SuccessResponse<QaEvalSummaryResponse> getSummary(
		@RequestParam(required = false) Long teamId
	) {
		return SuccessResponse.ok(qaEvalService.buildSummary(teamId));
	}

	@GetMapping("/teams")
	public SuccessResponse<List<TeamItem>> getTeams() {
		return SuccessResponse.ok(qaEvalService.getAllTeams());
	}
}
