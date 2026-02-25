package pingpong.backend.domain.swagger.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.SwaggerParameter;
import pingpong.backend.domain.swagger.SwaggerRequest;
import pingpong.backend.domain.swagger.SwaggerResponse;
import pingpong.backend.domain.swagger.dto.EndpointAggregate;
import pingpong.backend.domain.swagger.enums.CrudMethod;
import pingpong.backend.domain.swagger.repository.SwaggerRequestRepository;
import pingpong.backend.domain.swagger.repository.SwaggerResponseRepository;
import pingpong.backend.domain.swagger.util.SwaggerHashUtil;
import pingpong.backend.global.exception.CustomException;

@Component
@RequiredArgsConstructor
@Slf4j
public class SwaggerParser {

	private final RestClient restClient;
	private final SwaggerHashUtil swaggerHashUtil;
	private final ObjectMapper objectMapper;

	/**
	 * swagger JSON의 전체 필드 파싱
	 * @param root
	 * @return
	 */
	public List<EndpointAggregate> parseAll(JsonNode root){
		List<EndpointAggregate> result=new ArrayList<>();

		JsonNode pathsNode=root.get("paths");

		if(pathsNode==null){
			return result;
		}
		pathsNode.fields().forEachRemaining(pathEntry->{
			String path=pathEntry.getKey();
			JsonNode pathItem=pathEntry.getValue();

			JsonNode pathLevelParams=pathItem.get("parameters");
			pathItem.fields().forEachRemaining(operationEntry->{
				String method=operationEntry.getKey();
				if(!isHttpMethod(method)){
					return;
				}
				JsonNode operationNode=operationEntry.getValue();
				String tag=extractTag(operationNode);
				Endpoint endpoint=buildEndpoint(path,method,operationNode);
				endpoint.assignTag(tag);

				List<SwaggerParameter> parameters=extractParameters(pathLevelParams,operationNode,endpoint,root);
				List<SwaggerRequest> requests=extractRequests(operationNode,endpoint,root);
				List<SwaggerResponse> responses=extractResponses(operationNode,endpoint,root);

				result.add(new EndpointAggregate(endpoint,parameters,requests,responses, LocalDateTime.now()));
			});
		});
		return result;
	}

	private String extractTag(JsonNode operationNode){
		JsonNode tagsNode=operationNode.get("tags");
		if(tagsNode!=null && tagsNode.isArray() && tagsNode.size()>0){
			return tagsNode.get(0).asText();
		}
		return null;
	}


	/**
	 * swagger json 파싱
	 * @param uri
	 * @return
	 */
	public JsonNode fetchJson(String uri){
		return restClient.get()
			.uri(uri)
			.retrieve()
			.onStatus(HttpStatusCode::is5xxServerError,
				(req,res)->{
					throw new CustomException(SwaggerErrorCode.SWAGGER_CONNECTION_ERROR);
				})
			.body(JsonNode.class);
	}

	private boolean isHttpMethod(String method) {
		return method.equalsIgnoreCase("get")
			|| method.equalsIgnoreCase("post")
			|| method.equalsIgnoreCase("put")
			|| method.equalsIgnoreCase("delete")
			|| method.equalsIgnoreCase("patch")
			|| method.equalsIgnoreCase("options")
			|| method.equalsIgnoreCase("head");
	}


	/**
	 * Endpoint 생성
	 * @param path
	 * @param method
	 * @param operationNode
	 * @return
	 */

	private Endpoint buildEndpoint(String path, String method, JsonNode operationNode) {

		// ====== 1) description 노드 타입/길이 확인 ======
		JsonNode descNode = operationNode.get("description");
		String description = null;

		if (descNode == null || descNode.isNull()) {
			log.info("[Swagger][Endpoint] {} {} descNode=null", method, path);
		} else {
			log.info("[Swagger][Endpoint] {} {} descNodeType={}", method, path, descNode.getNodeType());

			if (descNode.isTextual()) {
				description = descNode.asText();
				log.info("[Swagger][Endpoint] {} {} description.length={}", method, path, description.length());
			} else {
				// textual이 아닌데 description이 들어온 경우: 그대로 저장하면 매우 길어질 수 있음
				String descJson = safeJson(descNode);
				log.warn("[Swagger][Endpoint] {} {} description is NOT text. json.length={}", method, path, descJson.length());
				// 필요하면 임시로 저장하지 않거나(null), 요약해서 저장하세요.
				// description = null;
				// 또는 description = descJson; // (주의: 길이 매우 큼)
			}
		}

		// ====== 2) summary / operationId 길이도 같이 확인 ======
		String summary = operationNode.path("summary").asText(null);
		String operationId = operationNode.path("operationId").asText(null);

		log.info("[Swagger][Endpoint] {} {} summary.length={}, operationId.length={}",
			method,
			path,
			summary == null ? 0 : summary.length(),
			operationId == null ? 0 : operationId.length()
		);

		// ====== 3) request/response hash도 확인(혹시 긴 값이면) ======
		String reqHash = swaggerHashUtil.generateRequestHash(operationNode);
		String resHash = swaggerHashUtil.generateResponseHash(operationNode);

		log.info("[Swagger][Endpoint] {} {} reqHash.len={}, resHash.len={}",
			method, path,
			reqHash == null ? 0 : reqHash.length(),
			resHash == null ? 0 : resHash.length()
		);

		return Endpoint.builder()
			.path(path)
			.method(CrudMethod.valueOf(method.toUpperCase()))
			.summary(summary)
			.description(description)  // 위에서 만든 description 사용
			.operationId(operationId)
			.requestSchemaHash(reqHash)
			.responseSchemaHash(resHash)
			.build();
	}

	private String safeJson(JsonNode node) {
		try {
			return objectMapper.writeValueAsString(node);
		} catch (JsonProcessingException e) {
			return node.toString();
		}
	}


	/**
	 * swagger Request JSON에서 추출해서 객체 생성
	 * @param operationNode
	 * @param endpoint
	 * @return
	 */
	public List<SwaggerRequest> extractRequests(JsonNode operationNode,
		Endpoint endpoint,JsonNode root){

		List<SwaggerRequest> result=new ArrayList<>();

		JsonNode requestBody=operationNode.get("requestBody");
		if(requestBody==null){
			return result;
		}

		boolean required=requestBody.get("required").asBoolean(false);

		JsonNode content=requestBody.get("content");
		if(content==null){
			return result;
		}

		content.fields().forEachRemaining(entry->{
			String mediaType=entry.getKey();
			JsonNode mediaNode=entry.getValue();

			JsonNode schemaNode=mediaNode.get("schema");

			String schemaHash=swaggerHashUtil.generateSchemaHash(schemaNode,root);
			String canonical= swaggerHashUtil.generateCanonical(schemaNode,root);


			SwaggerRequest request=SwaggerRequest.builder()
				.mediaType(mediaType)
				.required(required)
				.schemaHash(schemaHash)
				.schemaJson(canonical)
				.endpoint(endpoint)
				.build();
			result.add(request);
		});
		return result;
	}

	/**
	 * swagger Response JSON에서 추출해서 객체 생성
	 * @param operationNode
	 * @param endpoint
	 * @return
	 */
	public List<SwaggerResponse> extractResponses(JsonNode operationNode, Endpoint endpoint,JsonNode root) {

		List<SwaggerResponse> result = new ArrayList<>();

		JsonNode responsesNode = operationNode.get("responses");
		if (responsesNode == null || responsesNode.isNull()) {
			return result;
		}

		// 1. status code 정렬
		List<String> statusCodes = new ArrayList<>();
		responsesNode.fieldNames().forEachRemaining(statusCodes::add);
		Collections.sort(statusCodes);

		for (String statusCodeStr : statusCodes) {

			JsonNode responseNode = responsesNode.get(statusCodeStr);

			// 2. description
			String description = responseNode.path("description").asText(null);

			JsonNode contentNode = responseNode.path("content");

			// 3. content 존재 체크 (중요)
			if (contentNode.isMissingNode() || contentNode.isNull()) {
				continue;
			}

			// 4. media type 정렬
			List<String> mediaTypes = new ArrayList<>();
			contentNode.fieldNames().forEachRemaining(mediaTypes::add);
			Collections.sort(mediaTypes);

			for (String mediaType : mediaTypes) {

				JsonNode mediaNode = contentNode.get(mediaType);

				// 5. schema 안전 처리
				JsonNode schemaNode = mediaNode.get("schema");

				String schemaHash = swaggerHashUtil.generateSchemaHash(schemaNode,root);
				String canonical= swaggerHashUtil.generateCanonical(schemaNode,root);

				SwaggerResponse response = SwaggerResponse.builder()
					//statusCode는 String 강력 추천
					.statusCode(statusCodeStr)
					.mediaType(mediaType)
					.description(description)
					.schemaHash(schemaHash)
					.schemaJson(canonical)
					.endpoint(endpoint)
					.build();

				result.add(response);
			}
		}

		return result;
	}

	/**
	 * parameter 추출
	 * @param pathLevelParams
	 * @param operationNode
	 * @param endpoint
	 * @return
	 */
	public List<SwaggerParameter> extractParameters(
		JsonNode pathLevelParams,
		JsonNode operationNode,
		Endpoint endpoint,
		JsonNode root) {

		List<SwaggerParameter> result = new ArrayList<>();

		// Path-level parameters
		if (pathLevelParams != null && pathLevelParams.isArray()) {
			parseParameterArray(pathLevelParams, endpoint, result,root);
		}

		// Operation-level parameters
		JsonNode operationParams = operationNode.get("parameters");
		if (operationParams != null && operationParams.isArray()) {
			parseParameterArray(operationParams, endpoint, result,root);
		}

		return result;
	}

	/**
	 * swagger parameter JSON에서 추출해서 객체 생성
	 * @param paramArray
	 * @param endpoint
	 * @param result
	 */
	private void parseParameterArray(JsonNode paramArray,
		Endpoint endpoint,
		List<SwaggerParameter> result,
		JsonNode root) {

		for (JsonNode paramNode : paramArray) {

			String name = paramNode.path("name").asText(null);
			String inType = paramNode.path("in").asText(null);
			Boolean required = paramNode.path("required").asBoolean(false);

			JsonNode schemaNode = paramNode.get("schema");

			String schemaHash =
				swaggerHashUtil.generateSchemaHash(schemaNode,root);

			SwaggerParameter parameter = SwaggerParameter.builder()
				.name(name)
				.inType(inType)
				.required(required)
				.schemaHash(schemaHash)
				.endpoint(endpoint)

				.build();

			result.add(parameter);
		}
	}






}
