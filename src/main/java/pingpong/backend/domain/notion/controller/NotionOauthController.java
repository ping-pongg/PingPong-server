package pingpong.backend.domain.notion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.notion.dto.NotionConnectDatabaseRequest;
import pingpong.backend.domain.notion.dto.NotionOAuthExchangeRequest;
import pingpong.backend.domain.notion.dto.NotionOAuthExchangeResponse;
import pingpong.backend.domain.notion.service.NotionConnectionApiService;
import pingpong.backend.domain.notion.service.NotionConnectionService;
import pingpong.backend.domain.notion.service.NotionFacade;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/notion")
@Tag(name = "Notion 연결 API", description = "Notion OAuth 인증 및 데이터베이스 연결 관리 API")
public class NotionOauthController {

    private final NotionFacade notionFacade;
    private final NotionConnectionService notionConnectionService;
    private final NotionConnectionApiService notionConnectionApiService;

    @PostMapping("/token")
    @Operation(
            summary = "Notion OAuth 코드 교환",
            description = """
                    code/redirectUri로 토큰을 교환하고 팀에 저장합니다. 대표 데이터베이스가 비어있으면 내부적으로 1회 resolve를 시도합니다.
                    https://api.notion.com/v1/oauth/authorize?client_id=2f5d872b-594c-80cc-a32b-003748ffcba9&response_type=code&owner=user&redirect_uri=https%3A%2F%2Fpingpong-team.vercel.app
                    """
    )
    public SuccessResponse<NotionOAuthExchangeResponse> createConnection(
            @PathVariable Long teamId,
            @CurrentMember Member member,
            @RequestBody @Valid NotionOAuthExchangeRequest request
    ) {
        return SuccessResponse.ok(
                notionFacade.exchangeCodeAndPersist(teamId, member, request.code(), request.redirectUri())
        );
    }

    @PostMapping("/token/refresh")
    @Operation(
            summary = "Notion 토큰 갱신",
            description = "access_token 만료 시 저장된 refresh_token으로 토큰을 갱신합니다."
    )
    public SuccessResponse<Void> refreshConnection(
            @PathVariable Long teamId,
            @CurrentMember Member member
    ) {
        notionFacade.refreshTokens(teamId, member);
        return SuccessResponse.ok(null);
    }

    @GetMapping("/databases")
    @Operation(summary = "후보 데이터베이스 목록 조회", description = "팀의 Notion 워크스페이스에서 사용 가능한 데이터베이스 목록을 조회합니다.")
    public SuccessResponse<JsonNode> listCandidateDatabases(
            @PathVariable Long teamId,
            @CurrentMember Member member
    ) {
        notionConnectionService.assertTeamAccess(teamId, member);
        return SuccessResponse.ok(notionConnectionApiService.listCandidateDatabases(teamId));
    }

    @PutMapping("/databases/primary")
    @Operation(summary = "대표 데이터베이스 설정", description = "팀의 대표 Notion 데이터베이스를 설정합니다.")
    public SuccessResponse<Void> setPrimaryDatabase(
            @PathVariable Long teamId,
            @CurrentMember Member member,
            @RequestBody @Valid NotionConnectDatabaseRequest request
    ) {
        notionConnectionService.assertTeamAccess(teamId, member);
        notionConnectionApiService.connectDatabase(teamId, request.databaseId());
        return SuccessResponse.ok(null);
    }
}
