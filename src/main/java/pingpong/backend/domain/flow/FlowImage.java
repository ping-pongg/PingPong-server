package pingpong.backend.domain.flow;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
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

	@OneToMany(mappedBy = "image", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<FlowImageEndpoint> imageEndpoints = new ArrayList<>();

	public static FlowImage create(Flow flow, String objectKey) {
		return FlowImage.builder()
			.flow(flow)
			.objectKey(objectKey)
			.status(UploadStatus.PENDING)
			.build();
	}

	public void markComplete() {
		this.status = UploadStatus.COMPLETE;
	}
}
