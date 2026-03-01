package pingpong.backend.domain.swagger.util;

import static pingpong.backend.domain.swagger.util.SwaggerNormalizeUtil.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerEndpointSecurity;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.SwaggerParameter;
import pingpong.backend.domain.swagger.SwaggerRequest;
import pingpong.backend.domain.swagger.SwaggerResponse;
import pingpong.backend.domain.swagger.service.SwaggerParser;
import pingpong.backend.global.exception.CustomException;

@Component
public class SwaggerHashUtil {

	private static final ObjectMapper mapper = new ObjectMapper()
		.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true)
		.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

	/**
	 * swagger snapshot SHA-256 해시값 생성
	 * @param root
	 * @return
	 */

	public String generateSpecHash(JsonNode root) {
		try {
			JsonNode paths = root.get("paths");
			JsonNode components = root.get("components");

			ObjectNode minimal = mapper.createObjectNode();

			if (paths != null) {
				minimal.set("paths",removeNonStructuralFields(paths));
			}
			if (components != null) {
				minimal.set("components", removeNonStructuralFields(components));
			}

			String canonicalJson = mapper.writeValueAsString(minimal);
			return sha256(canonicalJson);

		} catch (Exception e) {
			throw new CustomException(SwaggerErrorCode.JSON_PROCESSING_EXCEPTION);
		}
	}

	public String computeStructureHash(
		Endpoint endpoint,
		List<SwaggerParameter> parameters,
		List<SwaggerRequest> requests,
		List<SwaggerResponse> responses,
		List<SwaggerEndpointSecurity> securities
	) {
		try {
			ObjectNode root = mapper.createObjectNode();

			root.put("path", endpoint.getPath());
			root.put("method", endpoint.getMethod().ordinal());

			// 순서 안정화
			parameters.sort(Comparator.comparing(SwaggerParameter::getName));
			responses.sort(Comparator.comparing(SwaggerResponse::getStatusCode));

			root.set("parameters", mapper.valueToTree(parameters));
			root.set("requests", mapper.valueToTree(requests));
			root.set("responses", mapper.valueToTree(responses));
			root.set("securities", mapper.valueToTree(securities));

			String canonicalJson = mapper.writeValueAsString(root);
			return sha256(canonicalJson);

		} catch (Exception e) {
			throw new CustomException(SwaggerErrorCode.JSON_PROCESSING_EXCEPTION);
		}
	}

	/**
	 * 필요없는 필드들 제외하고 hash값 생성하도록 함
	 * @param node
	 * @return
	 */
	private JsonNode removeNonStructuralFields(JsonNode node) {

		if (node.isObject()) {

			ObjectNode cleaned = ((ObjectNode) node).deepCopy();

			// 제거하고 싶은 필드들
			cleaned.remove(List.of(
				"description",
				"summary",
				"example",
				"examples",
				"title"
			));

			cleaned.fieldNames().forEachRemaining(field -> {
				JsonNode child = cleaned.get(field);
				cleaned.set(field, removeNonStructuralFields(child));
			});

			return cleaned;
		}

		if (node.isArray()) {
			ArrayNode arrayNode = (ArrayNode) node;
			for (int i = 0; i < arrayNode.size(); i++) {
				arrayNode.set(i, removeNonStructuralFields(arrayNode.get(i)));
			}
			return arrayNode;
		}

		return node;
	}

	/**
	 * SHA-256A 해시 함수 적용
	 * @param input
	 * @return
	 */
	private static String sha256(String input) {
		try{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

			StringBuilder hexString = new StringBuilder();
			for (byte b:hashBytes){
				hexString.append(String.format("%02x", b));
			}

			return hexString.toString();
		}catch(Exception e){
			throw new CustomException(SwaggerErrorCode.HASHING_EXCEPTION);
		}
	}

	/**
	 * requestBody에 대한 해시값 생성
	 * @param root
	 * @return
	 */
	public String generateRequestHash(JsonNode root) {
		JsonNode requestBody=root.get("requestBody");
		if(requestBody==null||requestBody.isNull()){
			return null;
		}

		JsonNode normalized=normalizeNode(requestBody);
		String canonical=writeCanonical(normalized);
		return sha256(canonical);
	}

	/**
	 * responseBody에 대한 해시값 생성
	 * @param root
	 * @return
	 */
	public String generateResponseHash(JsonNode root) {
		JsonNode requestBody=root.get("responses");
		if(requestBody==null||requestBody.isNull()){
			return null;
		}

		JsonNode normalized=normalizeNode(requestBody);
		String canonical=writeCanonical(normalized);
		return sha256(canonical);
	}


	/**
	 * hash 값 생성 전 정렬
	 * @param node
	 * @return
	 */
	private String writeCanonical(JsonNode node){
		try{
			return mapper.writeValueAsString(node);
		}catch(Exception e){
			throw new CustomException(SwaggerErrorCode.JSON_PROCESSING_EXCEPTION);
		}
	}

	/**
	 * schema에 대한 hash값 생성
	 * @param schemaNode
	 * @return
	 */
	public String generateSchemaHash(JsonNode schemaNode,JsonNode root){

		if(schemaNode==null||schemaNode.isNull()){
			return null;
		}

		JsonNode resolved=resolveSchema(schemaNode,root);
		JsonNode normalized=normalizeNode(resolved);
		String canonical=writeCanonical(normalized);
		return sha256(canonical);
	}

	/**
	 * canonical 생성
	 * @param schemaNode
	 * @param root
	 * @return
	 */
	public String generateCanonical(JsonNode schemaNode,JsonNode root){
		JsonNode resolved=resolveSchema(schemaNode,root);
		JsonNode normalized=normalizeNode(resolved);
		String canonical=writeCanonical(normalized);
		return canonical;
	}

	/**
	 * schema $ref 참조 해제
	 * @param schemaNode
	 * @param root
	 * @return
	 */
	public JsonNode resolveSchema(JsonNode schemaNode, JsonNode root) {
		if (schemaNode == null) {
			return schemaNode;
		}

		// $ref 존재 체크
		JsonNode refNode=schemaNode.get("$ref");
		if(refNode!=null && !refNode.isNull()){
			String ref=refNode.asText();

			//ex. "#/components/schemas/UserReponse"
			String[] parts=ref.split("/");

			JsonNode target=root;
			for(int i=1;i<parts.length;i++){
				target=target.get(parts[i]);
			}
			return resolveSchema(target,root);
		}

		if(schemaNode.isObject()){
			ObjectNode copy=mapper.createObjectNode();
			schemaNode.fieldNames().forEachRemaining(field->{
				copy.set(field,resolveSchema(schemaNode.get(field),root));
			});
			return copy;
		}

		if(schemaNode.isArray()){
			ArrayNode arr=mapper.createArrayNode();
			for(JsonNode element:schemaNode){
				arr.add(resolveSchema(element,root));
			}
			return arr;
		}
		return schemaNode;
	}
}
