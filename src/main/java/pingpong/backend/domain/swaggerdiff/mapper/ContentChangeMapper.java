package pingpong.backend.domain.swaggerdiff.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openapitools.openapidiff.core.model.ChangedContent;
import org.openapitools.openapidiff.core.model.ChangedMediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.swaggerdiff.dto.ContentChangeDto;
import pingpong.backend.domain.swaggerdiff.dto.DiffType;

@Component
@RequiredArgsConstructor
public class ContentChangeMapper {

	private final SchemaNormalizer schemaNormalizer;
	private final SchemaChangeMapper schemaChangeMapper;

	public List<ContentChangeDto> mapChanged(
		ChangedContent content,
		Map<String, Schema> prevSchemas,
		Map<String, Schema> currSchemas
	) {
		List<ContentChangeDto> result = new ArrayList<>();
		if (content == null) return result;

		for (Map.Entry<String, MediaType> entry : nullSafeEntries(content.getIncreased())) {
			JsonNode schema = schemaNormalizer.normalize(entry.getValue(), currSchemas);
			result.add(new ContentChangeDto(DiffType.ADDED, entry.getKey(), null, schema, null));
		}
		for (Map.Entry<String, MediaType> entry : nullSafeEntries(content.getMissing())) {
			JsonNode schema = schemaNormalizer.normalize(entry.getValue(), prevSchemas);
			result.add(new ContentChangeDto(DiffType.REMOVED, entry.getKey(), schema, null, null));
		}
		for (Map.Entry<String, ChangedMediaType> entry : nullSafeEntries(content.getChanged())) {
			ChangedMediaType cmt = entry.getValue();
			JsonNode oldSchema = cmt.getOldSchema() != null
				? schemaNormalizer.normalize(cmt.getOldSchema(), prevSchemas) : null;
			JsonNode newSchema = cmt.getNewSchema() != null
				? schemaNormalizer.normalize(cmt.getNewSchema(), currSchemas) : null;
			result.add(new ContentChangeDto(
				DiffType.MODIFIED, entry.getKey(), oldSchema, newSchema,
				schemaChangeMapper.toSchemaChangeDto(cmt.getSchema())
			));
		}

		return result;
	}

	public Set<String> collectHandledKeys(ChangedContent content) {
		Set<String> keys = new HashSet<>();
		if (content.getIncreased() != null) keys.addAll(content.getIncreased().keySet());
		if (content.getMissing() != null) keys.addAll(content.getMissing().keySet());
		if (content.getChanged() != null) keys.addAll(content.getChanged().keySet());
		return keys;
	}

	private <K, V> Set<Map.Entry<K, V>> nullSafeEntries(Map<K, V> map) {
		return map != null ? map.entrySet() : Collections.emptySet();
	}
}
