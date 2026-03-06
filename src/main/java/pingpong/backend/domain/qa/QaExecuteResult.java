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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "qa_execute_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QaExecuteResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "qa_case_id", nullable = false)
	private QaCase qaCase;

	@Column(nullable = false)
	private Integer httpStatus;

	@Column(nullable = false)
	private Boolean isSuccess;

	@Column(columnDefinition = "TEXT")
	private String responseHeaders;

	@Column(columnDefinition = "LONGTEXT")
	private String responseBody;

	@Column(nullable = false)
	private LocalDateTime executedAt;

	@Column
	private Long durationMs;

	public static QaExecuteResult create(QaCase qaCase, int httpStatus, boolean isSuccess,
		String responseHeaders, String responseBody, long durationMs) {
		QaExecuteResult result = new QaExecuteResult();
		result.qaCase = qaCase;
		result.httpStatus = httpStatus;
		result.isSuccess = isSuccess;
		result.responseHeaders = responseHeaders;
		result.responseBody = responseBody;
		result.executedAt = LocalDateTime.now();
		result.durationMs = durationMs;
		return result;
	}

	public static QaExecuteResult createFailed(QaCase qaCase, String errorMessage, long durationMs) {
		QaExecuteResult result = new QaExecuteResult();
		result.qaCase = qaCase;
		result.httpStatus = 0;
		result.isSuccess = false;
		result.responseHeaders = null;
		result.responseBody = errorMessage;
		result.executedAt = LocalDateTime.now();
		result.durationMs = durationMs;
		return result;
	}
}
