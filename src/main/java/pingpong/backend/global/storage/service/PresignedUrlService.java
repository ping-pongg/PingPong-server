package pingpong.backend.global.storage.service;

import java.net.URL;
import java.time.Duration;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.global.storage.dto.ImageUploadType;
import pingpong.backend.global.storage.dto.request.PresignedUrlRequest;
import pingpong.backend.global.storage.dto.response.PresignedUrlResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@Slf4j
@RequiredArgsConstructor
public class PresignedUrlService {

	@Value("${cloud.aws.s3.bucket}")
	private String bucketName;

	private final S3Presigner s3Presigner;
	private final S3Client s3Client;
	private final IdentityGenerator identityGenerator;

	/**
	 * 생성용 preSigned URL
	 */
	@Transactional(readOnly = true)
	public PresignedUrlResponse getPostS3Url(PresignedUrlRequest request) {

		// filename 설정하기(profile 경로 + 멤버ID + 랜덤 값)
		String objectKey= generateImageObjectKey(request.uploadType());

		PutObjectRequest putObjectRequest= PutObjectRequest.builder()
			.bucket(bucketName)
			.key(objectKey)
			.build();

		// presigned url 생성하기
		PutObjectPresignRequest presignedRequest =
			getPutPreSignedUrlRequest(putObjectRequest);

		String url = s3Presigner.presignPutObject(presignedRequest)
			.url()
			.toString();

		return PresignedUrlResponse.builder()
			.presignedUrl(url)
			.objectKey(objectKey)
			.build();
	}

	/**
	 * 조회용 preSigned URL
	 */
	@Transactional(readOnly = true)
	public PresignedUrlResponse getGetS3Url(String imagePath) {

		GetObjectRequest getObjectRequest= GetObjectRequest.builder()
			.bucket(bucketName)
			.key(imagePath)
			.build();

		// presigned url 생성하기
		GetObjectPresignRequest presignedRequest =
			getGetPreSignedUrlRequest(getObjectRequest); //imageName이어야 함.

		String url = s3Presigner.presignGetObject(presignedRequest)
			.url()
			.toString();

		return PresignedUrlResponse.builder()
			.presignedUrl(url)
			.objectKey(imagePath)
			.build();
	}


	/**
	 * 업로드용 preSigned URL 요청 객체 생성
	 */
	private PutObjectPresignRequest getPutPreSignedUrlRequest(PutObjectRequest objectRequest){
		return PutObjectPresignRequest.builder()
			.signatureDuration(Duration.ofMinutes(3))
			.putObjectRequest(objectRequest)
			.build();
	}

	/**
	 * get용 url 생성
	 */
	private GetObjectPresignRequest getGetPreSignedUrlRequest(GetObjectRequest objectRequest){
		return GetObjectPresignRequest.builder()
			.signatureDuration(Duration.ofMinutes(3))
			.getObjectRequest(objectRequest)
			.build();
	}

	/**
	 * UUID 통해 objectKey(파일 식별 전체 경로) 생성
	 */
	private String generateImageObjectKey(ImageUploadType type) {
		return "image/"+identityGenerator.generateIdentity()+type.getExtension();
	}

	@Async
	public void deleteImageByPath(String imagePath){
		try{
			s3Client.deleteObject(
				DeleteObjectRequest.builder()
					.bucket(bucketName)
					.key(imagePath)
					.build()
			);
		}catch(Exception e){
			log.error("이미지 삭제 실패.{}",e.getMessage());
			throw new IllegalStateException(e.getMessage());
		}
	}
}
