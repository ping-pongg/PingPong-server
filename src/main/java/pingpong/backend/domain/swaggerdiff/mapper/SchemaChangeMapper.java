package pingpong.backend.domain.swaggerdiff.mapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openapitools.openapidiff.core.model.ChangedMetadata;
import org.openapitools.openapidiff.core.model.ChangedSchema;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.models.media.Schema;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.swaggerdiff.dto.ChangedListDto;
import pingpong.backend.domain.swaggerdiff.dto.ChangedMetadataDto;
import pingpong.backend.domain.swaggerdiff.dto.SchemaChangeDto;

@Component
@RequiredArgsConstructor
public class SchemaChangeMapper {

	private final SchemaNormalizer schemaNormalizer;

	@SuppressWarnings({"unchecked", "rawtypes"})
	public SchemaChangeDto toSchemaChangeDto(ChangedSchema changed) {
		if (changed == null) return null;

		JsonNode oldSchema = changed.getOldSchema() != null
			? schemaNormalizer.normalize(changed.getOldSchema(), Collections.emptyMap()) : null;
		JsonNode newSchema = changed.getNewSchema() != null
			? schemaNormalizer.normalize(changed.getNewSchema(), Collections.emptyMap()) : null;

		Map<String, JsonNode> addedProps = new LinkedHashMap<>();
		if (changed.getIncreasedProperties() != null) {
			for (Map.Entry<String, Schema<?>> entry :
					((Map<String, Schema<?>>) (Map) changed.getIncreasedProperties()).entrySet()) {
				addedProps.put(entry.getKey(), schemaNormalizer.normalize(entry.getValue(), Collections.emptyMap()));
			}
		}

		Map<String, JsonNode> removedProps = new LinkedHashMap<>();
		if (changed.getMissingProperties() != null) {
			for (Map.Entry<String, Schema<?>> entry :
					((Map<String, Schema<?>>) (Map) changed.getMissingProperties()).entrySet()) {
				removedProps.put(entry.getKey(), schemaNormalizer.normalize(entry.getValue(), Collections.emptyMap()));
			}
		}

		Map<String, SchemaChangeDto> changedProps = new LinkedHashMap<>();
		if (changed.getChangedProperties() != null) {
			for (Map.Entry<String, ChangedSchema> entry : changed.getChangedProperties().entrySet()) {
				changedProps.put(entry.getKey(), toSchemaChangeDto(entry.getValue()));
			}
		}

		ChangedListDto requiredChanges = (changed.getRequired() != null && changed.getRequired().isDifferent())
			? toChangedListDto(changed.getRequired()) : null;

		ChangedListDto enumChanges = (changed.getEnumeration() != null && changed.getEnumeration().isDifferent())
			? toChangedListDto(changed.getEnumeration()) : null;

		return new SchemaChangeDto(
			oldSchema, newSchema,
			addedProps, removedProps, changedProps,
			toChangedMetadataDto(changed.getDescription()),
			requiredChanges,
			enumChanges,
			toSchemaChangeDto(changed.getItems())
		);
	}

	public ChangedMetadataDto toChangedMetadataDto(ChangedMetadata metadata) {
		if (metadata == null) return null;
		return new ChangedMetadataDto(metadata.getLeft(), metadata.getRight());
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public ChangedListDto toChangedListDto(org.openapitools.openapidiff.core.model.ChangedList<?> list) {
		if (list == null) return null;
		List<String> added = list.getIncreased() != null
			? ((List<Object>) (List) list.getIncreased()).stream().map(String::valueOf).toList()
			: Collections.emptyList();
		List<String> removed = list.getMissing() != null
			? ((List<Object>) (List) list.getMissing()).stream().map(String::valueOf).toList()
			: Collections.emptyList();
		return new ChangedListDto(added, removed);
	}
}
