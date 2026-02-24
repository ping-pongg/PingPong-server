package pingpong.backend.domain.flow.dto;

public record ImageUploadDto (

	Long imageId,
	String presignedUrl,
	String objectKey
){
}
