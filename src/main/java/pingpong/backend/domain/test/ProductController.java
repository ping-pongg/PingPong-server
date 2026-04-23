package pingpong.backend.domain.test;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pingpong.backend.domain.test.dto.product.ProductRequest;
import pingpong.backend.domain.test.dto.product.ProductResponse;

import java.time.LocalDateTime;

@Tag(name = "Product API", description = "상품 카탈로그 관리 기능을 제공합니다.")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    @Operation(summary = "상품 등록", description = "새로운 상품을 시스템에 등록합니다.")
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        // 실제 구현 대신 Mock 데이터 반환
        ProductResponse response = ProductResponse.builder()
                .productId(101L)
                .title(request.getTitle())
                .price(request.getPrice())
                .isAvailable(true)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "상품 상세 조회", description = "ID를 사용하여 특정 상품의 정보를 가져옵니다.")
    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ProductResponse.builder()
                .productId(productId)
                .title("기본 샘플 상품")
                .price(10000)
                .isAvailable(true)
                .build());
    }

    @Operation(summary = "상품 정보 업데이트", description = "기존 상품의 가격이나 재고를 수정합니다.")
    @PatchMapping("/{productId}")
    public ResponseEntity<Void> updateProduct(
            @PathVariable Long productId,
            @RequestBody ProductRequest request) {
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "상품 판매 중단", description = "시스템에서 상품을 삭제 처리하거나 비활성화합니다.")
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "재고 부족 알림 신청", description = "상품이 품절되었을 때 재입고 알림을 신청합니다.")
    @PostMapping("/{productId}/notifications")
    public ResponseEntity<String> registerStockNotification(
            @PathVariable Long productId,
            @Schema(description = "알림을 받을 사용자 이메일", example = "user@example.com")
            @RequestParam String email,
            @Schema(description = "알림 우선순위 (HIGH, LOW)", example = "HIGH")
            @RequestParam(defaultValue = "LOW") String priority) {

        // Mock 응답
        return ResponseEntity.ok(email + "님, 상품 " + productId + "번에 대한 알림 등록이 완료되었습니다.");
    }
}
