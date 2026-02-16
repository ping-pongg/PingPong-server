package pingpong.backend.global.rag.indexing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum IndexingErrorCode implements ApiErrorCode {

    INDEXING_NORMALIZER_NOT_FOUND("INDEX424", "등록된 Normalizer를 찾을 수 없습니다.", HttpStatus.FAILED_DEPENDENCY),
    INDEXING_VECTORIZE_FAILED("INDEX500", "벡터화 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INDEXING_CHUNK_FAILED("INDEX500", "청크 분할 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INDEXING_STATE_FAILED("INDEX500", "인덱싱 상태 저장 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String errorCode;
    private final String message;
    private final HttpStatus status;
}
