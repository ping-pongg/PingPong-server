package pingpong.backend.domain.swaggerdiff.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.openapitools.openapidiff.core.model.ChangedOpenApi;
import org.openapitools.openapidiff.core.model.ChangedOperation;
import org.springframework.stereotype.Component;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swaggerdiff.dto.*;

@Component
@RequiredArgsConstructor
public class OpenApiDiffMapper {

	private final ParameterChangeMapper parameterChangeMapper;
	private final RequestBodyChangeMapper requestBodyChangeMapper;
	private final ResponseChangeMapper responseChangeMapper;
	private final SchemaChangeMapper schemaChangeMapper;

	// diff 리스트 매핑
	public EndpointDiffListResponse toDiffList(
		ChangedOpenApi diff,
		List<Endpoint> allEndpoints
	) {
		Set<String> addedKeys = new HashSet<>();
		Set<String> removedKeys = new HashSet<>();
		Set<String> modifiedKeys = new HashSet<>();

		for (org.openapitools.openapidiff.core.model.Endpoint ep : nullSafe(diff.getNewEndpoints())) {
			addedKeys.add(endpointKey(ep.getPathUrl(), ep.getMethod().name()));
		}
		for (org.openapitools.openapidiff.core.model.Endpoint ep : nullSafe(diff.getMissingEndpoints())) {
			removedKeys.add(endpointKey(ep.getPathUrl(), ep.getMethod().name()));
		}
		for (ChangedOperation op : nullSafe(diff.getChangedOperations())) {
			modifiedKeys.add(endpointKey(op.getPathUrl(), op.getHttpMethod().name()));
		}

		Map<DiffType, List<EndpointSummaryDto>> grouped = new LinkedHashMap<>();
		grouped.put(DiffType.ADDED, new ArrayList<>());
		grouped.put(DiffType.REMOVED, new ArrayList<>());
		grouped.put(DiffType.MODIFIED, new ArrayList<>());
		grouped.put(DiffType.UNCHANGED, new ArrayList<>());

		for (Endpoint e : allEndpoints) {
			String key = endpointKey(e.getPath(), e.getMethod().name());
			EndpointSummaryDto dto = EndpointSummaryDto.from(e);

			if (addedKeys.contains(key)) grouped.get(DiffType.ADDED).add(dto);
			else if (removedKeys.contains(key)) grouped.get(DiffType.REMOVED).add(dto);
			else if (modifiedKeys.contains(key)) grouped.get(DiffType.MODIFIED).add(dto);
			else grouped.get(DiffType.UNCHANGED).add(dto);
		}

		return new EndpointDiffListResponse(
			toTagGroups(grouped.get(DiffType.ADDED)),
			toTagGroups(grouped.get(DiffType.MODIFIED)),
			toTagGroups(grouped.get(DiffType.REMOVED)),
			toTagGroups(grouped.get(DiffType.UNCHANGED))
		);
	}

	// 단건 diff 상세: ALL ADDED / REMOVED / UNCHANGED
	public EndpointDiffDetailDto toAllAdded(Endpoint endpoint, Operation operation, Map<String, Schema> schemas) {
		return toStaticEndpointDetail(DiffType.ADDED, endpoint, operation, schemas);
	}

	public EndpointDiffDetailDto toAllRemoved(Endpoint endpoint, Operation operation, Map<String, Schema> schemas) {
		return toStaticEndpointDetail(DiffType.REMOVED, endpoint, operation, schemas);
	}

	public EndpointDiffDetailDto toAllUnchanged(Endpoint endpoint, Operation operation, Map<String, Schema> schemas) {
		return toStaticEndpointDetail(DiffType.UNCHANGED, endpoint, operation, schemas);
	}

	private EndpointDiffDetailDto toStaticEndpointDetail(
		DiffType type, Endpoint endpoint, Operation operation, Map<String, Schema> schemas
	) {
		List<ParameterChangeDto> params = parameterChangeMapper.mapStatic(type, operation.getParameters());

		RequestBodyChangeDto reqBody = operation.getRequestBody() != null
			? requestBodyChangeMapper.mapStatic(type, operation.getRequestBody(), schemas) : null;

		List<ResponseChangeDto> responses = new ArrayList<>();
		for (Map.Entry<String, ApiResponse> entry : nullSafeEntries(operation.getResponses())) {
			responses.add(responseChangeMapper.mapStatic(type, entry.getKey(), entry.getValue(), schemas));
		}

		String summary = operation.getSummary();
		String description = operation.getDescription();
		return new EndpointDiffDetailDto(
			type,
			endpoint.getId(),
			tagOrDefault(endpoint.getTag()),
			endpoint.getPath(),
			endpoint.getMethod(),
			new ChangedMetadataDto(type != DiffType.ADDED ? summary : null, type != DiffType.REMOVED ? summary : null),
			new ChangedMetadataDto(type != DiffType.ADDED ? description : null, type != DiffType.REMOVED ? description : null),
			endpoint.getOperationId(),
			false,
			params, reqBody, responses
		);
	}

	// 단건 diff 상세: FROM CHANGED OPERATION
	public EndpointDiffDetailDto fromChangedOperation(
		Endpoint endpoint,
		ChangedOperation changedOp,
		Map<String, Schema> prevSchemas,
		Map<String, Schema> currSchemas
	) {
		Operation newOp = changedOp.getNewOperation();
		return new EndpointDiffDetailDto(
			DiffType.MODIFIED,
			endpoint.getId(),
			tagOrDefault(endpoint.getTag()),
			endpoint.getPath(),
			endpoint.getMethod(),
			schemaChangeMapper.toChangedMetadataDto(changedOp.getSummary()),
			schemaChangeMapper.toChangedMetadataDto(changedOp.getDescription()),
			endpoint.getOperationId(),
			changedOp.isDeprecated(),
			parameterChangeMapper.mapModified(changedOp.getParameters(), newOp),
			requestBodyChangeMapper.mapModified(changedOp.getRequestBody(), prevSchemas, currSchemas),
			responseChangeMapper.mapModified(changedOp.getApiResponses(), newOp, prevSchemas, currSchemas)
		);
	}

	// 유틸리티
	private List<EndpointDiffListResponse.TagGroupDto> toTagGroups(List<EndpointSummaryDto> dtos) {
		return dtos.stream()
			.collect(Collectors.groupingBy(
				dto -> Optional.ofNullable(dto.tag()).orElse("default"),
				LinkedHashMap::new,
				Collectors.toList()
			))
			.entrySet().stream()
			.map(e -> new EndpointDiffListResponse.TagGroupDto(e.getKey(), e.getValue()))
			.toList();
	}

	private String endpointKey(String path, String method) {
		return method.toUpperCase() + " " + path;
	}

	private String tagOrDefault(String tag) {
		return tag != null ? tag : "default";
	}

	private <T> List<T> nullSafe(List<T> list) {
		return list != null ? list : Collections.emptyList();
	}

	private <K, V> Set<Map.Entry<K, V>> nullSafeEntries(Map<K, V> map) {
		return map != null ? map.entrySet() : Collections.emptySet();
	}
}
