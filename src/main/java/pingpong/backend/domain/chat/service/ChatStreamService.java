package pingpong.backend.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pingpong.backend.domain.chat.ChatErrorCode;
import pingpong.backend.domain.chat.stream.ChatStreamManager;
import pingpong.backend.domain.chat.stream.StreamMetadata;
import pingpong.backend.domain.chat.stream.StreamStatus;
import pingpong.backend.domain.team.repository.MemberTeamRepository;
import pingpong.backend.global.exception.CustomException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 채팅 스트리밍 서비스
 * SSE를 통한 실시간 채팅 응답 스트리밍을 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ChatStreamManager streamManager;
    private final MemberTeamRepository memberTeamRepository;

    public void validateTeamAccess(Long teamId, Long memberId) {
        if (!memberTeamRepository.existsByTeamIdAndMemberId(teamId, memberId)) {
            throw new CustomException(ChatErrorCode.STREAM_ACCESS_DENIED);
        }
    }

    public SseEmitter streamChat(Long teamId, Long memberId, String streamId) {
        StreamMetadata metadata = validateStreamAccess(streamId, memberId);

        if (!metadata.getTeamId().equals(teamId)) {
            throw new CustomException(ChatErrorCode.STREAM_ACCESS_DENIED);
        }

        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5분 타임아웃
        streamChatResponse(streamId, teamId, metadata.getMessage(), emitter);
        return emitter;
    }

    /**
     * 스트림 초기화
     * 고유한 streamId를 생성하고 Redis에 메타데이터 저장
     *
     * @param teamId 팀 ID
     * @param memberId 멤버 ID
     * @param message 채팅 메시지
     * @return 생성된 streamId
     */
    public String initializeStream(Long teamId, Long memberId, String message) {
        try {
            String streamId = UUID.randomUUID().toString();

            StreamMetadata metadata = StreamMetadata.builder()
                    .streamId(streamId)
                    .teamId(teamId)
                    .memberId(memberId)
                    .status(StreamStatus.PENDING)
                    .message(message)
                    .createdAt(System.currentTimeMillis())
                    .build();

            streamManager.saveStream(metadata);
            log.info("Stream initialized: streamId={}, teamId={}, memberId={}", streamId, teamId, memberId);

            return streamId;
        } catch (Exception e) {
            log.error("Failed to initialize stream: teamId={}, memberId={}", teamId, memberId, e);
            throw new CustomException(ChatErrorCode.STREAM_INITIALIZATION_FAILED);
        }
    }

    /**
     * 스트림 접근 권한 검증
     * Redis에서 메타데이터를 조회하고 소유권 및 상태를 확인
     *
     * @param streamId 스트림 ID
     * @param memberId 요청한 멤버 ID
     * @return 검증된 스트림 메타데이터
     * @throws CustomException 스트림이 존재하지 않거나 접근 권한이 없는 경우
     */
    public StreamMetadata validateStreamAccess(String streamId, Long memberId) {
        StreamMetadata metadata = streamManager.getStream(streamId)
                .orElseThrow(() -> new CustomException(ChatErrorCode.STREAM_NOT_FOUND));

        // 소유권 검증
        if (!metadata.getMemberId().equals(memberId)) {
            log.warn("Stream access denied: streamId={}, requestMemberId={}, ownerId={}",
                    streamId, memberId, metadata.getMemberId());
            throw new CustomException(ChatErrorCode.STREAM_ACCESS_DENIED);
        }

        // 상태 검증
        if (metadata.getStatus() == StreamStatus.COMPLETED) {
            log.warn("Attempt to access completed stream: streamId={}", streamId);
            throw new CustomException(ChatErrorCode.STREAM_ALREADY_COMPLETED);
        }

        if (metadata.getStatus() == StreamStatus.ERROR) {
            log.warn("Attempt to access error stream: streamId={}", streamId);
            throw new CustomException(ChatErrorCode.STREAM_INITIALIZATION_FAILED);
        }

        return metadata;
    }

    /**
     * 실제 채팅 응답 스트리밍 처리
     * 비동기로 Spring AI ChatClient의 stream()을 호출하여 SSE로 토큰 전송
     *
     * @param streamId 스트림 ID
     * @param teamId 팀 ID
     * @param message 채팅 메시지
     * @param emitter SSE Emitter
     */
    @Async("chatStreamExecutor")
    public void streamChatResponse(String streamId, Long teamId, String message, SseEmitter emitter) {
        try {
            // 스트림 상태를 STREAMING으로 변경
            streamManager.updateStatus(streamId, StreamStatus.STREAMING);
            log.info("Starting stream: streamId={}, teamId={}", streamId, teamId);

            // RAG 필터 표현식 설정
            String filterExpression = "teamId == " + teamId;
            log.info("STREAM-RAG: filter expression='{}' streamId={} teamId={}", filterExpression, streamId, teamId);

            // [진단] QuestionAnswerAdvisor가 실제로 사용할 VectorStore 검색 결과를 미리 확인
            diagnosVectorStoreContext(streamId, teamId, message, filterExpression);

            log.info("STREAM-RAG: calling ChatClient (QuestionAnswerAdvisor + OpenAI streaming) — streamId={} teamId={}", streamId, teamId);

            // Spring AI ChatClient로 스트리밍 요청
            Flux<String> tokens = chatClient.prompt()
                    .user(message)
                    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filterExpression))
                    .stream()
                    .content();

            // 토큰 스트리밍 구독
            tokens.subscribe(
                    token -> {
                        try {
                            // SSE 형식으로 토큰 전송
                            emitter.send(SseEmitter.event().data(token));
                        } catch (IOException e) {
                            if (isClientDisconnect(e)) {
                                log.info("SSE client disconnected (ignore): streamId={}", streamId);
                                return;
                            }
                            log.error("Failed to send token: streamId={}", streamId, e);
                            throw new RuntimeException(e);
                        }
                    },
                    error -> {
                        // 에러 발생 시 처리
                        log.error("Stream error: streamId={}, teamId={}", streamId, teamId, error);
                        streamManager.updateStatus(streamId, StreamStatus.ERROR);
                        emitter.completeWithError(error);
                    },
                    () -> {
                        try {
                            // 스트리밍 완료 시 명시적인 종료 이벤트 전송
                            log.info("Stream completed: streamId={}, teamId={}", streamId, teamId);

                            // [DONE] 이벤트 전송 (SSE 표준 종료 신호)
                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data("[DONE]"));

                            streamManager.updateStatus(streamId, StreamStatus.COMPLETED);

                            // 연결 정상 종료
                            emitter.complete();

                            // 완료 후 Redis 정리 (TTL에 의해 자동 삭제되지만 명시적으로 삭제)
                            streamManager.deleteStream(streamId);
                        } catch (IOException e) {
                            if (isClientDisconnect(e)) {
                                log.info("SSE client disconnected (ignore): streamId={}", streamId);
                                return;
                            }
                            log.error("Failed to send completion event: streamId={}", streamId, e);
                            emitter.completeWithError(e);
                        }
                    }
            );

            // SseEmitter 콜백 설정
            emitter.onTimeout(() -> {
                log.warn("Stream timeout: streamId={}", streamId);
                streamManager.updateStatus(streamId, StreamStatus.ERROR);
            });

            emitter.onError((ex) -> {
                log.error("Emitter error: streamId={}", streamId, ex);
                streamManager.updateStatus(streamId, StreamStatus.ERROR);
            });

            emitter.onCompletion(() -> {
                log.debug("Emitter completed: streamId={}", streamId);
            });

        } catch (Exception e) {
            log.error("Failed to start streaming: streamId={}, teamId={}", streamId, teamId, e);
            streamManager.updateStatus(streamId, StreamStatus.ERROR);
            emitter.completeWithError(e);
        }
    }

    /**
     * [RAG 진단] QuestionAnswerAdvisor 내부에서 수행될 VectorStore 검색을 동일 조건으로 미리 실행하여
     * 컨텍스트 주입 가능 여부를 로그로 확인합니다.
     */
    private void diagnosVectorStoreContext(String streamId, Long teamId, String message, String filterExpression) {
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(message)
                            .topK(5)
                            .similarityThreshold(0.5)
                            .filterExpression(filterExpression)
                            .build()
            );

            if (docs == null || docs.isEmpty()) {
                log.warn("STREAM-RAG: VectorStore 검색 결과 0건 — LLM 프롬프트에 컨텍스트가 주입되지 않습니다. " +
                                "streamId={} teamId={} filter='{}' (가능한 원인: ①해당 teamId로 인덱싱된 데이터 없음 ②필터 타입 불일치 ③similarityThreshold=0.5 초과)",
                        streamId, teamId, filterExpression);
            } else {
                log.info("STREAM-RAG: VectorStore 검색 결과 {}건 — LLM 프롬프트에 컨텍스트 주입 예정. streamId={} teamId={} filter='{}'",
                        docs.size(), streamId, teamId, filterExpression);
                for (int i = 0; i < docs.size(); i++) {
                    Document doc = docs.get(i);
                    log.debug("STREAM-RAG: context[{}] id={} score={} sourceKey={} contentLength={}",
                            i, doc.getId(), doc.getScore(),
                            doc.getMetadata().get("sourceKey"),
                            doc.getText() != null ? doc.getText().length() : 0);
                }
            }
        } catch (Exception e) {
            log.error("STREAM-RAG: VectorStore 진단 쿼리 실패 — streamId={} teamId={} filter='{}' errorType={} message='{}'",
                    streamId, teamId, filterExpression, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    private boolean isClientDisconnect(IOException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("broken pipe") || lower.contains("connection reset");
    }
}
