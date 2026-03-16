package pingpong.backend.domain.swaggerdiff.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.ChangedOpenApi;
import org.openapitools.openapidiff.core.model.ChangedOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.SwaggerSnapshot;
import pingpong.backend.domain.swagger.enums.CrudMethod;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.swagger.repository.SwaggerSnapshotRepository;
import pingpong.backend.domain.swaggerdiff.dto.*;
import pingpong.backend.domain.swaggerdiff.mapper.OpenApiDiffMapper;
import pingpong.backend.global.exception.CustomException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SwaggerDiffService {

	private final EndpointRepository endpointRepository;
	private final SwaggerSnapshotRepository swaggerSnapshotRepository;
	private final OpenApiDiffMapper openApiDiffMapper;

	// diff 리스트 (added / removed / modified / unchanged)
	public EndpointDiffListResponse getDiffList(Long teamId) {
		SwaggerSnapshot currSnapshot = swaggerSnapshotRepository
			.findTopByTeamIdOrderByIdDesc(teamId)
			.orElseThrow(() -> new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND));

		List<Endpoint> allEndpoints = endpointRepository.findBySnapshotId(currSnapshot.getId());

		Optional<SwaggerSnapshot> prevSnapshotOpt = swaggerSnapshotRepository
			.findTopByTeamIdAndIdLessThanOrderByIdDesc(teamId, currSnapshot.getId());

		if (prevSnapshotOpt.isEmpty() || prevSnapshotOpt.get().getRawJson() == null) {
			List<EndpointDiffListResponse.TagGroupDto> addedGroups = allEndpoints.stream()
				.map(EndpointSummaryDto::from)
				.collect(Collectors.groupingBy(
					dto -> Optional.ofNullable(dto.tag()).orElse("default"),
					LinkedHashMap::new,
					Collectors.toList()
				))
				.entrySet().stream()
				.map(e -> new EndpointDiffListResponse.TagGroupDto(e.getKey(), e.getValue()))
				.toList();
			return new EndpointDiffListResponse(addedGroups, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
		}

		ChangedOpenApi diff = compareSafely(prevSnapshotOpt.get().getRawJson(), currSnapshot.getRawJson());
		return openApiDiffMapper.toDiffList(diff, allEndpoints);
	}

	// 엔드포인트 단건 조회 (diff 포함)
	public EndpointDiffDetailDto getEndpointDiffDetail(Long endpointId) {
		Endpoint curr = endpointRepository.findById(endpointId)
			.orElseThrow(() -> new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND));

		SwaggerSnapshot currSnapshot = curr.getSnapshot();
		if (currSnapshot == null || currSnapshot.getRawJson() == null) {
			throw new CustomException(SwaggerErrorCode.JSON_PROCESSING_EXCEPTION);
		}

		String path = curr.getPath();
		CrudMethod method = curr.getMethod();

		OpenAPI currApi = parseOpenApi(currSnapshot.getRawJson());
		Operation currOperation = findOperation(currApi, path, method);
		Map<String, Schema> currSchemas = safeSchemas(currApi);

		Optional<SwaggerSnapshot> prevSnapshotOpt = swaggerSnapshotRepository
			.findTopByTeamIdAndIdLessThanOrderByIdDesc(
				currSnapshot.getTeam().getId(), currSnapshot.getId()
			);

		// 이전 스냅샷 없음 → 전체 ADDED
		if (prevSnapshotOpt.isEmpty() || prevSnapshotOpt.get().getRawJson() == null) {
			if (currOperation == null) {
				throw new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND);
			}
			return openApiDiffMapper.toAllAdded(curr, currOperation, currSchemas);
		}

		SwaggerSnapshot prevSnapshot = prevSnapshotOpt.get();
		ChangedOpenApi diff = compareSafely(prevSnapshot.getRawJson(), currSnapshot.getRawJson());

		// newEndpoints → ADDED
		for (org.openapitools.openapidiff.core.model.Endpoint ep :
				nullSafe(diff.getNewEndpoints())) {
			if (matchesEndpoint(ep, path, method)) {
				if (currOperation == null) {
					throw new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND);
				}
				return openApiDiffMapper.toAllAdded(curr, currOperation, currSchemas);
			}
		}

		// missingEndpoints → REMOVED
		for (org.openapitools.openapidiff.core.model.Endpoint ep :
				nullSafe(diff.getMissingEndpoints())) {
			if (matchesEndpoint(ep, path, method)) {
				OpenAPI prevApi = parseOpenApi(prevSnapshot.getRawJson());
				Operation prevOperation = findOperation(prevApi, path, method);
				if (prevOperation == null) {
					throw new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND);
				}
				return openApiDiffMapper.toAllRemoved(curr, prevOperation, safeSchemas(prevApi));
			}
		}

		// changedOperations → MODIFIED
		for (ChangedOperation changedOp : nullSafe(diff.getChangedOperations())) {
			if (matchesChangedOperation(changedOp, path, method)) {
				OpenAPI prevApi = parseOpenApi(prevSnapshot.getRawJson());
				return openApiDiffMapper.fromChangedOperation(
					curr, changedOp, safeSchemas(prevApi), currSchemas
				);
			}
		}

		// UNCHANGED
		if (currOperation == null) {
			throw new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND);
		}
		return openApiDiffMapper.toAllUnchanged(curr, currOperation, currSchemas);
	}

	// 엔드포인트 단건 통합 조회 (openapi-diff 기반 통일)
	public EndpointDiffDetailDto getEndpointUnifiedDetail(Long endpointId) {
		return getEndpointDiffDetail(endpointId);
	}

	private ChangedOpenApi compareSafely(String oldJson, String newJson) {
		try {
			return OpenApiCompare.fromContents(oldJson, newJson);
		} catch (Exception e) {
			throw new CustomException(SwaggerErrorCode.JSON_PROCESSING_EXCEPTION);
		}
	}

	private OpenAPI parseOpenApi(String json) {
		ParseOptions options = new ParseOptions();
		options.setResolve(true);
		SwaggerParseResult result = new OpenAPIV3Parser().readContents(json, null, options);
		if (result.getOpenAPI() == null) {
			throw new CustomException(SwaggerErrorCode.JSON_PROCESSING_EXCEPTION);
		}
		return result.getOpenAPI();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Map<String, Schema> safeSchemas(OpenAPI api) {
		if (api == null || api.getComponents() == null || api.getComponents().getSchemas() == null) {
			return Collections.emptyMap();
		}
		return (Map<String, Schema>) (Map<?, ?>) api.getComponents().getSchemas();
	}

	private Operation findOperation(OpenAPI api, String path, CrudMethod method) {
		if (api.getPaths() == null) return null;
		PathItem pathItem = api.getPaths().get(path);
		if (pathItem == null) return null;
		return switch (method) {
			case GET -> pathItem.getGet();
			case POST -> pathItem.getPost();
			case PUT -> pathItem.getPut();
			case DELETE -> pathItem.getDelete();
			case PATCH -> pathItem.getPatch();
		};
	}

	private boolean matchesEndpoint(
		org.openapitools.openapidiff.core.model.Endpoint ep, String path, CrudMethod method
	) {
		return ep.getPathUrl().equals(path)
			&& ep.getMethod().name().equalsIgnoreCase(method.name());
	}

	private boolean matchesChangedOperation(ChangedOperation op, String path, CrudMethod method) {
		return op.getPathUrl().equals(path)
			&& op.getHttpMethod().name().equalsIgnoreCase(method.name());
	}

	private <T> List<T> nullSafe(List<T> list) {
		return list != null ? list : Collections.emptyList();
	}

}
