package pingpong.backend.domain.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pingpong.backend.domain.team.enums.Role;

@Schema(description = "팀 생성 요청")
public record TeamCreateRequest(

        @NotBlank
        @Schema(description = "팀 이름", example = "PingPong Team", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "Figma 링크", example = "https://figma.com/file/xxxx", nullable = true)
        String figma,

        @Schema(description = "Discord 링크", example = "https://discord.gg/xxxx", nullable = true)
        String discord,

        @Schema(description = "Swagger 링크", example = "https://api.example.com/swagger-ui/index.html", nullable = true)
        String swagger,

        @Schema(description = "GitHub 링크", example = "https://github.com/org/repo", nullable = true)
        String github,

        @NotNull
        @Schema(
                description = "생성자의 팀 내 역할",
                example = "BACKEND",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = Role.class
        )
        Role creatorRole
) {}