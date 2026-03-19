package pingpong.backend.domain.qa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.qa.QaErrorCode;
import pingpong.backend.domain.qa.QaSyncHistory;
import pingpong.backend.domain.qa.repository.QaSyncHistoryRepository;
import pingpong.backend.domain.team.Team;
import pingpong.backend.global.exception.CustomException;

@Service
@Slf4j
@RequiredArgsConstructor
public class QaSyncHistoryService {

	private final QaSyncHistoryRepository qaSyncHistoryRepository;

	/**
	 * 새로운 히스토리 생성 (즉시 커밋되어 유저가 'PROCESSING' 상태를 볼 수 있게 함)
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public QaSyncHistory createNewHistory(Team team, int totalCount) {
		QaSyncHistory history = QaSyncHistory.builder()
			.team(team)
			.totalCount(totalCount)
			.build();
		history.start(); // 생성과 동시에 PROCESSING 상태로 시작
		return qaSyncHistoryRepository.save(history);
	}

	/**
	 * 개별 작업 결과 반영 (하나 끝날 때마다 즉시 커밋)
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateProgress(Long historyId, boolean isSuccess) {
		QaSyncHistory history = qaSyncHistoryRepository.findById(historyId)
			.orElseThrow(() -> new CustomException(QaErrorCode.SYNC_HISTORY_NOT_FOUND));
		if (isSuccess) {
			history.incrementSuccess();
		} else {
			history.incrementFail();
		}
		// 메서드 종료 시 즉시 DB에 반영됨 (유저가 API 호출 시 숫자 올라가는 게 보임)
	}

	/**
	 * 최종 완료 처리
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void completeHistory(Long historyId) {
		QaSyncHistory history = qaSyncHistoryRepository.findById(historyId)
			.orElseThrow(() -> new CustomException(QaErrorCode.SYNC_HISTORY_NOT_FOUND));

		history.complete();
	}
}
