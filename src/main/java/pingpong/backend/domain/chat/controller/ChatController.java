package pingpong.backend.domain.chat.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pingpong.backend.domain.chat.dto.ChatRequest;
import pingpong.backend.domain.chat.dto.ChatResponse;
import pingpong.backend.domain.chat.dto.ChatStreamInitResponse;
import pingpong.backend.domain.chat.service.ChatService;
import pingpong.backend.domain.chat.service.ChatStreamService;
import pingpong.backend.domain.member.Member;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/chat")
@Tag(name = "채팅 API", description = "RAG 기반 채팅 API 입니다.")
public class ChatController {

    private final ChatService chatService;
    private final ChatStreamService chatStreamService;

    @Hidden
    @PostMapping("/test")
    @Operation(summary = "PM 채팅 응답 테스트 용도", description = "팀의 노션 문서를 기반으로 AI에게 질문하고, 답변을 스웨거에서도 확인할 수 있도록 텍스트로 답변이 돌아옵니다.")
    public SuccessResponse<ChatResponse> chat(
            @PathVariable Long teamId,
            @CurrentMember Member member,
            @RequestBody @Valid ChatRequest request
    ) {
        return SuccessResponse.ok(chatService.ask(teamId, request));
    }

    @PostMapping
    @Operation(summary = "PM 채팅 스트리밍 초기화", description = "팀의 노션 문서를 기반으로 AI 채팅 스트리밍을 초기화합니다. 반환된 streamId로 /stream 엔드포인트에 연결하세요.")
    public SuccessResponse<ChatStreamInitResponse> initChatStream(
            @PathVariable Long teamId,
            @CurrentMember Member member,
            @RequestBody @Valid ChatRequest request
    ) {
        chatStreamService.validateTeamAccess(teamId, member.getId());
        String streamId = chatStreamService.initializeStream(teamId, member.getId(), request.message());
        return SuccessResponse.ok(new ChatStreamInitResponse(streamId));
    }

    @GetMapping("/stream")
    @Operation(summary = "PM 채팅 스트리밍 수신",
            description = """
                    SSE를 통해 AI 응답을 실시간으로 스트리밍 받습니다.
                    ```bash
                    curl -N "http://localhost:8080/api/v1/teams/1/chat/stream?streamId={streamId}" \\
                      -H "Authorization: Bearer {jwt_token}"
                    ```
                    """)
    public SseEmitter streamChat(
            @PathVariable Long teamId,
            @CurrentMember Member member,
            @RequestParam String streamId
    ) {
        return chatStreamService.streamChat(teamId, member.getId(), streamId);
    }
}
