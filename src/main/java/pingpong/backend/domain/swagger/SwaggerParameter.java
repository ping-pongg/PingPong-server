package pingpong.backend.domain.swagger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Getter
@Builder
@Table
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SwaggerParameter {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column
	private Long id;

	@Column
	private String name;

	@Column
	private String inType;    // header, query, path, cookie

	@Column
	private Boolean required;

	@Column
	private String schemaHash;

	@Column
	private String description;

	@Column(columnDefinition = "LONGTEXT")
	private String schemaJson;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name="endpoint_id")
	private Endpoint endpoint;


}
