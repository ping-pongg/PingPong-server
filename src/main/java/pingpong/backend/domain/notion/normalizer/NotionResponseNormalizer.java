package pingpong.backend.domain.notion.normalizer;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pingpong.backend.global.rag.indexing.config.IndexingProperties;
import pingpong.backend.global.rag.indexing.dto.IndexJob;
import pingpong.backend.global.rag.indexing.enums.IndexSourceType;
import pingpong.backend.global.rag.indexing.normalizer.IndexingNormalizer;

@Component
@RequiredArgsConstructor
public class NotionResponseNormalizer implements IndexingNormalizer {

    private final IndexingProperties properties;

    @Override
    public IndexSourceType sourceType() {
        return IndexSourceType.NOTION;
    }

    @Override
    public String normalize(IndexJob job) {
        JsonNode root = job.payload();
        if (root == null || root.isNull()) {
            return "";
        }

        int maxChars = Math.max(10_000, properties.getMaxNormalizedChars());
        NormalizeBuffer out = new NormalizeBuffer(maxChars);

        out.section("[Source]");
        out.line("API: " + job.apiPath());
        if (job.resourceId() != null && !job.resourceId().isBlank()) {
            out.line("ResourceId: " + job.resourceId());
        }

        if (job.apiPath().contains("/databases/primary")) {
            normalizePrimaryDatabase(root, out);
        } else if (job.apiPath().contains("/notion/pages/")) {
            normalizePageDetail(root, out);
        } else {
            out.section("[Data]");
            appendGenericText(root, out, 0);
        }

        return out.result();
    }

    // -------------------------------------------------------------------------
    // DatabaseWithPagesResponse JSON 구조:
    //   { "databaseTitle": "...", "pages": [ { "id", "url", "title", "date": {"start","end"}, "status" }, ... ] }
    // -------------------------------------------------------------------------
    private void normalizePrimaryDatabase(JsonNode root, NormalizeBuffer out) {
        out.section("[Database]");
        out.line("Title: " + asText(root.path("databaseTitle")));

        out.section("[Pages]");
        JsonNode pages = root.path("pages");
        if (!pages.isArray() || pages.isEmpty()) {
            out.line("No pages");
            return;
        }

        int index = 1;
        for (JsonNode page : pages) {
            String dateStr = formatDateRange(page.path("date"));
            out.line("#" + index
                    + " id=" + asText(page.path("id"))
                    + " | title=" + asText(page.path("title"))
                    + " | status=" + asText(page.path("status"))
                    + " | date=" + dateStr
                    + " | url=" + asText(page.path("url")));
            index++;
        }
    }

    // -------------------------------------------------------------------------
    // PageDetailResponse JSON 구조:
    //   { "id", "url", "title", "date": {"start","end"}, "status",
    //     "pageContent": "...",
    //     "childDatabases": [ { "databaseTitle": "...", "pages": [ { "id","url","title","status" }, ... ] }, ... ] }
    // -------------------------------------------------------------------------
    private void normalizePageDetail(JsonNode root, NormalizeBuffer out) {
        out.section("[Page]");
        out.line("PageId: " + asText(root.path("id")));
        out.line("Title: " + asText(root.path("title")));
        out.line("Status: " + asText(root.path("status")));
        out.line("Date: " + formatDateRange(root.path("date")));
        out.line("Url: " + asText(root.path("url")));

        String content = root.path("pageContent").asText("").trim();
        if (!content.isBlank()) {
            out.section("[Content]");
            out.line(content);
        }

        out.section("[Child Databases]");
        JsonNode childDatabases = root.path("childDatabases");
        if (!childDatabases.isArray() || childDatabases.isEmpty()) {
            out.line("No child databases");
            return;
        }

        int dbIndex = 1;
        for (JsonNode child : childDatabases) {
            out.line("[child_db] #" + dbIndex + " title=" + asText(child.path("databaseTitle")));

            JsonNode childPages = child.path("pages");
            if (!childPages.isArray() || childPages.isEmpty()) {
                out.line("  No pages");
            } else {
                for (JsonNode page : childPages) {
                    out.line("  - pageId=" + asText(page.path("id"))
                            + " | title=" + asText(page.path("title"))
                            + " | status=" + asText(page.path("status"))
                            + " | url=" + asText(page.path("url")));
                }
            }
            dbIndex++;
        }
    }

    private void appendGenericText(JsonNode node, NormalizeBuffer out, int depth) {
        if (node == null || node.isNull() || depth > 6) {
            return;
        }
        if (node.isTextual()) {
            String text = node.asText("").trim();
            if (!text.isBlank()) {
                out.line(text);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                appendGenericText(child, out, depth + 1);
            }
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isValueNode()) {
                    String text = value.asText("").trim();
                    if (!text.isBlank()) {
                        out.line(entry.getKey() + ": " + text);
                    }
                } else {
                    appendGenericText(value, out, depth + 1);
                }
            });
        }
    }

    private String formatDateRange(JsonNode dateNode) {
        if (dateNode == null || dateNode.isNull() || dateNode.isMissingNode()) {
            return "-";
        }
        String start = dateNode.path("start").asText("").trim();
        String end = dateNode.path("end").asText("").trim();
        if (start.isBlank() && end.isBlank()) {
            return "-";
        }
        if (end.isBlank()) {
            return start;
        }
        return start + "~" + end;
    }

    private String asText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "-";
        }
        String text = node.asText("").trim();
        return text.isBlank() ? "-" : text;
    }

    // -------------------------------------------------------------------------
    // NormalizeBuffer: 최대 문자 수 초과 시 현재 섹션 경계에서 자름
    // -------------------------------------------------------------------------
    private static final class NormalizeBuffer {
        private final StringBuilder sb = new StringBuilder();
        private final int maxChars;
        private int currentSectionStart = 0;
        private boolean truncated = false;

        private NormalizeBuffer(int maxChars) {
            this.maxChars = maxChars;
        }

        private void section(String sectionHeader) {
            if (sectionHeader == null || sectionHeader.isBlank() || truncated) {
                return;
            }
            currentSectionStart = sb.length();
            line(sectionHeader);
        }

        private void line(String line) {
            if (line == null || line.isBlank() || truncated) {
                return;
            }
            int needed = line.length() + 1;
            if (sb.length() + needed > maxChars) {
                truncateAtSectionBoundary();
                return;
            }
            sb.append(line).append('\n');
        }

        private void truncateAtSectionBoundary() {
            if (truncated) {
                return;
            }
            truncated = true;
            if (currentSectionStart > 0 && currentSectionStart < sb.length()) {
                sb.setLength(currentSectionStart);
            }
            String marker = "[TRUNCATED: max-normalized-chars reached]";
            if (sb.length() + marker.length() + 1 <= maxChars) {
                sb.append(marker).append('\n');
            }
        }

        private String result() {
            return sb.toString().trim();
        }
    }
}
