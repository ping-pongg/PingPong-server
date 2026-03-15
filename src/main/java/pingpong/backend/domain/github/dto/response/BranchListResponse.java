package pingpong.backend.domain.github.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description="github repo branch list를 조회합니다.")
public record BranchListResponse(
	List<BranchItem> branches
){
	public record BranchItem(String name,String sha){}
}
