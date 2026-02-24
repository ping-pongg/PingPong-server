package pingpong.backend.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
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
import pingpong.backend.domain.eval.service.LlmEvalAsyncService;
import pingpong.backend.domain.team.repository.MemberTeamRepository;
import pingpong.backend.global.exception.CustomException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
    private final LlmEvalAsyncService evalAsyncService;

    @Value("${rag.chat.top-k}")
    private int topK;

    @Value("${rag.chat.similarity-threshold}")
    private double similarityThreshold;

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
     * 실제 채팅 응답 스트리밍 처리.
     * 비동기로 Spring AI ChatClient의 stream()을 호출하여 SSE로 토큰 전송.
     * 스트리밍 완료 후 LLM eval을 비동기로 저장.
     */
    @Async("chatStreamExecutor")
    public void streamChatResponse(String streamId, Long teamId, String message, SseEmitter emitter) {
        try {
            streamManager.updateStatus(streamId, StreamStatus.STREAMING);
            log.info("Starting stream: streamId={}, teamId={}", streamId, teamId);

            String filterExpression = "teamId == " + teamId;
            log.info("STREAM-RAG: filter expression='{}' streamId={} teamId={}", filterExpression, streamId, teamId);

            long totalStart = System.currentTimeMillis();

            // RAG 진단 + docs 캡처
            long retrievalStart = System.currentTimeMillis();
            List<Document> retrievedDocs = diagnosVectorStoreContext(streamId, teamId, message, filterExpression);
            int latencyRetrieval = (int) (System.currentTimeMillis() - retrievalStart);

            log.info("STREAM-RAG: calling ChatClient (QuestionAnswerAdvisor + OpenAI streaming) — streamId={} teamId={}", streamId, teamId);

            long generationStart = System.currentTimeMillis();

            // 스트리밍 중 텍스트 누적 및 마지막 ChatResponse(usage 포함) 캡처
            StringBuilder accumulatedText = new StringBuilder();
            AtomicReference<ChatResponse> lastChatResponse = new AtomicReference<>();

            // .content() 대신 .chatResponse()로 변경 → usage 메타데이터(토큰/비용) 보존
            Flux<ChatResponse> responseFlux = chatClient.prompt()
                    .user(message)
                    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filterExpression))
                    .stream()
                    .chatResponse();

            responseFlux.subscribe(
                    chatResp -> {
                        try {
                            String token = chatResp.getResult() != null
                                    && chatResp.getResult().getOutput() != null
                                    ? chatResp.getResult().getOutput().getText()
                                    : null;

                            if (token != null && !token.isEmpty()) {
                                accumulatedText.append(token);
                                emitter.send(SseEmitter.event().data(token));
                            }
                            // 마지막 chunk에 usage 메타데이터가 담기므로 계속 갱신
                            lastChatResponse.set(chatResp);
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
                        log.error("Stream error: streamId={}, teamId={}", streamId, teamId, error);
                        streamManager.updateStatus(streamId, StreamStatus.ERROR);
                        emitter.completeWithError(error);
                    },
                    () -> {
                        try {
                            log.info("Stream completed: streamId={}, teamId={}", streamId, teamId);

                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data("[DONE]"));

                            streamManager.updateStatus(streamId, StreamStatus.COMPLETED);
                            emitter.complete();
                            streamManager.deleteStream(streamId);

                            // 스트리밍 완료 후 비동기 eval 저장 (사용자 latency 무영향)
                            int latencyGeneration = (int) (System.currentTimeMillis() - generationStart);
                            int latencyTotal      = (int) (System.currentTimeMillis() - totalStart);
                            evalAsyncService.evaluateAndSave(
                                    teamId, message, accumulatedText.toString(),
                                    retrievedDocs, lastChatResponse.get(),
                                    latencyTotal, latencyRetrieval, latencyGeneration
                            );
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
     * [RAG 진단] QuestionAnswerAdvisor 내부에서 수행될 VectorStore 검색을 동일 조건으로 미리 실행.
     * 컨텍스트 주입 가능 여부를 로그로 확인하고, Step 1 검색 결과를 eval용으로 반환.
     *
     * @return Step 1에서 검색된 문서 목록 (없으면 빈 리스트)
     */
    private List<Document> diagnosVectorStoreContext(String streamId, Long teamId, String message, String filterExpression) {
        List<Document> step1Docs = List.of();

        // ── Step 1: 실제 필터 + threshold=0.3 로 검색 (QuestionAnswerAdvisor와 동일 조건)
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(message)
                            .topK(topK)
                            .similarityThreshold(similarityThreshold)
                            .filterExpression(filterExpression)
                            .build()
            );

            if (docs == null || docs.isEmpty()) {
                log.warn("STREAM-RAG [Step1]: 필터+threshold=0.3 → 결과 0건. streamId={} teamId={} filter='{}'",
                        streamId, teamId, filterExpression);

                // ── Step 2: threshold=0.0 으로 낮춰서 재검색 (유사도 임계값 문제 여부 확인)
                try {
                    List<Document> lowThresholdDocs = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query(message)
                                    .topK(topK)
                                    .similarityThreshold(0.0)
                                    .filterExpression(filterExpression)
                                    .build()
                    );
                    if (lowThresholdDocs == null || lowThresholdDocs.isEmpty()) {
                        log.warn("STREAM-RAG [Step2]: 필터+threshold=0.0 → 결과 0건. → 원인: ①teamId={} 에 해당하는 데이터가 Pinecone에 없음 또는 ②필터 표현식 타입 불일치",
                                teamId);

                        // ── Step 3: 필터 없이 전체 검색 (Pinecone에 데이터 자체가 있는지 확인)
                        try {
                            List<Document> noFilterDocs = vectorStore.similaritySearch(
                                    SearchRequest.builder()
                                            .query(message)
                                            .topK(3)
                                            .similarityThreshold(0.0)
                                            .build()
                            );
                            if (noFilterDocs == null || noFilterDocs.isEmpty()) {
                                log.warn("STREAM-RAG [Step3]: 필터 없음+threshold=0.0 → 결과 0건. → 원인 확정: Pinecone 인덱스가 비어있음 (인덱싱이 실행되지 않았거나 실패). indexing_state 테이블 확인 필요");
                            } else {
                                log.warn("STREAM-RAG [Step3]: 필터 없음+threshold=0.0 → 결과 {}건 존재! → 원인 확정: 필터 조건 문제 (teamId 메타데이터 타입 불일치 가능성)",
                                        noFilterDocs.size());
                                Object teamIdValue = noFilterDocs.get(0).getMetadata().get("teamId");
                                log.warn("STREAM-RAG [Step3]: Pinecone 내 teamId 메타데이터 값={} 타입={} (필터식: 'teamId == {}' — Long 숫자로 저장되어 있는지 확인)",
                                        teamIdValue,
                                        teamIdValue != null ? teamIdValue.getClass().getSimpleName() : "null",
                                        teamId);
                            }
                        } catch (Exception e3) {
                            log.error("STREAM-RAG [Step3]: 필터 없는 쿼리 실패 — errorType={} message='{}'",
                                    e3.getClass().getSimpleName(), e3.getMessage(), e3);
                        }

                    } else {
                        log.warn("STREAM-RAG [Step2]: 필터+threshold=0.0 → 결과 {}건 존재! → 원인 확정: similarityThreshold=0.3 가 너무 높음 (실제 최고 유사도가 0.3 미만). threshold 낮추는 것 검토 필요",
                                lowThresholdDocs.size());
                        log.warn("STREAM-RAG [Step2]: 가장 유사한 문서 score={} id={}",
                                lowThresholdDocs.get(0).getScore(), lowThresholdDocs.get(0).getId());
                    }
                } catch (Exception e2) {
                    log.error("STREAM-RAG [Step2]: threshold=0.0 쿼리 실패 — errorType={} message='{}'",
                            e2.getClass().getSimpleName(), e2.getMessage(), e2);
                }

            } else {
                log.info("STREAM-RAG [Step1]: 결과 {}건 — LLM 프롬프트에 컨텍스트 주입 예정. streamId={} teamId={} filter='{}'",
                        docs.size(), streamId, teamId, filterExpression);
                for (int i = 0; i < docs.size(); i++) {
                    Document doc = docs.get(i);
                    log.debug("STREAM-RAG: context[{}] id={} score={} sourceKey={} contentLength={}",
                            i, doc.getId(), doc.getScore(),
                            doc.getMetadata().get("sourceKey"),
                            doc.getText() != null ? doc.getText().length() : 0);
                }
                step1Docs = docs;
            }
        } catch (Exception e) {
            log.error("STREAM-RAG [Step1]: VectorStore 진단 쿼리 실패 — streamId={} teamId={} filter='{}' errorType={} message='{}'",
                    streamId, teamId, filterExpression, e.getClass().getSimpleName(), e.getMessage(), e);
        }

        return step1Docs;
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
