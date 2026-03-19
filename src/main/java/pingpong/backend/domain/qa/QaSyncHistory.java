package pingpong.backend.domain.qa;

import java.time.LocalDateTime;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pingpong.backend.domain.qa.enums.SyncStatus;
import pingpong.backend.domain.team.Team;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QaSyncHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id")
	private Team team;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SyncStatus status; // PENDING, PROCESSING, COMPLETED, FAILED

	private Integer totalCount;     // 대상 엔드포인트 총 개수
	private Integer successCount;   // 성공한 개수
	private Integer failCount;      // 실패한 개수

	private String errorMessage;    // 전체 실패 시 원인

	private LocalDateTime startedAt;
	private LocalDateTime completedAt;

	@Builder
	public QaSyncHistory(Team team, Integer totalCount) {
		this.team = team;
		this.totalCount = totalCount;
		this.successCount = 0;
		this.failCount = 0;
		this.status = SyncStatus.PENDING;
		this.startedAt = LocalDateTime.now();
	}

	// --- 비즈니스 로직 ---

	public void start() {
		this.status = SyncStatus.PROCESSING;
	}

	public void incrementSuccess() {
		this.successCount++;
	}

	public void incrementFail() {
		this.failCount++;
	}

	public void complete() {
		this.status = SyncStatus.COMPLETED;
		this.completedAt = LocalDateTime.now();
	}

	public void fail(String message) {
		this.status = SyncStatus.FAILED;
		this.errorMessage = message;
		this.completedAt = LocalDateTime.now();
	}
}