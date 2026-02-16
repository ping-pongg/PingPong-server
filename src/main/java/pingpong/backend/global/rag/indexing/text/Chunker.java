package pingpong.backend.global.rag.indexing.text;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pingpong.backend.global.rag.indexing.config.IndexingProperties;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class Chunker {

    private static final int MIN_CHUNK_SIZE = 200;
    private static final int CHUNK_BOUNDARY_WINDOW = 120;

    private final IndexingProperties properties;

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int chunkSize = Math.max(MIN_CHUNK_SIZE, properties.getChunkSize());
        int overlap = Math.max(0, Math.min(properties.getChunkOverlap(), chunkSize - 1));

        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int start = 0;

        while (start < length) {
            int hardEnd = Math.min(start + chunkSize, length);
            int end = findBoundary(text, start, hardEnd);
            if (end <= start) {
                end = hardEnd;
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            if (end >= length) {
                break;
            }

            int nextStart = end - overlap;
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        return chunks;
    }

    private int findBoundary(String text, int start, int hardEnd) {
        if (hardEnd >= text.length()) {
            return hardEnd;
        }

        int windowStart = Math.max(start + 1, hardEnd - CHUNK_BOUNDARY_WINDOW);
        for (int i = hardEnd; i >= windowStart; i--) {
            if (isBoundary(text.charAt(i - 1))) {
                return i;
            }
        }

        return hardEnd;
    }

    private boolean isBoundary(char c) {
        return Character.isWhitespace(c)
                || c == '\n'
                || c == '.'
                || c == ','
                || c == ';'
                || c == ':'
                || c == '!'
                || c == '?'
                || c == '…'
                || c == '。'
                || c == ')'
                || c == ']';
    }
}
