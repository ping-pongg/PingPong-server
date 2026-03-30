package pingpong.backend.domain.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상품 전체 수정 요청")
public record ItemUpdateRequest(
	@Schema(description = "상품명", example = "무선 키보드 v2") String name,
	@Schema(description = "상품 설명", example = "저소음 무선 기계식 키보드 2세대") String description,
	@Schema(description = "가격(원)", example = "99000") Integer price,
	@Schema(description = "재고 수량", example = "200") Integer stock
) {}
