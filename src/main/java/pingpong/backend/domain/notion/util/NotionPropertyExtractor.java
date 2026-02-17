package pingpong.backend.domain.notion.util;

import com.fasterxml.jackson.databind.JsonNode;
import pingpong.backend.domain.notion.dto.common.PageDateRange;

import java.util.Iterator;

/**
 * Notion API 응답에서 property 값을 추출하는 유틸리티 클래스
 * Notion의 동적 property 구조에서 특정 타입의 값을 안전하게 추출
 */
public class NotionPropertyExtractor {

    /**
     * properties 객체에서 title 타입의 속성 값을 추출
     *
     * @param propertiesNode Notion API의 properties JSON 노드
     * @return title 텍스트, 없으면 null
     */
    public static String extractTitle(JsonNode propertiesNode) {
        if (propertiesNode == null || propertiesNode.isNull()) {
            return null;
        }

        Iterator<String> fieldNames = propertiesNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode property = propertiesNode.get(fieldName);

            if (property.has("type") && "title".equals(property.get("type").asText())) {
                JsonNode titleArray = property.get("title");
                if (titleArray != null && titleArray.isArray() && titleArray.size() > 0) {
                    JsonNode firstElement = titleArray.get(0);

                    // text.content를 먼저 시도
                    if (firstElement.has("text") && firstElement.get("text").has("content")) {
                        return firstElement.get("text").get("content").asText();
                    }

                    // plain_text를 fallback으로 시도
                    if (firstElement.has("plain_text")) {
                        return firstElement.get("plain_text").asText();
                    }
                }
            }
        }
        return null;
    }

    /**
     * properties 객체에서 status 타입의 속성 값을 추출
     *
     * @param propertiesNode Notion API의 properties JSON 노드
     * @return status name, 없으면 null
     */
    public static String extractStatus(JsonNode propertiesNode) {
        if (propertiesNode == null || propertiesNode.isNull()) {
            return null;
        }

        Iterator<String> fieldNames = propertiesNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode property = propertiesNode.get(fieldName);

            if (property.has("type") && "status".equals(property.get("type").asText())) {
                JsonNode status = property.get("status");
                if (status != null && status.has("name")) {
                    return status.get("name").asText();
                }
            }
            if (property.has("type") && "select".equals(property.get("type").asText())) {
                JsonNode select = property.get("select");
                if (select != null && select.has("name")) {
                    return select.get("name").asText();
                }
            }
        }
        return null;
    }

    /**
     * properties 객체에서 date 타입의 속성 값을 추출
     *
     * @param propertiesNode Notion API의 properties JSON 노드
     * @return PageDateRange 객체, 날짜가 없으면 null
     */
    public static PageDateRange extractDateRange(JsonNode propertiesNode) {
        if (propertiesNode == null || propertiesNode.isNull()) {
            return null;
        }

        Iterator<String> fieldNames = propertiesNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode property = propertiesNode.get(fieldName);

            if (property.has("type") && "date".equals(property.get("type").asText())) {
                JsonNode date = property.get("date");
                if (date != null && !date.isNull()) {
                    String start = date.has("start") && !date.get("start").isNull()
                            ? date.get("start").asText()
                            : null;
                    String end = date.has("end") && !date.get("end").isNull()
                            ? date.get("end").asText()
                            : null;

                    // start와 end 둘 다 null이면 null 반환
                    if (start == null && end == null) {
                        return null;
                    }
                    return new PageDateRange(start, end);
                }
            }
        }
        return null;
    }

    /**
     * blocks 결과에서 paragraph 타입의 텍스트들을 추출하여 연결
     *
     * @param blocksNode Notion API의 blocks results JSON 노드
     * @return 연결된 텍스트, 없으면 빈 문자열
     */
    public static String extractParagraphText(JsonNode blocksNode) {
        if (blocksNode == null || !blocksNode.isArray()) {
            return "";
        }

        StringBuilder content = new StringBuilder();

        for (JsonNode block : blocksNode) {
            if (block.has("type") && "paragraph".equals(block.get("type").asText())) {
                JsonNode paragraph = block.get("paragraph");
                if (paragraph != null && paragraph.has("rich_text")) {
                    JsonNode richTextArray = paragraph.get("rich_text");
                    if (richTextArray.isArray()) {
                        for (JsonNode richText : richTextArray) {
                            if (richText.has("text") && richText.get("text").has("content")) {
                                content.append(richText.get("text").get("content").asText());
                            }
                        }
                    }
                }
            }
        }

        return content.toString();
    }

    /**
     * database 또는 page의 title 배열에서 제목 추출
     * (database GET 응답의 title 필드용)
     *
     * @param titleNode title 배열 JSON 노드
     * @return 제목 텍스트, 없으면 null
     */
    public static String extractTitleFromArray(JsonNode titleNode) {
        if (titleNode == null || !titleNode.isArray() || titleNode.size() == 0) {
            return null;
        }

        JsonNode firstElement = titleNode.get(0);

        // text.content를 먼저 시도
        if (firstElement.has("text") && firstElement.get("text").has("content")) {
            return firstElement.get("text").get("content").asText();
        }

        // plain_text를 fallback으로 시도
        if (firstElement.has("plain_text")) {
            return firstElement.get("plain_text").asText();
        }

        return null;
    }
}
