package pingpong.backend.global.storage.service;

import java.net.URL;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.global.storage.dto.request.PresignedUrlRequest;
import pingpong.backend.global.storage.dto.response.PresignedUrlResponse;

@Service
@Slf4j
@RequiredArgsConstructor
public class PresignedUrlService {

	@Value("${cloud.aws.s3.bucket}")
	private String bucketName;

	private final AmazonS3Client s3Client;
	private final IdentityGenerator identityGenerator;

	@Transactional(readOnly = true)
	public PresignedUrlResponse getPostS3Url(PresignedUrlRequest request) {
		String imageName=request.imageName();

		// filename 설정하기(profile 경로 + 멤버ID + 랜덤 값)
		String fileName=generateUniqueImageName(imageName);
		String imagePath = "image" + "/" + fileName;

		// presigned url 생성하기
		GeneratePresignedUrlRequest generatePresignedUrlRequest =
			getPutPreSignedUrlRequest(imagePath);

		URL url = s3Client.generatePresignedUrl(generatePresignedUrlRequest);

		return PresignedUrlResponse.builder()
			.presignedUrl(url.toExternalForm())
			.imagePath(imagePath)
			.build();
	}

	@Transactional(readOnly = true)
	public PresignedUrlResponse getGetS3Url(String imagePath) {

		// presigned url 생성하기
		GeneratePresignedUrlRequest generatePresignedUrlRequest =
			getGetPreSignedUrlRequest(imagePath); //imageName이어야 함.

		URL url = s3Client.generatePresignedUrl(generatePresignedUrlRequest);

		return PresignedUrlResponse.builder()
			.presignedUrl(url.toExternalForm())
			.imagePath(imagePath)
			.build();
	}


	/**
	 * 업로드용 preSigned URL 요청 객체 생성
	 */
	private GeneratePresignedUrlRequest getPutPreSignedUrlRequest(String imageName){
		GeneratePresignedUrlRequest generatePresignedUrlRequest= new GeneratePresignedUrlRequest(bucketName, imageName)
				.withMethod(HttpMethod.PUT)
				.withExpiration(new Date(System.currentTimeMillis()+180000));
		generatePresignedUrlRequest.addRequestParameter(
			Headers.S3_CANNED_ACL,
			CannedAccessControlList.PublicRead.toString());
		return generatePresignedUrlRequest;
	}

	/**
	 * get용 url 생성
	 */
	private GeneratePresignedUrlRequest getGetPreSignedUrlRequest(String imageName){
		return new GeneratePresignedUrlRequest(bucketName, imageName)
			.withMethod(HttpMethod.GET)
			.withExpiration(new Date(System.currentTimeMillis()+180000));
	}

	/**
	 * UUID 통해 imagePath 생성
	 */
	private String generateUniqueImageName(String imageName) {
		String ext = imageName.substring(imageName.lastIndexOf("."));
		return identityGenerator.generateIdentity() + ext;
	}

	@Async
	public void deleteImageByPath(String imagePath){
		try{
			s3Client.deleteObject(bucketName, imagePath);
		}catch(AmazonServiceException e){
			log.error("이미지 삭제 실패.{}",e.getMessage());
			throw new IllegalStateException(e.getMessage());
		}
	}
}
