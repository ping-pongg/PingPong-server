package pingpong.backend.domain.swaggerdiff.mapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openapitools.openapidiff.core.model.ChangedMediaType;
import org.openapitools.openapidiff.core.model.ChangedOperation;
import org.openapitools.openapidiff.core.model.ChangedParameter;
import org.openapitools.openapidiff.core.model.ChangedParameters;
import org.openapitools.openapidiff.core.model.ChangedRequestBody;
import org.openapitools.openapidiff.core.model.ChangedApiResponse;
import org.openapitools.openapidiff.core.model.ChangedContent;
import org.openapitools.openapidiff.core.model.ChangedResponse;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.swagger.dto.request.SnapshotRequest;
import pingpong.backend.domain.swagger.dto.response.EndpointDiffDetailResponse;
import pingpong.backend.domain.swagger.dto.response.ParameterResponse;
import pingpong.backend.domain.swagger.dto.response.ParameterSnapshotResponse;
import pingpong.backend.domain.swagger.dto.response.RequestBodyResponse;
import pingpong.backend.domain.swagger.dto.response.ResponseBodyResponse;
import pingpong.backend.domain.swagger.dto.response.SnapshotResponse;
import pingpong.backend.domain.swagger.enums.CrudMethod;
import pingpong.backend.domain.swagger.enums.DiffType;

@Component
@RequiredArgsConstructor
public class OpenApiDiffMapper {

	private final ObjectMapper objectMapper;

	// ── Public API ──

	public EndpointDiffDetailResponse toAllAdded(
		String path, CrudMethod method,
		io.swagger.v3.oas.models.Operation operation,
		Map<String, Schema> schemas
	) {
		List<ParameterResponse> params = new ArrayList<>();
		if (operation.getParameters() != null) {
			for (Parameter p : operation.getParameters()) {
				params.add(new ParameterResponse(DiffType.ADDED, null, toParameterSnapshot(p)));
			}
		}

		List<RequestBodyResponse> requests = new ArrayList<>();
		if (operation.getRequestBody() != null) {
			requests.addAll(mapRequestBodyAdded(operation.getRequestBody(), schemas));
		}

		List<ResponseBodyResponse> responses = new ArrayList<>();
		if (operation.getResponses() != null) {
			for (Map.Entry<String, ApiResponse> entry : operation.getResponses().entrySet()) {
				responses.addAll(mapApiResponseAdded(entry.getKey(), entry.getValue(), schemas));
			}
		}

		return new EndpointDiffDetailResponse(path, method, params, requests, responses);
	}

	public EndpointDiffDetailResponse toAllRemoved(
		String path, CrudMethod method,
		io.swagger.v3.oas.models.Operation operation,
		Map<String, Schema> schemas
	) {
		List<ParameterResponse> params = new ArrayList<>();
		if (operation.getParameters() != null) {
			for (Parameter p : operation.getParameters()) {
				params.add(new ParameterResponse(DiffType.REMOVED, toParameterSnapshot(p), null));
			}
		}

		List<RequestBodyResponse> requests = new ArrayList<>();
		if (operation.getRequestBody() != null) {
			requests.addAll(mapRequestBodyRemoved(operation.getRequestBody(), schemas));
		}

		List<ResponseBodyResponse> responses = new ArrayList<>();
		if (operation.getResponses() != null) {
			for (Map.Entry<String, ApiResponse> entry : operation.getResponses().entrySet()) {
				responses.addAll(mapApiResponseRemoved(entry.getKey(), entry.getValue(), schemas));
			}
		}

		return new EndpointDiffDetailResponse(path, method, params, requests, responses);
	}

	public EndpointDiffDetailResponse toAllUnchanged(
		String path, CrudMethod method,
		io.swagger.v3.oas.models.Operation operation,
		Map<String, Schema> schemas
	) {
		List<ParameterResponse> params = new ArrayList<>();
		if (operation.getParameters() != null) {
			for (Parameter p : operation.getParameters()) {
				ParameterSnapshotResponse snapshot = toParameterSnapshot(p);
				params.add(new ParameterResponse(DiffType.UNCHANGED, snapshot, snapshot));
			}
		}

		List<RequestBodyResponse> requests = new ArrayList<>();
		if (operation.getRequestBody() != null) {
			requests.addAll(mapRequestBodyUnchanged(operation.getRequestBody(), schemas));
		}

		List<ResponseBodyResponse> responses = new ArrayList<>();
		if (operation.getResponses() != null) {
			for (Map.Entry<String, ApiResponse> entry : operation.getResponses().entrySet()) {
				responses.addAll(mapApiResponseUnchanged(entry.getKey(), entry.getValue(), schemas));
			}
		}

		return new EndpointDiffDetailResponse(path, method, params, requests, responses);
	}

	public EndpointDiffDetailResponse fromChangedOperation(
		String path, CrudMethod method,
		ChangedOperation changedOperation,
		Map<String, Schema> prevSchemas,
		Map<String, Schema> currSchemas
	) {
		io.swagger.v3.oas.models.Operation newOp = changedOperation.getNewOperation();

		List<ParameterResponse> params = mapParameters(changedOperation.getParameters(), newOp);
		List<RequestBodyResponse> requests = mapRequestBody(changedOperation.getRequestBody(), prevSchemas, currSchemas);
		List<ResponseBodyResponse> responses = mapResponses(changedOperation.getApiResponses(), newOp, prevSchemas, currSchemas);

		return new EndpointDiffDetailResponse(path, method, params, requests, responses);
	}

	// ── Parameters ──

	private List<ParameterResponse> mapParameters(
		ChangedParameters changedParams,
		io.swagger.v3.oas.models.Operation newOp
	) {
		List<ParameterResponse> result = new ArrayList<>();
		Set<String> handledKeys = new HashSet<>();

		if (changedParams != null) {
			if (changedParams.getIncreased() != null) {
				for (Parameter p : changedParams.getIncreased()) {
					result.add(new ParameterResponse(DiffType.ADDED, null, toParameterSnapshot(p)));
					handledKeys.add(p.getName() + "|" + p.getIn());
				}
			}

			if (changedParams.getMissing() != null) {
				for (Parameter p : changedParams.getMissing()) {
					result.add(new ParameterResponse(DiffType.REMOVED, toParameterSnapshot(p), null));
					handledKeys.add(p.getName() + "|" + p.getIn());
				}
			}

			if (changedParams.getChanged() != null) {
				for (ChangedParameter cp : changedParams.getChanged()) {
					result.add(new ParameterResponse(
						DiffType.MODIFIED,
						toParameterSnapshot(cp.getOldParameter()),
						toParameterSnapshot(cp.getNewParameter())
					));
					handledKeys.add(cp.getNewParameter().getName() + "|" + cp.getNewParameter().getIn());
				}
			}
		}

		// UNCHANGED: parameters in newOp not covered by added/removed/modified
		if (newOp != null && newOp.getParameters() != null) {
			for (Parameter p : newOp.getParameters()) {
				if (!handledKeys.contains(p.getName() + "|" + p.getIn())) {
					ParameterSnapshotResponse snapshot = toParameterSnapshot(p);
					result.add(new ParameterResponse(DiffType.UNCHANGED, snapshot, snapshot));
				}
			}
		}

		return result;
	}

	private ParameterSnapshotResponse toParameterSnapshot(Parameter p) {
		if (p == null) return null;

		String type = extractType(p.getSchema());
		return new ParameterSnapshotResponse(
			p.getName(),
			p.getIn(),
			type,
			p.getRequired(),
			p.getDescription()
		);
	}

	// ── Request Body ──

	private List<RequestBodyResponse> mapRequestBody(
		ChangedRequestBody changedRequestBody,
		Map<String, Schema> prevSchemas,
		Map<String, Schema> currSchemas
	) {
		List<RequestBodyResponse> result = new ArrayList<>();
		if (changedRequestBody == null) {
			return result;
		}

		ChangedContent content = changedRequestBody.getContent();
		if (content == null) {
			return result;
		}

		Boolean oldRequired = changedRequestBody.getOldRequestBody() != null
			? changedRequestBody.getOldRequestBody().getRequired() : null;
		Boolean newRequired = changedRequestBody.getNewRequestBody() != null
			? changedRequestBody.getNewRequestBody().getRequired() : null;
		boolean requiredChanged = !java.util.Objects.equals(oldRequired, newRequired);

		if (content.getIncreased() != null) {
			for (Map.Entry<String, MediaType> entry : content.getIncreased().entrySet()) {
				result.add(new RequestBodyResponse(
					DiffType.ADDED, null,
					toSnapshotRequest(entry.getKey(), entry.getValue(), newRequired, currSchemas)
				));
			}
		}

		if (content.getMissing() != null) {
			for (Map.Entry<String, MediaType> entry : content.getMissing().entrySet()) {
				result.add(new RequestBodyResponse(
					DiffType.REMOVED,
					toSnapshotRequest(entry.getKey(), entry.getValue(), oldRequired, prevSchemas),
					null
				));
			}
		}

		if (content.getChanged() != null) {
			for (Map.Entry<String, ChangedMediaType> entry : content.getChanged().entrySet()) {
				result.add(new RequestBodyResponse(
					DiffType.MODIFIED,
					toSnapshotRequest(entry.getKey(), entry.getValue().getOldSchema(), oldRequired, prevSchemas),
					toSnapshotRequest(entry.getKey(), entry.getValue().getNewSchema(), newRequired, currSchemas)
				));
			}
		}

		// UNCHANGED (or MODIFIED-required): media types in newRb not covered above
		Set<String> handledKeys = new HashSet<>();
		if (content.getIncreased() != null) handledKeys.addAll(content.getIncreased().keySet());
		if (content.getMissing() != null) handledKeys.addAll(content.getMissing().keySet());
		if (content.getChanged() != null) handledKeys.addAll(content.getChanged().keySet());

		RequestBody newRb = changedRequestBody.getNewRequestBody();
		RequestBody oldRb = changedRequestBody.getOldRequestBody();
		if (newRb != null && newRb.getContent() != null) {
			for (Map.Entry<String, MediaType> entry : newRb.getContent().entrySet()) {
				if (!handledKeys.contains(entry.getKey())) {
					if (requiredChanged) {
						MediaType oldMt = (oldRb != null && oldRb.getContent() != null)
							? oldRb.getContent().get(entry.getKey()) : null;
						result.add(new RequestBodyResponse(
							DiffType.MODIFIED,
							toSnapshotRequest(entry.getKey(), oldMt != null ? oldMt : entry.getValue(), oldRequired, prevSchemas),
							toSnapshotRequest(entry.getKey(), entry.getValue(), newRequired, currSchemas)
						));
					} else {
						SnapshotRequest snapshot = toSnapshotRequest(entry.getKey(), entry.getValue(), newRequired, currSchemas);
						result.add(new RequestBodyResponse(DiffType.UNCHANGED, snapshot, snapshot));
					}
				}
			}
		}

		return result;
	}

	private List<RequestBodyResponse> mapRequestBodyAdded(RequestBody requestBody, Map<String, Schema> schemas) {
		List<RequestBodyResponse> result = new ArrayList<>();
		if (requestBody.getContent() == null) return result;

		for (Map.Entry<String, MediaType> entry : requestBody.getContent().entrySet()) {
			result.add(new RequestBodyResponse(
				DiffType.ADDED, null,
				toSnapshotRequest(entry.getKey(), entry.getValue(), requestBody.getRequired(), schemas)
			));
		}
		return result;
	}

	private List<RequestBodyResponse> mapRequestBodyRemoved(RequestBody requestBody, Map<String, Schema> schemas) {
		List<RequestBodyResponse> result = new ArrayList<>();
		if (requestBody.getContent() == null) return result;

		for (Map.Entry<String, MediaType> entry : requestBody.getContent().entrySet()) {
			result.add(new RequestBodyResponse(
				DiffType.REMOVED,
				toSnapshotRequest(entry.getKey(), entry.getValue(), requestBody.getRequired(), schemas),
				null
			));
		}
		return result;
	}

	private List<RequestBodyResponse> mapRequestBodyUnchanged(RequestBody requestBody, Map<String, Schema> schemas) {
		List<RequestBodyResponse> result = new ArrayList<>();
		if (requestBody.getContent() == null) return result;

		for (Map.Entry<String, MediaType> entry : requestBody.getContent().entrySet()) {
			SnapshotRequest snapshot = toSnapshotRequest(entry.getKey(), entry.getValue(), requestBody.getRequired(), schemas);
			result.add(new RequestBodyResponse(DiffType.UNCHANGED, snapshot, snapshot));
		}
		return result;
	}

	private SnapshotRequest toSnapshotRequest(String mediaTypeKey, MediaType mediaType, Boolean required, Map<String, Schema> schemas) {
		JsonNode schemaNode = mediaType != null && mediaType.getSchema() != null
			? normalizeSchema(mediaType.getSchema(), schemas)
			: null;
		return new SnapshotRequest(mediaTypeKey, required, schemaNode);
	}

	private SnapshotRequest toSnapshotRequest(String mediaTypeKey, Schema<?> schema, Boolean required, Map<String, Schema> schemas) {
		JsonNode schemaNode = schema != null ? normalizeSchema(schema, schemas) : null;
		return new SnapshotRequest(mediaTypeKey, required, schemaNode);
	}

	// ── Responses ──

	private List<ResponseBodyResponse> mapResponses(
		ChangedApiResponse changedApiResponse,
		io.swagger.v3.oas.models.Operation newOp,
		Map<String, Schema> prevSchemas,
		Map<String, Schema> currSchemas
	) {
		List<ResponseBodyResponse> result = new ArrayList<>();
		Set<String> handledKeys = new HashSet<>();

		if (changedApiResponse != null) {
			if (changedApiResponse.getIncreased() != null) {
				for (Map.Entry<String, ApiResponse> entry : changedApiResponse.getIncreased().entrySet()) {
					result.addAll(mapApiResponseAdded(entry.getKey(), entry.getValue(), currSchemas));
					handledKeys.add(entry.getKey());
				}
			}

			if (changedApiResponse.getMissing() != null) {
				for (Map.Entry<String, ApiResponse> entry : changedApiResponse.getMissing().entrySet()) {
					result.addAll(mapApiResponseRemoved(entry.getKey(), entry.getValue(), prevSchemas));
				}
			}

			if (changedApiResponse.getChanged() != null) {
				for (Map.Entry<String, ChangedResponse> entry : changedApiResponse.getChanged().entrySet()) {
					result.addAll(mapChangedResponse(entry.getKey(), entry.getValue(), prevSchemas, currSchemas));
					handledKeys.add(entry.getKey());
				}
			}
		}

		// UNCHANGED: status codes in newOp not covered by added/modified
		if (newOp != null && newOp.getResponses() != null) {
			for (Map.Entry<String, ApiResponse> entry : newOp.getResponses().entrySet()) {
				if (!handledKeys.contains(entry.getKey())) {
					result.addAll(mapApiResponseUnchanged(entry.getKey(), entry.getValue(), currSchemas));
				}
			}
		}

		return result;
	}

	private List<ResponseBodyResponse> mapChangedResponse(
		String statusCode, ChangedResponse changedResponse,
		Map<String, Schema> prevSchemas, Map<String, Schema> currSchemas
	) {
		List<ResponseBodyResponse> result = new ArrayList<>();

		ChangedContent content = changedResponse.getContent();
		if (content == null) {
			ApiResponse oldResp = changedResponse.getOldApiResponse();
			ApiResponse newResp = changedResponse.getNewApiResponse();
			result.add(new ResponseBodyResponse(
				DiffType.MODIFIED,
				toSnapshotResponse(statusCode, null, oldResp),
				toSnapshotResponse(statusCode, null, newResp)
			));
			return result;
		}

		if (content.getIncreased() != null) {
			for (Map.Entry<String, MediaType> entry : content.getIncreased().entrySet()) {
				result.add(new ResponseBodyResponse(
					DiffType.ADDED, null,
					toSnapshotResponse(statusCode, entry.getKey(), entry.getValue(),
						changedResponse.getNewApiResponse(), currSchemas)
				));
			}
		}

		if (content.getMissing() != null) {
			for (Map.Entry<String, MediaType> entry : content.getMissing().entrySet()) {
				result.add(new ResponseBodyResponse(
					DiffType.REMOVED,
					toSnapshotResponse(statusCode, entry.getKey(), entry.getValue(),
						changedResponse.getOldApiResponse(), prevSchemas),
					null
				));
			}
		}

		if (content.getChanged() != null) {
			for (Map.Entry<String, ChangedMediaType> entry : content.getChanged().entrySet()) {
				result.add(new ResponseBodyResponse(
					DiffType.MODIFIED,
					new SnapshotResponse(statusCode, entry.getKey(),
						changedResponse.getOldApiResponse() != null
							? changedResponse.getOldApiResponse().getDescription() : null,
						entry.getValue().getOldSchema() != null
							? normalizeSchema(entry.getValue().getOldSchema(), prevSchemas) : null),
					new SnapshotResponse(statusCode, entry.getKey(),
						changedResponse.getNewApiResponse() != null
							? changedResponse.getNewApiResponse().getDescription() : null,
						entry.getValue().getNewSchema() != null
							? normalizeSchema(entry.getValue().getNewSchema(), currSchemas) : null)
				));
			}
		}

		return result;
	}

	private List<ResponseBodyResponse> mapApiResponseAdded(String statusCode, ApiResponse apiResponse, Map<String, Schema> schemas) {
		List<ResponseBodyResponse> result = new ArrayList<>();
		if (apiResponse.getContent() == null || apiResponse.getContent().isEmpty()) {
			result.add(new ResponseBodyResponse(
				DiffType.ADDED, null,
				toSnapshotResponse(statusCode, null, apiResponse)
			));
		} else {
			for (Map.Entry<String, MediaType> entry : apiResponse.getContent().entrySet()) {
				result.add(new ResponseBodyResponse(
					DiffType.ADDED, null,
					toSnapshotResponse(statusCode, entry.getKey(), entry.getValue(), apiResponse, schemas)
				));
			}
		}
		return result;
	}

	private List<ResponseBodyResponse> mapApiResponseRemoved(String statusCode, ApiResponse apiResponse, Map<String, Schema> schemas) {
		List<ResponseBodyResponse> result = new ArrayList<>();
		if (apiResponse.getContent() == null || apiResponse.getContent().isEmpty()) {
			result.add(new ResponseBodyResponse(
				DiffType.REMOVED,
				toSnapshotResponse(statusCode, null, apiResponse),
				null
			));
		} else {
			for (Map.Entry<String, MediaType> entry : apiResponse.getContent().entrySet()) {
				result.add(new ResponseBodyResponse(
					DiffType.REMOVED,
					toSnapshotResponse(statusCode, entry.getKey(), entry.getValue(), apiResponse, schemas),
					null
				));
			}
		}
		return result;
	}

	private List<ResponseBodyResponse> mapApiResponseUnchanged(String statusCode, ApiResponse apiResponse, Map<String, Schema> schemas) {
		List<ResponseBodyResponse> result = new ArrayList<>();
		if (apiResponse.getContent() == null || apiResponse.getContent().isEmpty()) {
			SnapshotResponse snapshot = toSnapshotResponse(statusCode, null, apiResponse);
			result.add(new ResponseBodyResponse(DiffType.UNCHANGED, snapshot, snapshot));
		} else {
			for (Map.Entry<String, MediaType> entry : apiResponse.getContent().entrySet()) {
				SnapshotResponse snapshot = toSnapshotResponse(statusCode, entry.getKey(), entry.getValue(), apiResponse, schemas);
				result.add(new ResponseBodyResponse(DiffType.UNCHANGED, snapshot, snapshot));
			}
		}
		return result;
	}

	private SnapshotResponse toSnapshotResponse(String statusCode, String mediaTypeKey, ApiResponse apiResponse) {
		return new SnapshotResponse(
			statusCode,
			mediaTypeKey,
			apiResponse != null ? apiResponse.getDescription() : null,
			null
		);
	}

	private SnapshotResponse toSnapshotResponse(
		String statusCode, String mediaTypeKey, MediaType mediaType, ApiResponse apiResponse,
		Map<String, Schema> schemas
	) {
		JsonNode schemaNode = mediaType != null && mediaType.getSchema() != null
			? normalizeSchema(mediaType.getSchema(), schemas)
			: null;
		return new SnapshotResponse(
			statusCode,
			mediaTypeKey,
			apiResponse != null ? apiResponse.getDescription() : null,
			schemaNode
		);
	}

	// ── Schema Normalization ──

	@SuppressWarnings({"unchecked", "rawtypes"})
	private JsonNode normalizeSchema(Schema<?> schema, Map<String, Schema> allSchemas) {
		if (schema == null) return null;

		// Resolve $ref
		if (schema.get$ref() != null) {
			String refName = extractSchemaName(schema.get$ref());
			if (refName != null && allSchemas != null) {
				Schema resolved = allSchemas.get(refName);
				if (resolved != null) {
					return normalizeSchema(resolved, allSchemas);
				}
			}
			// Cannot resolve — return $ref only
			ObjectNode refNode = objectMapper.createObjectNode();
			refNode.put("$ref", schema.get$ref());
			return refNode;
		}

		ObjectNode node = objectMapper.createObjectNode();

		if (schema.getType() != null) node.put("type", schema.getType());
		if (schema.getFormat() != null) node.put("format", schema.getFormat());
		if (schema.getDescription() != null) node.put("description", schema.getDescription());

		// required (List<String>)
		if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
			ArrayNode reqArr = objectMapper.createArrayNode();
			schema.getRequired().forEach(reqArr::add);
			node.set("required", reqArr);
		}

		// properties
		if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
			ObjectNode propsNode = objectMapper.createObjectNode();
			for (Map.Entry<String, Schema> entry : ((Map<String, Schema>) (Map<?, ?>) schema.getProperties()).entrySet()) {
				JsonNode propNode = normalizeSchema(entry.getValue(), allSchemas);
				if (propNode != null) propsNode.set(entry.getKey(), propNode);
			}
			node.set("properties", propsNode);
		}

		// items (for arrays)
		if (schema.getItems() != null) {
			JsonNode itemsNode = normalizeSchema(schema.getItems(), allSchemas);
			if (itemsNode != null) node.set("items", itemsNode);
		}

		// oneOf
		if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
			ArrayNode arr = objectMapper.createArrayNode();
			for (Schema s : (List<Schema>) (List<?>) schema.getOneOf()) {
				JsonNode n = normalizeSchema(s, allSchemas);
				if (n != null) arr.add(n);
			}
			node.set("oneOf", arr);
		}

		// anyOf
		if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
			ArrayNode arr = objectMapper.createArrayNode();
			for (Schema s : (List<Schema>) (List<?>) schema.getAnyOf()) {
				JsonNode n = normalizeSchema(s, allSchemas);
				if (n != null) arr.add(n);
			}
			node.set("anyOf", arr);
		}

		// allOf
		if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
			ArrayNode arr = objectMapper.createArrayNode();
			for (Schema s : (List<Schema>) (List<?>) schema.getAllOf()) {
				JsonNode n = normalizeSchema(s, allSchemas);
				if (n != null) arr.add(n);
			}
			node.set("allOf", arr);
		}

		// enum
		if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
			ArrayNode arr = objectMapper.createArrayNode();
			for (Object e : schema.getEnum()) {
				if (e != null) arr.add(e.toString());
			}
			node.set("enum", arr);
		}

		return node.isEmpty() ? null : node;
	}

	private String extractSchemaName(String ref) {
		if (ref == null) return null;
		int idx = ref.lastIndexOf('/');
		return idx >= 0 ? ref.substring(idx + 1) : ref;
	}

	// ── Utility ──

	private String extractType(Schema<?> schema) {
		if (schema == null) return null;

		if (schema.get$ref() != null) {
			return "ref";
		}

		String type = schema.getType();
		if (type == null) return null;

		if ("array".equals(type)) {
			Schema<?> items = schema.getItems();
			String innerType = extractType(items);
			return innerType != null ? "array<" + innerType + ">" : "array";
		}

		return type;
	}
}
