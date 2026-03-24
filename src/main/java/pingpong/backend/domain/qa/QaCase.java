package pingpong.backend.domain.qa;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import pingpong.backend.domain.qa.converter.JsonNodeConverter;
import pingpong.backend.domain.qa.converter.MapStringConverter;
import pingpong.backend.domain.qa.dto.ExpectedResponse;
import pingpong.backend.domain.qa.enums.SourceType;
import pingpong.backend.domain.qa.enums.TestType;
import pingpong.backend.domain.swagger.Endpoint;

@Getter
@DynamicUpdate
@Entity
@Table(name = "qa_case")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QaCase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column
	private String scenarioName;

	@Column
	@Enumerated(EnumType.STRING)
	private TestType testType;

	@Column
	@Enumerated(EnumType.STRING)
	private SourceType sourceType;

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
	@Convert(converter = MapStringConverter.class)
	private Map<String,String> headers;

	@Column(columnDefinition = "LONGTEXT")
	@Convert(converter = JsonNodeConverter.class)
	private JsonNode body;

	@Column
	private int expectedStatusCode;

	@Column
	private LocalDateTime createdAt;

	public static QaCase create(
		Endpoint endpoint,
		String scenarioName,
		TestType testType,
		String description,
		String pathVariables,
		String queryParams,
		Map<String, String> headers,
		JsonNode body,
		SourceType sourceType,
		int expectedStatusCode
	) {
		QaCase qa = new QaCase();
		qa.endpoint = endpoint;
		qa.scenarioName = scenarioName;
		qa.testType = testType;
		qa.description = description;
		qa.pathVariables = pathVariables;
		qa.queryParams = queryParams;
		qa.headers = headers;
		qa.body = body;
		// Embedded 객체 생성
		qa.expectedStatusCode = expectedStatusCode;
		qa.sourceType = sourceType;
		qa.createdAt = LocalDateTime.now();
		return qa;
	}
	public void updateIsSuccess(boolean isSuccess) {
		this.isSuccess = isSuccess;
	}
	// QaCase.java 엔티티 내부에 추가
	public void updateTestData(
		String pathVariables,
		String queryParams,
		Map<String, String> headers,
		JsonNode body,
		SourceType sourceType
	) {
		this.pathVariables = pathVariables;
		this.queryParams = queryParams;
		this.headers = headers;
		this.body = body;
		// 수정이 발생했으므로 성공 여부를 초기화하거나 최신화할 준비를 합니다.
		this.createdAt = LocalDateTime.now();
		this.sourceType=sourceType;
	}
}
