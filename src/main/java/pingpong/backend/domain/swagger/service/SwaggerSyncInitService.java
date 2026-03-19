package pingpong.backend.domain.swagger.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.qa.QaSyncHistory;
import pingpong.backend.domain.qa.dto.SwaggerChangedEvent;
import pingpong.backend.domain.qa.repository.QaSyncHistoryRepository;
import pingpong.backend.domain.qa.service.QaService;
import pingpong.backend.domain.qa.service.QaSyncHistoryService;
import pingpong.backend.domain.swagger.dto.response.EndpointGroupResponse;
import pingpong.backend.domain.swagger.event.SwaggerSyncInitEvent;
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.TeamErrorCode;
import pingpong.backend.domain.team.repository.TeamRepository;
import pingpong.backend.global.exception.CustomException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwaggerSyncInitService {

    private final SwaggerService swaggerService;
    private final QaService qaService;
    private final TeamRepository teamRepository;
    private final QaSyncHistoryRepository qaSyncHistoryRepository;
    private final QaSyncHistoryService qaSyncHistoryService;

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

    // 2. 변경 이벤트 (중간에 새로고침 버튼 눌렀을 때)
    @Async("indexExecutor")
    @EventListener
    public void onSwaggerChanged(SwaggerChangedEvent event) {
        log.info("SWAGGER_FLOW: 변경된 엔드포인트 QA 자동 생성 시작 teamId={}", event.teamId());
        try {

            // 이미 Controller에서 syncSwagger가 완료되었으므로 바로 QA 생성 진입
            processQaGeneration(event.teamId());
        } catch (Exception e) {
            log.error("SWAGGER_FLOW: 변경분 QA 생성 실패 teamId={}", event.teamId(), e);
        }
    }

    /**
     * 공통 QA 생성 로직
     */
    private void processQaGeneration(Long teamId) throws InterruptedException {
        Team team=teamRepository.findById(teamId).orElseThrow(()->new CustomException(TeamErrorCode.TEAM_NOT_FOUND));

        List<EndpointGroupResponse> groups = swaggerService.getLatestSnapshotGrouped(teamId);
        QaSyncHistory history=qaSyncHistoryService.createNewHistory(team,groups.size());
        log.info("QA_GEN: 시나리오 생성 작업 시작 (대상 그룹 수: {})", groups.size());

        for (EndpointGroupResponse group : groups) {
            for (var endpoint : group.endpoints()) {
                if(!endpoint.isChanged()){
                    continue;
                }
                try {
                    // API Rate Limit 방지를 위한 미세 지연
                    Thread.sleep(200);
                    qaService.createQaCases(endpoint.endpointId());
                    qaSyncHistoryService.updateProgress(history.getId(),true);
                } catch (Exception e) {
                    qaSyncHistoryService.updateProgress(history.getId(),true);
                }
            }
        }
        qaSyncHistoryService.completeHistory(history.getId());
        log.info("QA_GEN: 모든 프로세스 완료 teamId={}", teamId);
    }
}
