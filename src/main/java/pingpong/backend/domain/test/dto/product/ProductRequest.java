package pingpong.backend.domain.test.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "상품 등록 및 수정 요청")
public class ProductRequest {
    @Schema(description = "상품명", example = "고성능 게이밍 마우스")
    @NotBlank(message = "상품명은 필수입니다.")
    private String title;

    @Schema(description = "상품 카테고리", example = "ELECTRONICS")
    private String category;

    @Schema(description = "가격", example = "45000")
    @Min(0)
    private Integer price;

    @Schema(description = "재고 수량", example = "100")
    private Integer stockQuantity;

    @Schema(description = "바보",example="13")
    private Integer babo;
}