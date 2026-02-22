package pingpong.backend.domain.swagger;

import java.time.LocalDateTime;
import java.util.Objects;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

	@Column
	private String description;

	@Column
	private String operationId;

	@Column
	private String tag;

	//연동 여부
	@Column(name="is_linked")
	private Boolean isLinked;

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

	@JoinColumn(name="snapshot_id")
	@ManyToOne(fetch = FetchType.LAZY)
	private SwaggerSnapshot snapshot;


	public void markCreated(LocalDateTime createdAt, Member createdBy,SwaggerSnapshot snapshot) {
		this.createdAt = createdAt;
		this.createdBy = createdBy;
		this.snapshot = snapshot;
	}


	public void applyDiff(Endpoint prev) {
		if (prev == null) {
			this.isChanged = true;
			this.changeType = ChangeType.CREATED;
			return;
		}

		boolean requestChanged =
			!Objects.equals(this.requestSchemaHash, prev.getRequestSchemaHash());
		boolean responseChanged =
			!Objects.equals(this.responseSchemaHash, prev.getResponseSchemaHash());
		boolean changed = requestChanged || responseChanged;
		this.isChanged = changed;

		if (!changed) {
			return;
		}

		if (requestChanged && responseChanged) {
			this.changeType = ChangeType.BOTH_CHANGED;
		} else if (requestChanged) {
			this.changeType = ChangeType.REQUEST_CHANGED;
		} else if (responseChanged) {
			this.changeType = ChangeType.RESPONSE_CHANGED;
		} else {
			this.changeType = ChangeType.MODIFIED;
		}
	}

	public void assignTag(String tag) {
		this.tag = tag;
	}
}
