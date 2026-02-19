package pingpong.backend.domain.swagger.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "swagger snapshot 생성 응답")
public record SwaggerSnapshotResponse(

	@Schema
	Long snapshotId,

	@Schema
	Long serverId,

	@Schema
	LocalDateTime createdAt,

	@Schema
	String specHash,

	@Schema
	int endpointCount
){

}
