package pingpong.backend.domain.notion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.notion.dto.NotionCreateDatabaseRequest;
import pingpong.backend.domain.notion.dto.NotionCreatePageRequest;
import pingpong.backend.domain.notion.dto.NotionPageUpdateRequest;
import pingpong.backend.domain.notion.service.NotionFacade;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/notion")
@Tag(name = "Notion 리소스 API", description = "Notion 데이터베이스 조회 및 페이지 관리 API")
public class NotionResourceController {

    private final NotionFacade notionFacade;

    @GetMapping("/databases/primary")
    @Operation(summary = "팀 데이터베이스의 전체 내용 조회", description = "팀에 설정된 대표 데이터베이스를 타임스탬프 기반 필터/정렬로 조회합니다.")
    public SuccessResponse<JsonNode> queryPrimaryDatabase(
            @PathVariable Long teamId,
            @CurrentMember Member member
    ) {
        return SuccessResponse.ok(notionFacade.queryPrimaryDatabase(teamId, member));
    }

    @PostMapping("/databases/primary")
    @Operation(summary = "팀 데이터베이스에 페이지 추가 생성", description = "팀에 설정된 대표 데이터베이스에 새 페이지를 생성합니다.")
    public SuccessResponse<JsonNode> createPageInPrimaryDatabase(
            @PathVariable Long teamId,
            @CurrentMember Member member,
            @RequestBody @Valid NotionCreatePageRequest request
    ) {
        return SuccessResponse.ok(notionFacade.createPageInPrimaryDatabase(teamId, member, request));
    }

    @PatchMapping("/pages/{pageId}")
    @Operation(summary = "페이지 수정", description = "제공된 필드(title, date, status)만 수정합니다.")
    public SuccessResponse<JsonNode> updatePage(
            @PathVariable Long teamId,
            @PathVariable String pageId,
            @CurrentMember Member member,
            @RequestBody @Valid NotionPageUpdateRequest request
    ) {
        return SuccessResponse.ok(notionFacade.updatePage(teamId, member, pageId, request));
    }

    @GetMapping("/pages/{pageId}")
    @Operation(summary = "페이지 내용 전체 조회", description = "페이지의 하위 블록(콘텐츠)과 child_database 조회 결과를 함께 반환합니다. deep=true 시 최대 4단계 재귀 로딩합니다.")
    public SuccessResponse<JsonNode> getPageBlocks(
            @PathVariable Long teamId,
            @PathVariable String pageId,
            @RequestParam(value = "page_size", required = false) Integer pageSize,
            @RequestParam(value = "start_cursor", required = false) String startCursor,
            @RequestParam(value = "deep", required = false, defaultValue = "false") boolean deep,
            @CurrentMember Member member
    ) {
        return SuccessResponse.ok(notionFacade.getPageBlocks(teamId, member, pageId, pageSize, startCursor, deep));
    }

    @PostMapping("/pages/{pageId}/databases")
    @Operation(summary = "페이지 하위에 데이터베이스 생성", description = "지정된 페이지 하위에 새 데이터베이스를 생성합니다.")
    public SuccessResponse<JsonNode> createDatabase(
            @PathVariable Long teamId,
            @PathVariable String pageId,
            @CurrentMember Member member,
            @RequestBody @Valid NotionCreateDatabaseRequest request
    ) {
        return SuccessResponse.ok(notionFacade.createDatabase(teamId, member, pageId, request));
    }
}
