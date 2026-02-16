package pingpong.backend.domain.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pingpong.backend.domain.chat.dto.ChatRequest;
import pingpong.backend.domain.chat.dto.ChatResponse;
import pingpong.backend.domain.chat.service.ChatService;
import pingpong.backend.domain.member.Member;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/chat")
@Tag(name = "채팅 API", description = "RAG 기반 채팅 API 입니다.")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @Operation(summary = "PM 채팅", description = "팀의 노션 문서를 기반으로 AI에게 질문합니다.")
    public SuccessResponse<ChatResponse> chat(
            @PathVariable Long teamId,
            @CurrentMember Member member,
            @RequestBody @Valid ChatRequest request
    ) {
        return SuccessResponse.ok(chatService.ask(teamId, request));
    }
}
