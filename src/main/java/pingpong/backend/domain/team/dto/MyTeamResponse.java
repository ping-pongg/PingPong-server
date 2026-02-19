package pingpong.backend.domain.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.team.Team;

@Schema(description = "내가 참여 중인 팀 조회 응답")
public record MyTeamResponse(

        @Schema(description = "팀 ID", example = "1")
        Long teamId,

        @Schema(description = "팀 이름", example = "PingPong Team")
        String name,

        @Schema(description = "Figma 링크", example = "https://figma.com/file/xxxx", nullable = true)
        String figma,

        @Schema(description = "Discord 링크", example = "https://discord.gg/xxxx", nullable = true)
        String discord,

        @Schema(description = "Swagger 링크", example = "https://api.example.com/swagger-ui/index.html", nullable = true)
        String swagger,

        @Schema(description = "GitHub 링크", example = "https://github.com/org/repo", nullable = true)
        String github,

        @Schema(description = "팀 정보 업데이트 여부", example = "false")
        Boolean isUpdated
) {
    public static MyTeamResponse of(Team team) {
        return new MyTeamResponse(
                team.getId(),
                team.getName(),
                team.getFigma(),
                team.getDiscord(),
                team.getSwagger(),
                team.getGithub(),
                team.getIsUpdated()
        );
    }
}
