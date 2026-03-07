package pingpong.backend.domain.qa.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record QaTeamFailureResponse(
        Long caseId,
        Long endpointId,
        String endpointPath,
        String method,
        String tag,
        String description,
        Map<String, String> pathVariables,
        Map<String, String> queryParams,
        Map<String, String> headers,
        Object body,
        LatestResult latestResult
) {
    public record LatestResult(
            Integer httpStatus,
            Object responseBody,
            Long durationMs,
            LocalDateTime executedAt
    ) {}
}
