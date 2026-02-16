package pingpong.backend.global.rag.indexing.job;

import pingpong.backend.global.rag.indexing.dto.IndexJob;

public interface IndexJobHandler {

    void handle(IndexJob job);
}
