package pingpong.backend.domain.notion.normalizer;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pingpong.backend.global.rag.indexing.config.IndexingProperties;
import pingpong.backend.global.rag.indexing.dto.IndexJob;
import pingpong.backend.global.rag.indexing.enums.IndexSourceType;
import pingpong.backend.global.rag.indexing.normalizer.IndexingNormalizer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotionResponseNormalizer implements IndexingNormalizer {

    private static final int MAX_BLOCK_DEPTH = 8;
    private static final int CHILD_DB_SAMPLE_LIMIT = 5;

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
            normalizePageBlocks(root, out);
        } else {
            out.section("[Blocks]");
            appendGenericText(root, out, 0);
        }

        return out.result();
    }

    private void normalizePrimaryDatabase(JsonNode root, NormalizeBuffer out) {
        JsonNode database = root.path("database");

        out.section("[Schema]");
        out.line("DatabaseId: " + textOrDash(database.path("id")));
        out.line("Title: " + textOrDash(joinRichText(database.path("title"))));
        out.line("Url: " + textOrDash(database.path("url")));
        appendSchema(database.path("properties"), out, "  ");

        out.section("[Pages]");
        JsonNode pages = root.path("query_result").path("results");
        if (!pages.isArray() || pages.isEmpty()) {
            out.line("No pages");
            return;
        }

        int pageIndex = 1;
        for (JsonNode page : pages) {
            out.line("[page] #" + pageIndex);
            out.line("  id: " + textOrDash(page.path("id")));
            out.line("  title: " + textOrDash(extractPageTitle(page)));
            out.line("  url: " + textOrDash(page.path("url")));
            out.line("  last_edited_time: " + textOrDash(page.path("last_edited_time")));
            appendCorePageProperties(page.path("properties"), out, "  ");
            pageIndex++;
        }
    }

    private void normalizePageBlocks(JsonNode root, NormalizeBuffer out) {
        out.section("[Blocks]");

        JsonNode results = root.path("results");
        if (results.isArray()) {
            BlockContext context = new BlockContext();
            appendBlocks(results, 0, null, context, out);
        }

        out.section("[Child Databases]");
        JsonNode childDatabases = root.path("child_databases");
        if (!childDatabases.isArray() || childDatabases.isEmpty()) {
            out.line("No child databases");
            return;
        }

        int index = 1;
        for (JsonNode child : childDatabases) {
            JsonNode childDatabase = child.path("database");
            out.line("[child_db] #" + index + " id=" + textOrDash(childDatabase.path("id")));
            out.line("  title: " + textOrDash(joinRichText(childDatabase.path("title"))));
            out.line("  url: " + textOrDash(childDatabase.path("url")));

            out.line("  [Schema]");
            appendSchema(childDatabase.path("properties"), out, "    ");

            out.line("  [Pages]");
            JsonNode samplePages = child.path("query_result").path("results");
            appendSamplePages(samplePages, out, "    ");

            index++;
        }
    }

    private void appendBlocks(JsonNode blocks,
                              int depth,
                              String currentHeading,
                              BlockContext context,
                              NormalizeBuffer out) {
        if (depth > MAX_BLOCK_DEPTH || blocks == null || !blocks.isArray()) {
            return;
        }

        int numberedIndex = 0;
        for (JsonNode block : blocks) {
            String type = block.path("type").asText("");
            if (type.isBlank()) {
                continue;
            }

            String blockId = block.path("id").asText("");
            String nextHeading = currentHeading;
            String line;

            if (isHeadingType(type)) {
                String headingText = extractBlockText(block, type);
                if (headingText.isBlank()) {
                    continue;
                }
                nextHeading = headingText;
                line = indent(depth) + "[heading] " + headingText + " {blockId=" + textOrDash(blockId) + "}";
            } else {
                line = renderBlockLine(block, type, depth, currentHeading, ++numberedIndex);
                if (line == null || line.isBlank()) {
                    continue;
                }
            }

            out.line(line);

            JsonNode children = block.path("children");
            if (children.isArray() && !children.isEmpty()) {
                appendBlocks(children, depth + 1, nextHeading, context, out);
            }
        }
    }

    private String renderBlockLine(JsonNode block,
                                   String type,
                                   int depth,
                                   String heading,
                                   int numberedIndex) {
        String blockId = block.path("id").asText("");
        String text = extractBlockText(block, type);

        if ("paragraph".equals(type) && text.isBlank()) {
            return null;
        }

        String indent = indent(depth);
        String sectionPrefix = heading == null || heading.isBlank() ? "" : "[section:" + heading + "] ";

        switch (type) {
            case "to_do": {
                boolean checked = block.path(type).path("checked").asBoolean(false);
                if (text.isBlank()) {
                    return null;
                }
                return indent + sectionPrefix + "[to_do:" + (checked ? "checked" : "unchecked") + "] " + text
                        + " {blockId=" + textOrDash(blockId) + ", depth=" + depth + "}";
            }
            case "numbered_list_item": {
                if (text.isBlank()) {
                    return null;
                }
                return indent + sectionPrefix + "[" + numberedIndex + ".] " + text
                        + " {blockId=" + textOrDash(blockId) + ", depth=" + depth + "}";
            }
            case "bulleted_list_item": {
                if (text.isBlank()) {
                    return null;
                }
                return indent + sectionPrefix + "[-] " + text
                        + " {blockId=" + textOrDash(blockId) + ", depth=" + depth + "}";
            }
            default: {
                if (text.isBlank()) {
                    return null;
                }
                String normalizedType = isHeadingType(type) ? "heading" : type;
                return indent + sectionPrefix + "[" + normalizedType + "] " + text
                        + " {blockId=" + textOrDash(blockId) + ", depth=" + depth + "}";
            }
        }
    }

    private void appendSchema(JsonNode schemaNode, NormalizeBuffer out, String indent) {
        if (schemaNode == null || !schemaNode.isObject()) {
            out.line(indent + "No schema");
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = schemaNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getKey();
            JsonNode def = entry.getValue();
            String type = def.path("type").asText("unknown");
            out.line(indent + "- " + name + " (" + type + ")" + formatPropertyOptions(type, def));
        }
    }

    private void appendSamplePages(JsonNode pages, NormalizeBuffer out, String indent) {
        if (pages == null || !pages.isArray() || pages.isEmpty()) {
            out.line(indent + "No sample pages");
            return;
        }

        int max = Math.min(CHILD_DB_SAMPLE_LIMIT, pages.size());
        for (int i = 0; i < max; i++) {
            JsonNode page = pages.get(i);
            out.line(indent + "- title: " + textOrDash(extractPageTitle(page)));
            out.line(indent + "  id: " + textOrDash(page.path("id")));
            out.line(indent + "  url: " + textOrDash(page.path("url")));
            out.line(indent + "  last_edited_time: " + textOrDash(page.path("last_edited_time")));
            appendCorePageProperties(page.path("properties"), out, indent + "  ");
        }
    }

    private void appendCorePageProperties(JsonNode propertiesNode, NormalizeBuffer out, String indent) {
        if (propertiesNode == null || !propertiesNode.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            String type = value.path("type").asText("");
            if (!isCoreType(type)) {
                continue;
            }

            String parsed = parsePropertyValue(value);
            if (!parsed.isBlank()) {
                out.line(indent + "- " + entry.getKey() + " (" + type + "): " + parsed);
            }
        }
    }

    private String parsePropertyValue(JsonNode propertyNode) {
        if (propertyNode == null || propertyNode.isNull()) {
            return "";
        }

        String type = propertyNode.path("type").asText("");
        switch (type) {
            case "title":
                return joinRichText(propertyNode.path("title"));
            case "rich_text":
                return joinRichText(propertyNode.path("rich_text"));
            case "status": {
                String current = propertyNode.path("status").path("name").asText("");
                String options = joinOptionNames(propertyNode.path("status").path("options"));
                return formatCurrentAndOptions(current, options);
            }
            case "select": {
                String current = propertyNode.path("select").path("name").asText("");
                String options = joinOptionNames(propertyNode.path("select").path("options"));
                return formatCurrentAndOptions(current, options);
            }
            case "multi_select": {
                String current = joinNames(propertyNode.path("multi_select"));
                String options = joinOptionNames(propertyNode.path("multi_select").path("options"));
                return formatCurrentAndOptions(current, options);
            }
            case "date":
                return formatDateRange(propertyNode.path("date"));
            default:
                return propertyNode.asText("");
        }
    }

    private String formatPropertyOptions(String type, JsonNode definitionNode) {
        if ("status".equals(type)) {
            String options = joinOptionNames(definitionNode.path("status").path("options"));
            return options.isBlank() ? "" : " options=[" + options + "]";
        }
        if ("select".equals(type)) {
            String options = joinOptionNames(definitionNode.path("select").path("options"));
            return options.isBlank() ? "" : " options=[" + options + "]";
        }
        if ("multi_select".equals(type)) {
            String options = joinOptionNames(definitionNode.path("multi_select").path("options"));
            return options.isBlank() ? "" : " options=[" + options + "]";
        }
        return "";
    }

    private String formatCurrentAndOptions(String current, String options) {
        String currentText = current == null || current.isBlank() ? "-" : current;
        if (options == null || options.isBlank()) {
            return "current=" + currentText;
        }
        return "current=" + currentText + ", options=[" + options + "]";
    }

    private String extractPageTitle(JsonNode pageNode) {
        JsonNode properties = pageNode.path("properties");
        if (properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode property = entry.getValue();
                if ("title".equals(property.path("type").asText())) {
                    String title = joinRichText(property.path("title"));
                    if (!title.isBlank()) {
                        return title;
                    }
                }
            }
        }

        String fallbackTitle = joinRichText(pageNode.path("title"));
        return fallbackTitle.isBlank() ? "" : fallbackTitle;
    }

    private String extractBlockText(JsonNode blockNode, String type) {
        JsonNode typedNode = blockNode.path(type);

        String richText = joinRichText(typedNode.path("rich_text"));
        if (!richText.isBlank()) {
            return richText;
        }

        String caption = joinRichText(typedNode.path("caption"));
        if (!caption.isBlank()) {
            return caption;
        }

        String title = typedNode.path("title").asText("");
        if (!title.isBlank()) {
            return title;
        }

        String name = typedNode.path("name").asText("");
        if (!name.isBlank()) {
            return name;
        }

        return "";
    }

    private void appendGenericText(JsonNode node, NormalizeBuffer out, int depth) {
        if (node == null || node.isNull() || depth > MAX_BLOCK_DEPTH) {
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
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                if (value.isValueNode()) {
                    String text = value.asText("").trim();
                    if (!text.isBlank()) {
                        out.line(entry.getKey() + ": " + text);
                    }
                } else {
                    appendGenericText(value, out, depth + 1);
                }
            }
        }
    }

    private boolean isHeadingType(String type) {
        return "heading_1".equals(type) || "heading_2".equals(type) || "heading_3".equals(type);
    }

    private boolean isCoreType(String type) {
        return "title".equals(type)
                || "status".equals(type)
                || "date".equals(type)
                || "select".equals(type)
                || "multi_select".equals(type);
    }

    private String joinRichText(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode node : arrayNode) {
            String text = node.path("plain_text").asText("").trim();
            if (!text.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(text);
            }
        }
        return sb.toString();
    }

    private String joinNames(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            String name = node.path("name").asText("").trim();
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return String.join(", ", names);
    }

    private String joinOptionNames(JsonNode optionsNode) {
        if (optionsNode == null || !optionsNode.isArray()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode option : optionsNode) {
            String name = option.path("name").asText("").trim();
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return String.join(", ", names);
    }

    private String formatDateRange(JsonNode dateNode) {
        if (dateNode == null || dateNode.isNull()) {
            return "";
        }
        String start = dateNode.path("start").asText("");
        String end = dateNode.path("end").asText("");

        if (start.isBlank() && end.isBlank()) {
            return "";
        }
        if (end.isBlank()) {
            return "single: " + start;
        }
        return "range: " + start + " ~ " + end;
    }

    private String textOrDash(JsonNode node) {
        if (node == null || node.isNull()) {
            return "-";
        }
        String text = node.asText("").trim();
        return text.isBlank() ? "-" : text;
    }

    private String textOrDash(String text) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        return text;
    }

    private String indent(int depth) {
        if (depth <= 0) {
            return "";
        }
        return "  ".repeat(depth);
    }

    private static final class BlockContext {
    }

    private static final class NormalizeBuffer {
        private final StringBuilder sb = new StringBuilder();
        private final int maxChars;
        private final Map<String, Integer> sectionBoundaries = new LinkedHashMap<>();
        private String currentSection = "";
        private boolean truncated = false;

        private NormalizeBuffer(int maxChars) {
            this.maxChars = maxChars;
        }

        private void section(String sectionHeader) {
            if (sectionHeader == null || sectionHeader.isBlank() || truncated) {
                return;
            }
            currentSection = sectionHeader;
            sectionBoundaries.put(sectionHeader, sb.length());
            line(sectionHeader);
        }

        private void line(String line) {
            if (line == null || line.isBlank() || truncated) {
                return;
            }

            int needed = line.length() + 1;
            if (sb.length() + needed > maxChars) {
                truncateWithBoundary();
                return;
            }

            sb.append(line).append('\n');
        }

        private void truncateWithBoundary() {
            if (truncated) {
                return;
            }
            truncated = true;

            int cutIndex = sb.length();
            if (!currentSection.isBlank()) {
                Integer sectionStart = sectionBoundaries.get(currentSection);
                if (sectionStart != null && sectionStart > 0 && sectionStart < sb.length()) {
                    cutIndex = sectionStart;
                }
            }

            if (cutIndex < sb.length()) {
                sb.setLength(cutIndex);
            }

            String marker = "[TRUNCATED: max-normalized-chars reached]";
            int available = maxChars - sb.length();
            if (available > marker.length() + 1) {
                sb.append(marker).append('\n');
            }
        }

        private String result() {
            return sb.toString().trim();
        }
    }
}
