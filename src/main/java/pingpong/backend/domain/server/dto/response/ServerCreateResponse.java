package pingpong.backend.domain.server.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "서버 생성 응답")
public record ServerCreateResponse(

	@Schema(description = "생성된 서버 ID", example = "1")
	Long serverId
) {}
