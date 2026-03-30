package pingpong.backend.domain.test;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import pingpong.backend.domain.test.dto.ItemCreateRequest;
import pingpong.backend.domain.test.dto.ItemPriceUpdateRequest;
import pingpong.backend.domain.test.dto.ItemResponse;
import pingpong.backend.domain.test.dto.ItemUpdateRequest;

@Tag(name = "상품 API", description = "Endpoint의 diff 계산을 시연하기 위해 예시로 구현한 API입니다.")
@RestController
@RequestMapping("/api/v1/item")
public class TestController {

	// =========================
	// GET: 상품 조회
	// =========================
	@GetMapping("/{itemId}")
	@Operation(
		summary = "상품 조회",
		description = "상품 ID로 단건 조회합니다.",
		responses = {
			@ApiResponse(
				responseCode = "200",
				description = "조회 성공",
				content = @Content(mediaType = "application/json",
					schema = @Schema(implementation = ItemResponse.class))
			),
			@ApiResponse(responseCode = "404", description = "상품 없음")
		}
	)
	public ResponseEntity<ItemResponse> getItem(
		@PathVariable
		@Parameter(description = "상품 ID", required = true, example = "1")
		Long itemId
	) {
		if (itemId < 0) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		ItemResponse res = new ItemResponse(
			itemId, "무선 키보드", "저소음 무선 기계식 키보드",
			89000, 150, Instant.now().toString()
		);
		return ResponseEntity.ok(res);
	}

	// =========================
	// POST: 상품 등록
	// =========================
	@PostMapping
	@Operation(
		summary = "상품 등록",
		description = "새 상품을 등록합니다.",
		responses = {
			@ApiResponse(
				responseCode = "201",
				description = "등록 성공",
				content = @Content(mediaType = "application/json",
					schema = @Schema(implementation = ItemResponse.class))
			)
		}
	)
	public ResponseEntity<ItemResponse> createItem(
		@RequestBody
		@io.swagger.v3.oas.annotations.parameters.RequestBody(
			description = "상품 등록 요청 본문",
			required = true,
			content = @Content(schema = @Schema(implementation = ItemCreateRequest.class))
		)
		ItemCreateRequest request
	) {
		ItemResponse res = new ItemResponse(
			1L, request.name(), request.description(),
			request.price(), request.stock(), Instant.now().toString()
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(res);
	}

	// =========================
	// PUT: 상품 전체 수정
	// =========================
	@PutMapping("/{itemId}")
	@Operation(
		summary = "상품 전체 수정",
		description = "상품의 모든 필드를 새 값으로 교체합니다.",
		responses = {
			@ApiResponse(
				responseCode = "200",
				description = "수정 성공",
				content = @Content(mediaType = "application/json",
					schema = @Schema(implementation = ItemResponse.class))
			),
			@ApiResponse(responseCode = "404", description = "상품 없음")
		}
	)
	public ResponseEntity<ItemResponse> updateItem(
		@PathVariable
		@Parameter(description = "상품 ID", required = true, example = "1")
		Long itemId,

		@RequestBody
		@io.swagger.v3.oas.annotations.parameters.RequestBody(
			description = "상품 전체 수정 요청 본문",
			required = true,
			content = @Content(schema = @Schema(implementation = ItemUpdateRequest.class))
		)
		ItemUpdateRequest request
	) {
		if (itemId < 0) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		ItemResponse res = new ItemResponse(
			itemId, request.name(), request.description(),
			request.price(), request.stock(), Instant.now().toString()
		);
		return ResponseEntity.ok(res);
	}

	// =========================
	// PATCH: 상품 가격 수정
	// =========================
	@PatchMapping("/{itemId}/price")
	@Operation(
		summary = "상품 가격 수정",
		description = "상품의 가격만 부분 수정합니다.",
		responses = {
			@ApiResponse(
				responseCode = "200",
				description = "수정 성공",
				content = @Content(mediaType = "application/json",
					schema = @Schema(implementation = ItemResponse.class))
			),
			@ApiResponse(responseCode = "404", description = "상품 없음")
		}
	)
	public ResponseEntity<ItemResponse> updateItemPrice(
		@PathVariable
		@Parameter(description = "상품 ID", required = true, example = "1")
		Long itemId,

		@RequestBody
		@io.swagger.v3.oas.annotations.parameters.RequestBody(
			description = "상품 가격 수정 요청 본문",
			required = true,
			content = @Content(schema = @Schema(implementation = ItemPriceUpdateRequest.class))
		)
		ItemPriceUpdateRequest request
	) {
		if (itemId < 0) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		ItemResponse res = new ItemResponse(
			itemId, "무선 키보드", "저소음 무선 기계식 키보드",
			request.price(), 150, Instant.now().toString()
		);
		return ResponseEntity.ok(res);
	}
}
