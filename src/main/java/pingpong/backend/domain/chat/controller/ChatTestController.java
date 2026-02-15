package pingpong.backend.domain.chat.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/chat")
@Tag(name = "채팅 API", description = "채팅 관련 API 입니다.")
public class ChatTestController {

    private final ChatClient chatClient;

    public ChatTestController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/test")
    public String ping(@RequestParam(defaultValue = "한 문장으로 자기소개 해줘") String q) {
        return chatClient.prompt()
                .user(q)
                .call()
                .content();
    }
}
