package pingpong.backend.domain.qa;

import java.time.LocalDateTime;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import pingpong.backend.domain.swagger.Endpoint;

@Getter
@Entity
@Table(name = "qa_case")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QaCase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "endpoint_id", nullable = false)
	private Endpoint endpoint;

	@Column
	private Boolean isSuccess;

	@Column(nullable = false)
	private String description;

	@Column(columnDefinition = "TEXT")
	private String pathVariables;

	@Column(columnDefinition = "TEXT")
	private String queryParams;

	@Column(columnDefinition = "TEXT")
	private String headers;

	@Column(columnDefinition = "LONGTEXT")
	private String body;

	@Column
	private LocalDateTime createdAt;

	public static QaCase create(Endpoint endpoint, String description,
		String pathVariables, String queryParams, String headers, String body) {
		QaCase qa = new QaCase();
		qa.endpoint = endpoint;
		qa.description = description;
		qa.pathVariables = pathVariables;
		qa.queryParams = queryParams;
		qa.headers = headers;
		qa.body = body;
		qa.createdAt = LocalDateTime.now();
		return qa;
	}

	public void updateIsSuccess(boolean isSuccess) {
		this.isSuccess = isSuccess;
	}
}
