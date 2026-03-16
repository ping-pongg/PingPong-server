package pingpong.backend.domain.swagger.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import pingpong.backend.domain.qa.service.QaService;
import pingpong.backend.domain.swagger.dto.response.EndpointGroupResponse;
import pingpong.backend.domain.swagger.event.SwaggerSyncInitEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwaggerSyncInitService {

    private final SwaggerService swaggerService;
    private final QaService qaService;

    @Async("indexExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamCreated(SwaggerSyncInitEvent event) {
        Long teamId = event.teamId();
        log.info("SWAGGER_SYNC_INIT: 초기 동기화 시작 teamId={}", teamId);
        try {
            swaggerService.syncSwagger(teamId, event.member());
            log.info("SWAGGER_SYNC_INIT: 초기 동기화 완료 teamId={}", teamId);

            List<EndpointGroupResponse> groups = swaggerService.getLatestSnapshotGrouped(teamId);
            log.info("SWAGGER_INIT_FLOW: QA 시나리오 자동 생성 시작 (대상 그룹 수: {})", groups.size());

            groups.forEach(group ->
                group.endpoints().forEach(endpoint -> {
                    try {
                        qaService.createQaCases(endpoint.endpointId()); // 기존 메서드 재사용
                    } catch (Exception e) {
                        log.error("SWAGGER_INIT_FLOW: QA 생성 실패 - endpointId={}, error={}",
                            endpoint.endpointId(), e.getMessage());
                        // 특정 엔드포인트 실패가 전체 흐름을 끊지 않도록 개별 try-catch
                    }
                })
            );
            log.info("SWAGGER_INIT_FLOW: 모든 초기화 프로세스 완료 teamId={}", teamId);
        } catch (Exception e) {
            log.error("SWAGGER_SYNC_INIT: 초기 동기화 실패 teamId={}", teamId, e);
        }
    }
}
