package pingpong.backend.domain.swaggerdiff.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.openapitools.openapidiff.core.model.ChangedRequestBody;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.swaggerdiff.dto.ContentChangeDto;
import pingpong.backend.domain.swaggerdiff.dto.DiffType;
import pingpong.backend.domain.swaggerdiff.dto.RequestBodyChangeDto;

@Component
@RequiredArgsConstructor
public class RequestBodyChangeMapper {

	private final ContentChangeMapper contentChangeMapper;
	private final SchemaNormalizer schemaNormalizer;
	private final SchemaChangeMapper schemaChangeMapper;

	public RequestBodyChangeDto mapModified(
		ChangedRequestBody changed,
		Map<String, Schema> prevSchemas,
		Map<String, Schema> currSchemas
	) {
		if (changed == null) return null;

		Boolean oldRequired = changed.getOldRequestBody() != null
			? changed.getOldRequestBody().getRequired() : null;
		Boolean newRequired = changed.getNewRequestBody() != null
			? changed.getNewRequestBody().getRequired() : null;

		List<ContentChangeDto> contentChanges = contentChangeMapper.mapChanged(
			changed.getContent(), prevSchemas, currSchemas
		);

		if (changed.getContent() != null) {
			Set<String> handledKeys = contentChangeMapper.collectHandledKeys(changed.getContent());
			RequestBody newRb = changed.getNewRequestBody();
			RequestBody oldRb = changed.getOldRequestBody();

			if (newRb != null && newRb.getContent() != null) {
				boolean requiredChanged = !Objects.equals(oldRequired, newRequired);
				for (Map.Entry<String, MediaType> entry : newRb.getContent().entrySet()) {
					if (!handledKeys.contains(entry.getKey())) {
						JsonNode schema = schemaNormalizer.normalize(entry.getValue(), currSchemas);
						if (requiredChanged) {
							JsonNode oldSchema = oldRb != null && oldRb.getContent() != null
								&& oldRb.getContent().get(entry.getKey()) != null
								? schemaNormalizer.normalize(oldRb.getContent().get(entry.getKey()), prevSchemas)
								: schema;
							contentChanges.add(new ContentChangeDto(
								DiffType.MODIFIED, entry.getKey(), oldSchema, schema, null
							));
						} else {
							contentChanges.add(new ContentChangeDto(
								DiffType.UNCHANGED, entry.getKey(), schema, schema, null
							));
						}
					}
				}
			}
		}

		return new RequestBodyChangeDto(
			oldRequired, newRequired,
			schemaChangeMapper.toChangedMetadataDto(changed.getDescription()),
			contentChanges
		);
	}

	public RequestBodyChangeDto mapStatic(DiffType type, RequestBody rb, Map<String, Schema> schemas) {
		List<ContentChangeDto> contents = new ArrayList<>();
		for (Map.Entry<String, MediaType> entry : nullSafeEntries(rb.getContent())) {
			JsonNode schema = schemaNormalizer.normalize(entry.getValue(), schemas);
			contents.add(new ContentChangeDto(type, entry.getKey(),
				type != DiffType.ADDED ? schema : null,
				type != DiffType.REMOVED ? schema : null, null));
		}
		Boolean req = rb.getRequired();
		return new RequestBodyChangeDto(
			type != DiffType.ADDED ? req : null,
			type != DiffType.REMOVED ? req : null,
			null, contents);
	}

	private <K, V> Set<Map.Entry<K, V>> nullSafeEntries(Map<K, V> map) {
		return map != null ? map.entrySet() : Collections.emptySet();
	}
}
