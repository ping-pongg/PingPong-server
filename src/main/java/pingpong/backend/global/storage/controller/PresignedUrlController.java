package pingpong.backend.global.storage.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import pingpong.backend.global.response.result.SuccessResponse;
import pingpong.backend.global.storage.dto.request.PresignedUrlRequest;
import pingpong.backend.global.storage.dto.response.PresignedUrlResponse;
import pingpong.backend.global.storage.service.PresignedUrlService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/s3")
@Tag(name="presigend URL 발급 API", description = "이미지 업로드 시 사용하는 API입니다.")
public class PresignedUrlController {

	private final PresignedUrlService presignedUrlService;

	@PostMapping("/post-url")
	@Operation(summary="이미지 업로드용 presigned url 생성")
	public SuccessResponse<PresignedUrlResponse> createPresignedUrl(
		@RequestBody @Valid PresignedUrlRequest request
	){
		PresignedUrlResponse response=presignedUrlService.getPostS3Url(request);
		return SuccessResponse.ok(response);
	}

	@GetMapping("/get-url")
	@Operation(summary="이미지 조회용 presigned url 조회")
	public SuccessResponse<PresignedUrlResponse> getPresignedUrl(
		@RequestParam String imagePath
	){
		PresignedUrlResponse response=presignedUrlService.getGetS3Url(imagePath);
		return SuccessResponse.ok(response);
	}

	@DeleteMapping("/image")
	@Operation(summary = "S3 이미지 삭제 요청")
	public SuccessResponse<Void> deleteImage(
		@RequestParam String imagePath
	) {
		presignedUrlService.deleteImageByPath(imagePath);
		return SuccessResponse.ok(null);
	}


}
