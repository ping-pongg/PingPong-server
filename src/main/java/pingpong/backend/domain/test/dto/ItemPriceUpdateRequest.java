package pingpong.backend.domain.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상품 가격 수정 요청")
public record ItemPriceUpdateRequest(
	@Schema(description = "변경할 가격(원)", example = "79000") Integer price
) {}
