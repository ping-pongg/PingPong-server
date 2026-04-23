package pingpong.backend.domain.test.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
@Schema(description = "상품 정보 상세 응답")
public class ProductResponse {
    @Schema(description = "상품 고유 번호", example = "1024")
    private Long productId;

    @Schema(description = "상품명")
    private String title;

    @Schema(description = "판매 가격")
    private Integer price;

    @Schema(description = "현재 판매 가능 여부", example = "true")
    private Boolean isAvailable;
}
