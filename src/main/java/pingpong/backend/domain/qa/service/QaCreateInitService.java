package pingpong.backend.domain.qa.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.swagger.event.SwaggerSyncInitEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaCreateInitService {

	private final QaService qaService;

	@Async("indexExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onTeamCreated(QaCreateInitService event) {
		Long teamId = event.teamId();
		log.info("SWAGGER_SYNC_INIT: 초기 동기화 시작 teamId={}", teamId);
		try {
			swaggerService.syncSwagger(teamId, event.member());
			log.info("SWAGGER_SYNC_INIT: 초기 동기화 완료 teamId={}", teamId);
		} catch (Exception e) {
			log.error("SWAGGER_SYNC_INIT: 초기 동기화 실패 teamId={}", teamId, e);
		}
	}
}
