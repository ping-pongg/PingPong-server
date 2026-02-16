package pingpong.backend.domain.notion.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.notion.dto.NotionCreatePageRequest;
import pingpong.backend.domain.notion.dto.NotionPageUpdateRequest;
import pingpong.backend.domain.notion.dto.response.DatabaseCreatedResponse;
import pingpong.backend.domain.notion.dto.response.DatabaseWithPagesResponse;
import pingpong.backend.domain.notion.dto.response.PageDetailResponse;
import pingpong.backend.domain.notion.service.NotionFacade;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/notion")
@Tag(name = "Notion 리소스 API", description = "Notion 데이터베이스 조회 및 페이지 관리 API 입니다.")
public class NotionResourceController {

    private final NotionFacade notionFacade;

    @GetMapping("/databases/primary")
    @Operation(summary = "팀 데이터베이스의 전체 내용 조회", description = "팀에 설정된 대표 데이터베이스의 제목과 페이지 목록을 조회합니다.")
    public SuccessResponse<DatabaseWithPagesResponse> queryPrimaryDatabase(
            @PathVariable Long teamId,
            @CurrentMember Member member
    ) {
        return SuccessResponse.ok(notionFacade.queryPrimaryDatabase(teamId, member));
    }

    @PostMapping("/databases/primary")
    @Operation(
            summary = "팀 데이터베이스에 페이지 생성",
            description = """
                    팀에 설정된 대표 데이터베이스에 새 페이지를 생성합니다.

                    **요청 필드:**
                    - title: 페이지 제목 (필수)
                    - date: 날짜 범위 (선택) - start: 시작일, end: 종료일
                    - status: 상태 (선택)

                    **응답:**
                    생성된 페이지의 상세 정보 (id, title, date, status, pageContent, childDatabases)
                    """
    )
    public SuccessResponse<PageDetailResponse> createPageInPrimaryDatabase(
            @PathVariable Long teamId,
            @CurrentMember Member member,
            @RequestBody @Valid NotionCreatePageRequest request
    ) {
        return SuccessResponse.ok(notionFacade.createPageInPrimaryDatabase(teamId, member, request));
    }

    @PatchMapping("/pages/{pageId}")
    @Operation(summary = "페이지 수정", description = "제공된 필드(title, date, status)를 수정하고 상세 정보를 반환합니다.")
    public SuccessResponse<PageDetailResponse> updatePage(
            @PathVariable Long teamId,
            @PathVariable String pageId,
            @CurrentMember Member member,
            @RequestBody @Valid NotionPageUpdateRequest request
    ) {
        return SuccessResponse.ok(notionFacade.updatePage(teamId, member, pageId, request));
    }

    @GetMapping("/pages/{pageId}")
    @Operation(summary = "페이지 내용 전체 조회", description = "페이지의 속성, 본문 내용(paragraph), 자식 데이터베이스를 조회합니다.")
    public SuccessResponse<PageDetailResponse> getPageBlocks(
            @PathVariable Long teamId,
            @PathVariable String pageId,
            @CurrentMember Member member
    ) {
        return SuccessResponse.ok(notionFacade.getPageBlocks(teamId, member, pageId));
    }

    @PostMapping("/pages/{pageId}/databases")
    @Operation(summary = "페이지 하위에 고정된 구조의 데이터베이스 생성",
               description = "지정된 페이지 하위에 'API Status Overview' 데이터베이스를 생성합니다. (고정된 구조: Status(select), API List(title))")
    public SuccessResponse<DatabaseCreatedResponse> createDatabase(
            @PathVariable Long teamId,
            @PathVariable String pageId,
            @CurrentMember Member member
    ) {
        return SuccessResponse.ok(notionFacade.createDatabase(teamId, member, pageId));
    }
}
