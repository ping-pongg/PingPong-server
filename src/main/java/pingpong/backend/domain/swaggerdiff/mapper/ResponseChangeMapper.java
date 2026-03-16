package pingpong.backend.domain.swaggerdiff.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openapitools.openapidiff.core.model.ChangedApiResponse;
import org.openapitools.openapidiff.core.model.ChangedResponse;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.swaggerdiff.dto.ChangedMetadataDto;
import pingpong.backend.domain.swaggerdiff.dto.ContentChangeDto;
import pingpong.backend.domain.swaggerdiff.dto.DiffType;
import pingpong.backend.domain.swaggerdiff.dto.ResponseChangeDto;

@Component
@RequiredArgsConstructor
public class ResponseChangeMapper {

	private final ContentChangeMapper contentChangeMapper;
	private final SchemaNormalizer schemaNormalizer;
	private final SchemaChangeMapper schemaChangeMapper;

	public List<ResponseChangeDto> mapModified(
		ChangedApiResponse changedApiResponse,
		Operation newOp,
		Map<String, Schema> prevSchemas,
		Map<String, Schema> currSchemas
	) {
		List<ResponseChangeDto> result = new ArrayList<>();
		Set<String> handledKeys = new HashSet<>();

		if (changedApiResponse != null) {
			for (Map.Entry<String, ApiResponse> entry : nullSafeEntries(changedApiResponse.getIncreased())) {
				result.add(mapStatic(DiffType.ADDED, entry.getKey(), entry.getValue(), currSchemas));
				handledKeys.add(entry.getKey());
			}
			for (Map.Entry<String, ApiResponse> entry : nullSafeEntries(changedApiResponse.getMissing())) {
				result.add(mapStatic(DiffType.REMOVED, entry.getKey(), entry.getValue(), prevSchemas));
			}
			for (Map.Entry<String, ChangedResponse> entry : nullSafeEntries(changedApiResponse.getChanged())) {
				result.add(mapChangedResponse(entry.getKey(), entry.getValue(), prevSchemas, currSchemas));
				handledKeys.add(entry.getKey());
			}
		}

		if (newOp != null) {
			for (Map.Entry<String, ApiResponse> entry : nullSafeEntries(newOp.getResponses())) {
				if (!handledKeys.contains(entry.getKey())) {
					result.add(mapStatic(DiffType.UNCHANGED, entry.getKey(), entry.getValue(), currSchemas));
				}
			}
		}

		return result;
	}

	public ResponseChangeDto mapStatic(DiffType type, String statusCode, ApiResponse resp, Map<String, Schema> schemas) {
		List<ContentChangeDto> contents = new ArrayList<>();
		for (Map.Entry<String, MediaType> entry : nullSafeEntries(resp.getContent())) {
			JsonNode schema = schemaNormalizer.normalize(entry.getValue(), schemas);
			contents.add(new ContentChangeDto(type, entry.getKey(),
				type != DiffType.ADDED ? schema : null,
				type != DiffType.REMOVED ? schema : null, null));
		}
		String desc = resp.getDescription();
		return new ResponseChangeDto(type, statusCode,
			new ChangedMetadataDto(
				type != DiffType.ADDED ? desc : null,
				type != DiffType.REMOVED ? desc : null),
			contents);
	}

	private ResponseChangeDto mapChangedResponse(
		String statusCode, ChangedResponse changedResp,
		Map<String, Schema> prevSchemas, Map<String, Schema> currSchemas
	) {
		List<ContentChangeDto> contentChanges = contentChangeMapper.mapChanged(
			changedResp.getContent(), prevSchemas, currSchemas
		);

		if (changedResp.getContent() != null) {
			Set<String> handledKeys = contentChangeMapper.collectHandledKeys(changedResp.getContent());
			ApiResponse newResp = changedResp.getNewApiResponse();
			for (Map.Entry<String, MediaType> entry : nullSafeEntries(newResp != null ? newResp.getContent() : null)) {
				if (!handledKeys.contains(entry.getKey())) {
					JsonNode schema = schemaNormalizer.normalize(entry.getValue(), currSchemas);
					contentChanges.add(new ContentChangeDto(DiffType.UNCHANGED, entry.getKey(), schema, schema, null));
				}
			}
		}

		return new ResponseChangeDto(
			DiffType.MODIFIED, statusCode,
			schemaChangeMapper.toChangedMetadataDto(changedResp.getDescription()),
			contentChanges
		);
	}

	private <K, V> Set<Map.Entry<K, V>> nullSafeEntries(Map<K, V> map) {
		return map != null ? map.entrySet() : Collections.emptySet();
	}
}
