package pingpong.backend.global.storage.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

	@Value("${cloud.aws.credentials.access-key}")
	private String accessKey;

	@Value("${cloud.aws.credentials.secret-key}")
	private String secretKey;

	@Value("${cloud.aws.region.static}")
	private String region;

	@Bean
	public AwsBasicCredentials awsBasicCredentials() {
		return AwsBasicCredentials.create(accessKey, secretKey);
	}

	/**
	 * 실제 API 호출rmsep
	 */
	@Bean
	public S3Client s3Client(AwsBasicCredentials awsBasicCredentials) {
		return S3Client.builder()
			.credentialsProvider(
				StaticCredentialsProvider.create(awsBasicCredentials)
			)
			.region(Region.of(region))
			.build();
	}

	/**
	 * Presigned URL 전용
	 */
	@Bean
	public S3Presigner s3Presigner(AwsBasicCredentials awsBasicCredentials) {
		return S3Presigner.builder()
			.credentialsProvider(
				StaticCredentialsProvider.create(awsBasicCredentials)
			)
			.region(Region.of(region))
			.build();
	}
}
