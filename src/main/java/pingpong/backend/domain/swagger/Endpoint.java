package pingpong.backend.domain.swagger;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

	//연동 여부
	@Column(name="is_linked")
	private Boolean isLinked;

	@Column
	private Boolean isChanged;

	//변경 타입
	@Column
	private ChangeType changeType;

	@Column
	private LocalDateTime createdAt;

	@Column
	private LocalDateTime updatedAt;

	@Column
	private Long createdBy;

	@Column
	private Long updatedBy;

	@Column(name="request_schema_hash")
	private String requestSchemaHash;

	@Column(name="response_schema_hash")
	private String responseSchemaHash;



}
