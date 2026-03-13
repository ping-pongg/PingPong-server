package pingpong.backend.domain.swaggerdiff.service;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

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
import pingpong.backend.domain.swagger.dto.response.EndpointDiffDetailResponse;
import pingpong.backend.domain.swagger.enums.CrudMethod;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.swagger.repository.SwaggerSnapshotRepository;
import pingpong.backend.domain.swaggerdiff.mapper.OpenApiDiffMapper;
import pingpong.backend.global.exception.CustomException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SwaggerDiffService {

	private final EndpointRepository endpointRepository;
	private final SwaggerSnapshotRepository swaggerSnapshotRepository;
	private final OpenApiDiffMapper openApiDiffMapper;

	public EndpointDiffDetailResponse getEndpointDiffDetails(Long endpointId) {
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

		// Find previous snapshot for the same team
		Optional<SwaggerSnapshot> prevSnapshotOpt = swaggerSnapshotRepository
			.findTopByTeamIdAndIdLessThanOrderByIdDesc(
				currSnapshot.getTeam().getId(),
				currSnapshot.getId()
			);

		// No previous snapshot → all ADDED
		if (prevSnapshotOpt.isEmpty() || prevSnapshotOpt.get().getRawJson() == null) {
			if (currOperation == null) {
				throw new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND);
			}
			return openApiDiffMapper.toAllAdded(path, method, currOperation, currSchemas);
		}

		SwaggerSnapshot prevSnapshot = prevSnapshotOpt.get();

		// Compare using openapi-diff
		ChangedOpenApi diff;
		try {
			diff = OpenApiCompare.fromContents(
				prevSnapshot.getRawJson(),
				currSnapshot.getRawJson()
			);
		} catch (Exception e) {
			throw new CustomException(SwaggerErrorCode.JSON_PROCESSING_EXCEPTION);
		}

		// Check if endpoint is in newEndpoints (newly added)
		for (org.openapitools.openapidiff.core.model.Endpoint ep :
				Optional.ofNullable(diff.getNewEndpoints()).orElse(Collections.emptyList())) {
			if (matchesEndpoint(ep, path, method)) {
				if (currOperation == null) {
					throw new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND);
				}
				return openApiDiffMapper.toAllAdded(path, method, currOperation, currSchemas);
			}
		}

		// Check if endpoint is in missingEndpoints (deleted)
		for (org.openapitools.openapidiff.core.model.Endpoint ep :
				Optional.ofNullable(diff.getMissingEndpoints()).orElse(Collections.emptyList())) {
			if (matchesEndpoint(ep, path, method)) {
				OpenAPI prevApi = parseOpenApi(prevSnapshot.getRawJson());
				Operation prevOperation = findOperation(prevApi, path, method);
				if (prevOperation == null) {
					throw new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND);
				}
				return openApiDiffMapper.toAllRemoved(path, method, prevOperation, safeSchemas(prevApi));
			}
		}

		// Check if endpoint is in changedOperations
		for (ChangedOperation changedOp :
				Optional.ofNullable(diff.getChangedOperations()).orElse(Collections.emptyList())) {
			if (matchesChangedOperation(changedOp, path, method)) {
				OpenAPI prevApi = parseOpenApi(prevSnapshot.getRawJson());
				return openApiDiffMapper.fromChangedOperation(path, method, changedOp,
					safeSchemas(prevApi), currSchemas);
			}
		}

		// Endpoint exists in both but unchanged
		if (currOperation == null) {
			throw new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND);
		}
		return openApiDiffMapper.toAllUnchanged(path, method, currOperation, currSchemas);
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

	private boolean matchesEndpoint(org.openapitools.openapidiff.core.model.Endpoint ep, String path, CrudMethod method) {
		return ep.getPathUrl().equals(path)
			&& ep.getMethod().name().equalsIgnoreCase(method.name());
	}

	private boolean matchesChangedOperation(ChangedOperation op, String path, CrudMethod method) {
		return op.getPathUrl().equals(path)
			&& op.getHttpMethod().name().equalsIgnoreCase(method.name());
	}
}
