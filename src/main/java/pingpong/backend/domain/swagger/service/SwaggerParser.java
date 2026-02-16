package pingpong.backend.domain.swagger.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
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
public class SwaggerParser {

	private final RestClient restClient;
	private final SwaggerHashUtil swaggerHashUtil;

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
				Endpoint endpoint=buildEndpoint(path,method,operationNode);

				List<SwaggerParameter> parameters=extractParameters(pathLevelParams,operationNode,endpoint);
				List<SwaggerRequest> requests=extractRequests(operationNode,endpoint);
				List<SwaggerResponse> responses=extractResponses(operationNode,endpoint);

				result.add(new EndpointAggregate(endpoint,parameters,requests,responses));
			});
		});
		return result;
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
	private Endpoint buildEndpoint(String path,
		String method,
		JsonNode operationNode){
		Endpoint endpoint=Endpoint.builder()
			.path(path)
			.method(CrudMethod.valueOf(method.toUpperCase()))
			.summary(operationNode.path("summary").asText(null))
			.description(operationNode.path("description").asText(null))
			.operationId(operationNode.path("operationId").asText(null))
			.requestSchemaHash(swaggerHashUtil.generateRequestHash(operationNode))
			.responseSchemaHash(swaggerHashUtil.generateResponseHash(operationNode))
			.build();
		return endpoint;
	}

	/**
	 * swagger Request JSON에서 추출해서 객체 생성
	 * @param root
	 * @param endpoint
	 * @return
	 */
	public List<SwaggerRequest> extractRequests(JsonNode root,
		Endpoint endpoint){

		List<SwaggerRequest> result=new ArrayList<>();

		JsonNode requestBody=root.get("requestBody");
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

			String schemaHash=swaggerHashUtil.generateSchemaHash(schemaNode);

			SwaggerRequest request=SwaggerRequest.builder()
				.mediaType(mediaType)
				.required(required)
				.schemaHash(schemaHash)
				.endpoint(endpoint)
				.build();
			result.add(request);
		});
		return result;
	}

	/**
	 * swagger Response JSON에서 추출해서 객체 생성
	 * @param root
	 * @param endpoint
	 * @return
	 */
	public List<SwaggerResponse> extractResponses(JsonNode root,
		Endpoint endpoint){

		List<SwaggerResponse> result=new ArrayList<>();

		JsonNode responsesNode=root.get("responses");
		if(responsesNode==null){
			return result;
		}

		responsesNode.fields().forEachRemaining(statusEntry->{
			String statusCodeStr=statusEntry.getKey();
			JsonNode responseNode=statusEntry.getValue();

			Long statusCode;
			try{
				statusCode=Long.parseLong(statusCodeStr);
			}catch(NumberFormatException e){
				return;
			}

			String description=responseNode.path("description").asText(null);
			JsonNode contentNode=responseNode.path("content");
			if(contentNode==null){
				return;
			}

			contentNode.fields().forEachRemaining(contentEntry->{
				String mediaType=contentEntry.getKey();
				JsonNode mediaNode=contentEntry.getValue();

				JsonNode schemaNode=mediaNode.get("schema");
				String schemaHash=swaggerHashUtil.generateSchemaHash(schemaNode);
				SwaggerResponse response=SwaggerResponse.builder()
					.mediaType(mediaType)
					.statusCode(statusCode)
					.description(description)
					.schemaHash(schemaHash)
					.endpoint(endpoint)
					.build();
				result.add(response);
			});
		});
		return result;
	}

	public List<SwaggerParameter> extractParameters(
		JsonNode pathLevelParams,
		JsonNode operationNode,
		Endpoint endpoint) {

		List<SwaggerParameter> result = new ArrayList<>();

		// Path-level parameters
		if (pathLevelParams != null && pathLevelParams.isArray()) {
			parseParameterArray(pathLevelParams, endpoint, result);
		}

		// Operation-level parameters
		JsonNode operationParams = operationNode.get("parameters");
		if (operationParams != null && operationParams.isArray()) {
			parseParameterArray(operationParams, endpoint, result);
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
		List<SwaggerParameter> result) {

		for (JsonNode paramNode : paramArray) {

			String name = paramNode.path("name").asText(null);
			String inType = paramNode.path("in").asText(null);
			Boolean required = paramNode.path("required").asBoolean(false);

			JsonNode schemaNode = paramNode.get("schema");

			String schemaHash =
				swaggerHashUtil.generateSchemaHash(schemaNode);

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
