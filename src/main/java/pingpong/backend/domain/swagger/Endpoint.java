package pingpong.backend.domain.swagger;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pingpong.backend.domain.flow.FlowImageEndpoint;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.swagger.enums.ChangeType;
import pingpong.backend.domain.swagger.enums.CrudMethod;

@Getter
@Entity
@Builder
@Table
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor(access= AccessLevel.PRIVATE)
public class Endpoint {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column
	private Long id;

	@Column
	private String path;

	@Column
	private CrudMethod method;

	@Column
	private String summary;

	@Column(columnDefinition = "TEXT")
	private String description;

	//해당 엔드포인트 연동 완료 여부
	@Column
	private Boolean isCompleted;

	@Column
	private String operationId;

	@Column
	private String tag;

	@Column(name="structure_hash")
	private String structureHash;

	@Column
	private Boolean isChanged;

	//변경 타입
	@Column
	@Enumerated(EnumType.STRING)
	private ChangeType changeType;

	@Column
	private LocalDateTime createdAt;

	@Column
	private LocalDateTime updatedAt;

	@JoinColumn(name="created_by")
	@ManyToOne(fetch= FetchType.LAZY)
	private Member createdBy;

	@JoinColumn(name="updated_by")
	@ManyToOne(fetch= FetchType.LAZY)
	private Member updatedBy;

	@Column(name="request_schema_hash")
	private String requestSchemaHash;

	@Column(name="response_schema_hash")
	private String responseSchemaHash;

	@OneToMany(mappedBy = "endpoint", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<FlowImageEndpoint> imageEndpoints = new ArrayList<>();

	@JoinColumn(name="snapshot_id")
	@ManyToOne(fetch = FetchType.LAZY)
	private SwaggerSnapshot snapshot;

	public void markCompleted() {
		this.isCompleted = true;
	}

	public void markCreated(LocalDateTime createdAt, Member createdBy,SwaggerSnapshot snapshot) {
		this.createdAt = createdAt;
		this.createdBy = createdBy;
		this.snapshot = snapshot;
		this.isCompleted = false;

	}

	/**
	 * 이전 snaphost의 동일 endpoint와 비교해서
	 * 변경여부(isChanged), 변경 타입(changeType)을 결정
	 * @param prev
	 */
	public void applyDiff(Endpoint prev) {
		if (prev == null) {
			this.isChanged = true;
			this.changeType = ChangeType.CREATED;
			return;
		}
		boolean changed=!Objects.equals(this.structureHash,prev.getStructureHash());
		this.isChanged = changed;

		if(!changed){
			return;
		}
		this.changeType=ChangeType.MODIFIED;
	}

	public void assignTag(String tag) {
		this.tag = tag;
	}

	public void updateStructureHash(String structureHash) {
		this.structureHash = structureHash;
	}
}
