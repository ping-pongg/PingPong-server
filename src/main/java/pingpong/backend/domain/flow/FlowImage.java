package pingpong.backend.domain.flow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pingpong.backend.domain.flow.enums.UploadStatus;

@Getter
@Entity
@Builder
@Table
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor(access= AccessLevel.PRIVATE)
public class FlowImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch= FetchType.LAZY)
	@JoinColumn(name="flow_id")
	private Flow flow;

	@Column(nullable = false)
	private String objectKey;

	// 업로드 상태 관리
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UploadStatus status;

	public static FlowImage create(Flow flow, String objectKey) {
		return FlowImage.builder()
			.flow(flow)
			.objectKey(objectKey)
			.status(UploadStatus.PENDING)
			.build();
	}

	public void markComplete() {
		// 업로드 완료 시점에 서버가 검증한 정보들을 저장
		this.status = UploadStatus.COMPLETE;

	}

}
