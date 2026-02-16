package pingpong.backend.global.rag.indexing.normalizer;

import pingpong.backend.global.rag.indexing.enums.IndexSourceType;
import pingpong.backend.global.rag.indexing.dto.IndexJob;

public interface IndexingNormalizer {

    IndexSourceType sourceType();

    String normalize(IndexJob job);

    default boolean supports(IndexSourceType sourceType) {
        return sourceType != null && sourceType() == sourceType;
    }
}
